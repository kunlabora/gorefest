package gorefest.shell.auth

import gorefest.core.PlayerId
import gorefest.core.auth.LoginAttempt
import gorefest.shell.db.Tx
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/** [approved]: the admin let this account in (see [AppConfig.approvalRequired]). */
data class Player(val id: PlayerId, val email: String, val name: String?, val approved: Boolean = true)

class Players {
    /** The player with this email, created with [newId] if there's none yet. */
    context(tx: Tx)
    fun findOrCreate(email: String, newId: PlayerId, approved: Boolean = true): Player {
        tx.connection.prepareStatement(
            "insert into players (id, email, approved_at) values (?, ?, case when ? then now() end) on conflict (email) do nothing",
        ).use {
            it.setObject(1, newId.value.toJavaUuid())
            it.setString(2, email)
            it.setBoolean(3, approved)
            it.executeUpdate()
        }
        return tx.connection.prepareStatement("select id, email, name, approved_at is not null as approved from players where email = ?").use {
            it.setString(1, email)
            it.executeQuery().use { rs -> rs.next(); rs.toPlayer() }
        }
    }

    context(tx: Tx)
    fun find(id: PlayerId): Player? =
        tx.connection.prepareStatement("select id, email, name, approved_at is not null as approved from players where id = ?").use {
            it.setObject(1, id.value.toJavaUuid())
            it.executeQuery().use { rs -> if (rs.next()) rs.toPlayer() else null }
        }

    context(tx: Tx)
    fun findByEmail(email: String): Player? =
        tx.connection.prepareStatement("select id, email, name, approved_at is not null as approved from players where email = ?").use {
            it.setString(1, email)
            it.executeQuery().use { rs -> if (rs.next()) rs.toPlayer() else null }
        }

    /** Replaces the address unless another account has it. True if it changed. */
    context(tx: Tx)
    fun changeEmail(id: PlayerId, email: String): Boolean =
        tx.connection.prepareStatement("update players set email = ? where id = ? and not exists (select 1 from players where email = ? and id <> ?)").use {
            it.setString(1, email)
            it.setObject(2, id.value.toJavaUuid())
            it.setString(3, email)
            it.setObject(4, id.value.toJavaUuid())
            it.executeUpdate() > 0
        }

    /** Sets the last sign-in, and forgets any inactivity warning: they're back. */
    context(tx: Tx)
    fun touchLogin(id: PlayerId, at: Instant) {
        tx.connection.prepareStatement("update players set last_login_at = ?, inactive_warned_at = null where id = ?").use {
            it.setTimestamp(1, Timestamp.from(at))
            it.setObject(2, id.value.toJavaUuid())
            it.executeUpdate()
        }
    }

