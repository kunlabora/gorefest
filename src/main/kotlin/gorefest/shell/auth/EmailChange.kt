package gorefest.shell.auth

import gorefest.core.PlayerId
import gorefest.core.auth.LoginAttempt
import gorefest.core.auth.LoginCheck
import gorefest.core.auth.checkCode
import gorefest.shell.AppConfig
import gorefest.shell.db.Database
import gorefest.shell.db.Tx
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.uuid.toJavaUuid

private val EMAIL = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
private const val MAX_EMAIL_LENGTH = 254

/**
 * Changing the address on an account: a code goes to the new address, and only entering it replaces the old one
 * (sessions stay), so nobody can point an account at an address they don't read. The old address gets a notice.
 * Nothing here tells whether an address belongs to somebody else: asking for one that does looks the same as any other.
 */
class EmailChange(
    private val db: Database,
    private val players: Players,
    private val mailer: Mailer,
    private val config: AppConfig,
    private val clock: Clock,
) {
    private val secrets = Secrets(config.secretKey)
    private val rows = Rows()
    private val perPlayer = RateLimiter(limit = CODES_PER_PLAYER_PER_HOUR, window = Duration.ofHours(1), clock)

    sealed interface Requested {
        data class Sent(val to: String) : Requested
        data object Invalid : Requested
        data object SameAsNow : Requested
        data object TooMany : Requested

        /** The admin is known by this address (`ADMIN_EMAIL`): changing it here would lock them out. */
        data object IsAdminAddress : Requested
    }

    sealed interface Confirmed {
        data object Changed : Confirmed
        data class WrongCode(val triesLeft: Int) : Confirmed

        /** Expired, used up, locked, or the address got taken meanwhile: the person starts over. */
        data object Failed : Confirmed
    }

    suspend fun request(player: Player, typed: String): Requested {
        val email = typed.trim().lowercase()
        if (!EMAIL.matches(email) || email.length > MAX_EMAIL_LENGTH) return Requested.Invalid
        if (email == player.email) return Requested.SameAsNow
        if (config.isAdmin(player.email)) return Requested.IsAdminAddress
        if (!perPlayer.tryAcquire(player.id.toString())) return Requested.TooMany
        val code = secrets.code()
        val taken = db.transaction {
            rows.put(player.id, email, secrets.codeHash(player.id.value, code), LoginAttempt.startingAt(clock.instant()), clock.instant())
            players.findByEmail(email) != null
        }
        // An address somebody else has gets no mail but the same answer: the screen can't be used to find out who is here.
        if (!taken) {
            mailer.send(
                Mail(
                    email, "Your Boardfest code: $code",
                    "Your code is $code.\n\nEnter it to make this your Boardfest address. It works once, for the next 15 minutes.\nDidn't ask for this? Then you can ignore this mail.\n",
                    clock.instant(),
                ),
            )
        }
        return Requested.Sent(email)
    }

    /** The address a code was sent to and not yet entered, for the code form. */
    suspend fun pending(id: PlayerId): String? = db.transaction { rows.find(id) }?.takeIf { it.attempt.usedAt == null && clock.instant().isBefore(it.attempt.expiresAt) }?.email

    suspend fun confirm(id: PlayerId, code: String): Confirmed {
        val (result, oldEmail, newEmail) = db.transaction {
            val stored = rows.lock(id) ?: return@transaction Triple(Confirmed.Failed, null, null)
            val matches = secrets.matches(stored.codeHash, secrets.codeHash(id.value, code.filter(Char::isDigit)))
            val verification = stored.attempt.checkCode(clock.instant(), matches)
            rows.save(id, verification.attempt)
            when (val check = verification.check) {
                LoginCheck.Ok -> {
                    val old = players.find(id)?.email
                    if (old != null && players.changeEmail(id, stored.email)) Triple(Confirmed.Changed, old, stored.email) else Triple(Confirmed.Failed, null, null)
                }
                is LoginCheck.WrongCode -> Triple(Confirmed.WrongCode(check.triesLeft), null, null)
                else -> Triple(Confirmed.Failed, null, null)
            }
        }
        if (result == Confirmed.Changed && oldEmail != null) {
            mailer.send(
                Mail(
                    oldEmail, "Your Boardfest email was changed",
                    "The email address on your Boardfest account was changed to $newEmail.\nNot you? Tell the person who runs Boardfest right away.\n",
                    clock.instant(),
                ),
            )
        }
        return result
    }

    private data class StoredChange(val email: String, val codeHash: ByteArray, val attempt: LoginAttempt)

    private class Rows {
        context(tx: Tx)
        fun put(id: PlayerId, email: String, codeHash: ByteArray, attempt: LoginAttempt, now: Instant) {
            tx.connection.prepareStatement(
                """
                insert into email_changes (player_id, new_email, code_hash, expires_at, created_at) values (?, ?, ?, ?, ?)
                on conflict (player_id) do update set new_email = excluded.new_email, code_hash = excluded.code_hash,
                  expires_at = excluded.expires_at, used_at = null, wrong_codes = 0, created_at = excluded.created_at
                """.trimIndent(),
            ).use {
                it.setObject(1, id.value.toJavaUuid())
                it.setString(2, email)
                it.setBytes(3, codeHash)
                it.setTimestamp(4, Timestamp.from(attempt.expiresAt))
                it.setTimestamp(5, Timestamp.from(now))
                it.executeUpdate()
            }
        }

        context(tx: Tx)
        fun find(id: PlayerId): PendingChange? = select(id, lock = false)?.let { PendingChange(it.email, it.attempt) }

        context(tx: Tx)
        fun lock(id: PlayerId): StoredChange? = select(id, lock = true)

        context(tx: Tx)
        private fun select(id: PlayerId, lock: Boolean): StoredChange? =
            tx.connection.prepareStatement(
                "select new_email, code_hash, expires_at, used_at, wrong_codes from email_changes where player_id = ?" + if (lock) " for update" else "",
            ).use {
                it.setObject(1, id.value.toJavaUuid())
                it.executeQuery().use { rs ->
                    if (!rs.next()) null
                    else StoredChange(rs.getString(1), rs.getBytes(2), LoginAttempt(rs.getTimestamp(3).toInstant(), rs.getTimestamp(4)?.toInstant(), rs.getInt(5)))
                }
            }

        context(tx: Tx)
        fun save(id: PlayerId, attempt: LoginAttempt) {
            tx.connection.prepareStatement("update email_changes set used_at = ?, wrong_codes = ? where player_id = ?").use {
                it.setTimestamp(1, attempt.usedAt?.let(Timestamp::from))
                it.setInt(2, attempt.wrongCodes)
                it.setObject(3, id.value.toJavaUuid())
                it.executeUpdate()
            }
        }
    }

    private data class PendingChange(val email: String, val attempt: LoginAttempt)

    private companion object {
        const val CODES_PER_PLAYER_PER_HOUR = 5
    }
}
