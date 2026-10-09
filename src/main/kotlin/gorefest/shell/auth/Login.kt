package gorefest.shell.auth

import gorefest.core.PlayerId
import gorefest.core.auth.LoginAttempt
import gorefest.core.auth.LoginCheck
import gorefest.core.auth.Verification
import gorefest.core.auth.checkCode
import gorefest.core.auth.checkLink
import gorefest.core.player.LogIn
import gorefest.core.player.LogInDecider
import gorefest.shell.AppConfig
import gorefest.shell.IdGenerator
import gorefest.shell.db.Database
import gorefest.shell.db.Tx
import gorefest.shell.eventstore.CommandHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import kotlin.uuid.Uuid

sealed interface LoginResult {
    /** [sessionToken] goes in the cookie; [needsName] sends a new player to the welcome step first. */
    data class LoggedIn(val player: PlayerId, val sessionToken: String, val returnTo: String, val needsName: Boolean) : LoginResult
    data class Failed(val check: LoginCheck, val email: String, val returnTo: String) : LoginResult
    data object UnknownAttempt : LoginResult
}

/** Magic link + code login, and session lookup. */
class Login(
    private val db: Database,
    private val commands: CommandHandler,
    private val mailer: Mailer,
    private val config: AppConfig,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val secrets = Secrets(config.secretKey)
    val players = Players()
    private val attempts = LoginAttempts()
    private val sessions = Sessions()

    sealed interface Requested {
        /** The mail went out; the code form posts [attempt] back. */
        data class Sent(val attempt: Uuid) : Requested

        /** Too many codes for this email or from this address lately. */
        data object TooMany : Requested
    }

    private val perAddress = RateLimiter(limit = MAILS_PER_ADDRESS_PER_HOUR, window = Duration.ofHours(1), clock)

    /** Sends the mail, unless [email] or [address] asked for too many codes in the last hour. */
    suspend fun request(email: String, returnTo: String, address: String = "unknown"): Requested {
        val id = ids.next()
        val token = secrets.token()
        val code = secrets.code()
        val attempt = LoginAttempt.startingAt(clock.instant())
        // Count and insert under one per-email lock, so parallel requests can't all pass the check.
        val allowed = db.transaction {
            attempts.lockEmail(email)
            if (attempts.countSince(email, clock.instant() - Duration.ofHours(1)) >= MAILS_PER_EMAIL_PER_HOUR) return@transaction false
            if (!perAddress.tryAcquire(address)) return@transaction false
            attempts.insert(id, email, secrets.hash(token), secrets.codeHash(id, code), returnTo, attempt, clock.instant())
            true
        }
        if (!allowed) return Requested.TooMany
        try {
            sendMail(email, code, token)
        } catch (e: Throwable) {
            // A mail that never went out must not count against the cap.
            withContext(NonCancellable) { db.transaction { attempts.delete(id) } }
            throw e
        }
        return Requested.Sent(id)
    }

    private suspend fun sendMail(email: String, code: String, token: String) {
        mailer.send(
            Mail(
                to = email,
                subject = "Your Boardfest code: $code",
                text = """
                    |Your code is $code.
                    |
                    |Or tap this link to log in:
                    |${config.baseUrl}/login/link?token=$token
                    |
                    |The code and the link work once, for the next 15 minutes.
                    |Didn't ask for this? Then you can ignore this mail.
                    """.trimMargin(),
                sentAt = clock.instant(),
            ),
        )
    }

    /** Email and return path of an attempt, for the "Check your mail" page. Doesn't use it up. */
    suspend fun pending(attemptId: Uuid): Pending? =
        db.transaction { attempts.findById(attemptId)?.let { Pending(it.email, it.returnTo) } }

    data class Pending(val email: String, val returnTo: String)

    suspend fun withCode(attemptId: Uuid, code: String): LoginResult = complete {
        attempts.lockById(attemptId)?.let { stored ->
            val matches = secrets.matches(stored.codeHash, secrets.codeHash(stored.id, code.filter(Char::isDigit)))
            stored to stored.attempt.checkCode(clock.instant(), matches)
        }
    }

    /** Checks a link token without using it up, for the confirm page. */
    suspend fun emailForLink(token: String): String? = db.transaction {
        attempts.findByToken(secrets.hash(token))?.takeIf { it.attempt.checkLink(clock.instant()).check == LoginCheck.Ok }?.email
    }

    suspend fun withLink(token: String): LoginResult = complete {
        attempts.lockByToken(secrets.hash(token))?.let { stored -> stored to stored.attempt.checkLink(clock.instant()) }
    }

    suspend fun currentPlayer(sessionToken: String): Player? = db.transaction {
        sessions.player(secrets.hash(sessionToken), clock.instant())?.let { players.find(it) }
    }

    suspend fun logout(sessionToken: String) = db.transaction { sessions.delete(secrets.hash(sessionToken)) }

    /** Names the player. A player the admin hasn't approved yet gets the admin a mail the first time they name themselves. */
    suspend fun rename(player: PlayerId, name: String) {
        val (before, after) = db.transaction {
            val before = players.find(player)
            players.rename(player, name)
            before to players.find(player)
        }
        if (before?.name == null && after != null && !after.approved) notifyAdmin(after)
    }

    private suspend fun notifyAdmin(newcomer: Player) {
        val admin = config.adminEmail ?: return
        mailer.send(
            Mail(
                to = admin,
                subject = "${newcomer.name} wants into Boardfest",
                text = "${newcomer.name} (${newcomer.email}) signed up and is waiting for your approval:\n${config.baseUrl}/admin/approvals\n",
                sentAt = clock.instant(),
            ),
        )
    }

    /** Accounts waiting for the admin, oldest first. */
    suspend fun waiting(): List<Player> = db.transaction { players.waiting() }

    /** Lets the player in and tells them. False if they weren't waiting. */
    suspend fun approve(id: PlayerId): Boolean {
        val (done, player) = db.transaction { players.approve(id) to players.find(id) }
        if (done && player != null) {
            mailer.send(Mail(player.email, "You're in on Boardfest", "Welcome, ${player.name}. Your account is approved:\n${config.baseUrl}/\n", clock.instant()))
        }
        return done
    }

    /** Turns the account down: it's removed, and they're told. False if they weren't waiting. */
    suspend fun reject(id: PlayerId): Boolean {
        val (done, player) = db.transaction { players.find(id).let { found -> players.deleteUnapproved(id) to found } }
        if (done && player != null) {
            mailer.send(Mail(player.email, "About your Boardfest account", "Sorry, your Boardfest sign-up wasn't approved. This is a private group, so it's by invitation only.\n", clock.instant()))
        }
        return done
    }

    suspend fun names(ids: Collection<PlayerId>): Map<PlayerId, String> = db.transaction { players.names(ids) }

    /** Drops expired sessions and login attempts older than a day. */
    suspend fun purge(): Int = db.transaction {
        sessions.purgeExpired(clock.instant()) + attempts.purgeBefore(clock.instant() - ATTEMPT_RETENTION)
    }

    /** Runs [purge] every [every] until [scope] is cancelled. */
    fun startPurging(scope: CoroutineScope, every: Duration = Duration.ofHours(1)) = scope.launch {
        while (true) {
            purge()
            delay(every.toMillis())
        }
    }

    private suspend fun complete(verify: context(Tx) () -> Pair<StoredLoginAttempt, Verification>?): LoginResult {
        // The player to log in (if any), and what to tell the browser.
        val (player, result) = db.transaction {
            val (stored, verification) = verify() ?: return@transaction null to LoginResult.UnknownAttempt
            attempts.save(stored.id, verification.attempt)
            if (verification.check != LoginCheck.Ok) {
                return@transaction null to LoginResult.Failed(verification.check, stored.email, stored.returnTo)
            }
            val player = players.findOrCreate(stored.email, PlayerId(ids.next()), approved = !config.approvalRequired || config.isAdmin(stored.email))
            // The admin may have signed up before being configured as one.
            if (config.isAdmin(stored.email) && !player.approved) players.approve(player.id)
            val token = secrets.token()
            sessions.create(secrets.hash(token), player.id, clock.instant() + SESSION_TTL)
            player.id to LoginResult.LoggedIn(player.id, token, stored.returnTo, needsName = player.name == null)
        }
        // After the commit: if this fails, the player's next login registers them instead.
        if (player != null) commands.handle(LogInDecider, LogIn(player))
        return result
    }

    companion object {
        val SESSION_TTL: Duration = Duration.ofDays(365)
        val ATTEMPT_RETENTION: Duration = Duration.ofDays(1)
        const val MAILS_PER_EMAIL_PER_HOUR = 5
        const val MAILS_PER_ADDRESS_PER_HOUR = 20
    }
}