    /** Accounts that haven't signed in since [cutoff] and haven't been warned yet. */
    context(tx: Tx)
    fun toWarn(cutoff: Instant): List<Player> =
        tx.connection.prepareStatement(
            "select id, email, name, approved_at is not null as approved from players where last_login_at < ? and inactive_warned_at is null order by last_login_at",
        ).use {
            it.setTimestamp(1, Timestamp.from(cutoff))
            it.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toPlayer()) } }
        }

    context(tx: Tx)
    fun markWarned(id: PlayerId, at: Instant) {
        tx.connection.prepareStatement("update players set inactive_warned_at = ? where id = ?").use {
            it.setTimestamp(1, Timestamp.from(at))
            it.setObject(2, id.value.toJavaUuid())
            it.executeUpdate()
        }
    }

    /** Accounts warned before [cutoff] that haven't signed in since the warning. */
    context(tx: Tx)
    fun toErase(cutoff: Instant): List<Player> =
        tx.connection.prepareStatement(
            "select id, email, name, approved_at is not null as approved from players where inactive_warned_at < ? and last_login_at < inactive_warned_at order by inactive_warned_at",
        ).use {
            it.setTimestamp(1, Timestamp.from(cutoff))
            it.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toPlayer()) } }
        }

    /** When the account was made, for the file of someone's data. */
    context(tx: Tx)
    fun createdAt(id: PlayerId): Instant? =
        tx.connection.prepareStatement("select created_at from players where id = ?").use {
            it.setObject(1, id.value.toJavaUuid())
            it.executeQuery().use { rs -> if (rs.next()) rs.getTimestamp(1).toInstant() else null }
        }

    /** Named accounts still waiting for approval, oldest first. */
    context(tx: Tx)
    fun waiting(): List<Player> =
        tx.connection.prepareStatement(
            "select id, email, name, approved_at is not null as approved from players where approved_at is null and name is not null order by created_at",
        ).use { it.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toPlayer()) } } }

    /** True if [id] was waiting and is now in. */
    context(tx: Tx)
    fun approve(id: PlayerId): Boolean =
        tx.connection.prepareStatement("update players set approved_at = now() where id = ? and approved_at is null").use {
            it.setObject(1, id.value.toJavaUuid())
            it.executeUpdate() > 0
        }

    /** Removes an account that was never approved (sessions and collection rows go with it). True if there was one. */
    context(tx: Tx)
    fun deleteUnapproved(id: PlayerId): Boolean =
        tx.connection.prepareStatement("delete from players where id = ? and approved_at is null").use {
            it.setObject(1, id.value.toJavaUuid())
            it.executeUpdate() > 0
        }

    /** Removes the account whatever its state (sessions, collection and sync rows go with it). True if there was one. */
    context(tx: Tx)
    fun delete(id: PlayerId): Boolean =
        tx.connection.prepareStatement("delete from players where id = ?").use {
            it.setObject(1, id.value.toJavaUuid())
            it.executeUpdate() > 0
        }

    context(tx: Tx)
    fun rename(id: PlayerId, name: String) {
        tx.connection.prepareStatement("update players set name = ? where id = ?").use {
            it.setString(1, name)
            it.setObject(2, id.value.toJavaUuid())
            it.executeUpdate()
        }
    }

    context(tx: Tx)
    fun names(ids: Collection<PlayerId>): Map<PlayerId, String> {
        if (ids.isEmpty()) return emptyMap()
        return tx.connection.prepareStatement("select id, name from players where id = any(?) and name is not null").use {
            it.setArray(1, tx.connection.createArrayOf("uuid", ids.map { id -> id.value.toJavaUuid() }.toTypedArray()))
            it.executeQuery().use { rs ->
                buildMap { while (rs.next()) put(rs.playerId("id"), rs.getString("name")) }
            }
        }
    }

    context(tx: Tx)
    fun emails(ids: Collection<PlayerId>): Map<PlayerId, String> {
        if (ids.isEmpty()) return emptyMap()
        return tx.connection.prepareStatement("select id, email from players where id = any(?)").use {
            it.setArray(1, tx.connection.createArrayOf("uuid", ids.map { id -> id.value.toJavaUuid() }.toTypedArray()))
            it.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.playerId("id"), rs.getString("email")) } }
        }
    }

    private fun ResultSet.toPlayer() = Player(playerId("id"), getString("email"), getString("name"), getBoolean("approved"))
}

data class StoredLoginAttempt(
    val id: Uuid,
    val email: String,
    val codeHash: ByteArray,
    val returnTo: String,
    val attempt: LoginAttempt,
)

class LoginAttempts {
    context(tx: Tx)
    fun insert(id: Uuid, email: String, tokenHash: ByteArray, codeHash: ByteArray, returnTo: String, attempt: LoginAttempt, now: Instant) {
        tx.connection.prepareStatement(
            "insert into login_attempts (id, email, token_hash, code_hash, return_to, expires_at, created_at) values (?, ?, ?, ?, ?, ?, ?)",
        ).use {
            it.setObject(1, id.toJavaUuid())
            it.setString(2, email)
            it.setBytes(3, tokenHash)
            it.setBytes(4, codeHash)
            it.setString(5, returnTo)
            it.setTimestamp(6, Timestamp.from(attempt.expiresAt))
            it.setTimestamp(7, Timestamp.from(now))
            it.executeUpdate()
        }
    }

    /** How many codes were sent to [email] since [since], for rate limiting. */
    context(tx: Tx)
    fun countSince(email: String, since: Instant): Int =
        tx.connection.prepareStatement("select count(*) from login_attempts where email = ? and created_at > ?").use {
            it.setString(1, email)
            it.setTimestamp(2, Timestamp.from(since))
            it.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }

    /** Serialises concurrent requests for the same [email] until the transaction ends. */
    context(tx: Tx)
    fun lockEmail(email: String) {
        tx.connection.prepareStatement("select pg_advisory_xact_lock(hashtext(?))").use {
            it.setString(1, email)
            it.execute()
        }
    }

    context(tx: Tx)
    fun delete(id: Uuid) {
        tx.connection.prepareStatement("delete from login_attempts where id = ?").use {
            it.setObject(1, id.toJavaUuid())
            it.executeUpdate()
        }
    }

    /** Login attempts hold the email in clear: an erased account leaves none behind. */
    context(tx: Tx)
    fun deleteForEmail(email: String): Int =
        tx.connection.prepareStatement("delete from login_attempts where email = ?").use {
            it.setString(1, email)
            it.executeUpdate()
        }

