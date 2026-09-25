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
import java.time.Clock
import java.time.Duration
import kotlin.uuid.Uuid

sealed interface LoginResult {
    /** [sessionToken] goes in the cookie; [needsName] sends a new player to the welcome step first. */
    data class LoggedIn(val sessionToken: String, val returnTo: String, val needsName: Boolean) : LoginResult
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
        val recent = db.transaction { attempts.countSince(email, clock.instant() - Duration.ofHours(1)) }
        if (recent >= MAILS_PER_EMAIL_PER_HOUR || !perAddress.tryAcquire(address)) return Requested.TooMany
        val id = ids.next()
        val token = secrets.token()
        val code = secrets.code()
        val attempt = LoginAttempt.startingAt(clock.instant())
        db.transaction { attempts.insert(id, email, secrets.hash(token), secrets.codeHash(id, code), returnTo, attempt, clock.instant()) }
        mailer.send(
            Mail(
                to = email,
                subject = "Your Gorefest code: $code",
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
        return Requested.Sent(id)
    }

    /** Email and return path of an attempt, for the "Check your mail" page. Doesn't use it up. */
    suspend fun pending(attemptId: Uuid): Pending? =
        db.transaction { attempts.lockById(attemptId)?.let { Pending(it.email, it.returnTo) } }

    data class Pending(val email: String, val returnTo: String)

    suspend fun withCode(attemptId: Uuid, code: String): LoginResult = complete {
        attempts.lockById(attemptId)?.let { stored ->
            val matches = secrets.matches(stored.codeHash, secrets.codeHash(stored.id, code.filter(Char::isDigit)))
            stored to stored.attempt.checkCode(clock.instant(), matches)
        }
    }

    /** Checks a link token without using it up, for the confirm page. */
    suspend fun emailForLink(token: String): String? = db.transaction {
        attempts.lockByToken(secrets.hash(token))?.takeIf { it.attempt.checkLink(clock.instant()).check == LoginCheck.Ok }?.email
    }

    suspend fun withLink(token: String): LoginResult = complete {
        attempts.lockByToken(secrets.hash(token))?.let { stored -> stored to stored.attempt.checkLink(clock.instant()) }
    }

    suspend fun currentPlayer(sessionToken: String): Player? = db.transaction {
        sessions.player(secrets.hash(sessionToken), clock.instant())?.let { players.find(it) }
    }

    suspend fun logout(sessionToken: String) = db.transaction { sessions.delete(secrets.hash(sessionToken)) }

    suspend fun rename(player: PlayerId, name: String) = db.transaction { players.rename(player, name) }

    suspend fun names(ids: Collection<PlayerId>): Map<PlayerId, String> = db.transaction { players.names(ids) }

    private suspend fun complete(verify: context(Tx) () -> Pair<StoredLoginAttempt, Verification>?): LoginResult {
        // The player to log in (if any), and what to tell the browser.
        val (player, result) = db.transaction {
            val (stored, verification) = verify() ?: return@transaction null to LoginResult.UnknownAttempt
            attempts.save(stored.id, verification.attempt)
            if (verification.check != LoginCheck.Ok) {
                return@transaction null to LoginResult.Failed(verification.check, stored.email, stored.returnTo)
            }
            val player = players.findOrCreate(stored.email, PlayerId(ids.next()))
            val token = secrets.token()
            sessions.create(secrets.hash(token), player.id, clock.instant() + SESSION_TTL)
            player.id to LoginResult.LoggedIn(token, stored.returnTo, needsName = player.name == null)
        }
        // After the commit: if this fails, the player's next login registers them instead.
        if (player != null) commands.handle(LogInDecider, LogIn(player))
        return result
    }

    companion object {
        val SESSION_TTL: Duration = Duration.ofDays(365)
        const val MAILS_PER_EMAIL_PER_HOUR = 5
        const val MAILS_PER_ADDRESS_PER_HOUR = 20
    }
}