    /** Drops attempts created before [before]: they no longer count for rate limiting, and they hold emails. */
    context(tx: Tx)
    fun purgeBefore(before: Instant): Int =
        tx.connection.prepareStatement("delete from login_attempts where created_at < ?").use {
            it.setTimestamp(1, Timestamp.from(before))
            it.executeUpdate()
        }

    /** Pending email changes hold the typed address in clear; once expired for a day they are of no use. */
    context(tx: Tx)
    fun purgeEmailChangesBefore(before: Instant): Int =
        tx.connection.prepareStatement("delete from email_changes where expires_at < ?").use {
            it.setTimestamp(1, Timestamp.from(before))
            it.executeUpdate()
        }

    /** Plain read, for checks that don't use the attempt up. */
    context(tx: Tx)
    fun findById(id: Uuid): StoredLoginAttempt? = select("id = ?", lock = false) { it.setObject(1, id.toJavaUuid()) }

    context(tx: Tx)
    fun findByToken(tokenHash: ByteArray): StoredLoginAttempt? = select("token_hash = ?", lock = false) { it.setBytes(1, tokenHash) }

    /** Locks the row, so two concurrent checks of the same attempt can't both succeed. */
    context(tx: Tx)
    fun lockById(id: Uuid): StoredLoginAttempt? = select("id = ?", lock = true) { it.setObject(1, id.toJavaUuid()) }

    context(tx: Tx)
    fun lockByToken(tokenHash: ByteArray): StoredLoginAttempt? = select("token_hash = ?", lock = true) { it.setBytes(1, tokenHash) }

    context(tx: Tx)
    fun save(id: Uuid, attempt: LoginAttempt) {
        tx.connection.prepareStatement("update login_attempts set used_at = ?, wrong_codes = ? where id = ?").use {
            it.setTimestamp(1, attempt.usedAt?.let(Timestamp::from))
            it.setInt(2, attempt.wrongCodes)
            it.setObject(3, id.toJavaUuid())
            it.executeUpdate()
        }
    }

    context(tx: Tx)
    private fun select(where: String, lock: Boolean, bind: (java.sql.PreparedStatement) -> Unit): StoredLoginAttempt? =
        tx.connection.prepareStatement(
            "select id, email, code_hash, return_to, expires_at, used_at, wrong_codes from login_attempts where $where${if (lock) " for update" else ""}",
        ).use {
            bind(it)
            it.executeQuery().use { rs ->
                if (!rs.next()) return null
                StoredLoginAttempt(
                    id = rs.getObject("id", java.util.UUID::class.java).toKotlinUuid(),
                    email = rs.getString("email"),
                    codeHash = rs.getBytes("code_hash"),
                    returnTo = rs.getString("return_to"),
                    attempt = LoginAttempt(
                        expiresAt = rs.getTimestamp("expires_at").toInstant(),
                        usedAt = rs.getTimestamp("used_at")?.toInstant(),
                        wrongCodes = rs.getInt("wrong_codes"),
                    ),
                )
            }
        }
}

class Sessions {
    context(tx: Tx)
    fun create(tokenHash: ByteArray, player: PlayerId, expiresAt: Instant) {
        tx.connection.prepareStatement("insert into sessions (token_hash, player_id, expires_at) values (?, ?, ?)").use {
            it.setBytes(1, tokenHash)
            it.setObject(2, player.value.toJavaUuid())
            it.setTimestamp(3, Timestamp.from(expiresAt))
            it.executeUpdate()
        }
    }

    context(tx: Tx)
    fun player(tokenHash: ByteArray, now: Instant): PlayerId? =
        tx.connection.prepareStatement("select player_id from sessions where token_hash = ? and expires_at > ?").use {
            it.setBytes(1, tokenHash)
            it.setTimestamp(2, Timestamp.from(now))
            it.executeQuery().use { rs -> if (rs.next()) rs.playerId("player_id") else null }
        }

    context(tx: Tx)
    fun purgeExpired(now: Instant): Int =
        tx.connection.prepareStatement("delete from sessions where expires_at <= ?").use {
            it.setTimestamp(1, Timestamp.from(now))
            it.executeUpdate()
        }

    context(tx: Tx)
    fun delete(tokenHash: ByteArray) {
        tx.connection.prepareStatement("delete from sessions where token_hash = ?").use {
            it.setBytes(1, tokenHash)
            it.executeUpdate()
        }
    }
}

private fun ResultSet.playerId(column: String) = PlayerId(getObject(column, java.util.UUID::class.java).toKotlinUuid())
