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

data class Player(val id: PlayerId, val email: String, val name: String?)

class Players {
    /** The player with this email, created with [newId] if there's none yet. */
    context(tx: Tx)
    fun findOrCreate(email: String, newId: PlayerId): Player {
        tx.connection.prepareStatement("insert into players (id, email) values (?, ?) on conflict (email) do nothing").use {
            it.setObject(1, newId.value.toJavaUuid())
            it.setString(2, email)
            it.executeUpdate()
        }
        return tx.connection.prepareStatement("select id, email, name from players where email = ?").use {
            it.setString(1, email)
            it.executeQuery().use { rs -> rs.next(); rs.toPlayer() }
        }
    }

    context(tx: Tx)
    fun find(id: PlayerId): Player? =
        tx.connection.prepareStatement("select id, email, name from players where id = ?").use {
            it.setObject(1, id.value.toJavaUuid())
            it.executeQuery().use { rs -> if (rs.next()) rs.toPlayer() else null }
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

    private fun ResultSet.toPlayer() = Player(playerId("id"), getString("email"), getString("name"))
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

    /** Locks the row, so two concurrent checks of the same attempt can't both succeed. */
    context(tx: Tx)
    fun lockById(id: Uuid): StoredLoginAttempt? = selectForUpdate("id = ?") { it.setObject(1, id.toJavaUuid()) }

    context(tx: Tx)
    fun lockByToken(tokenHash: ByteArray): StoredLoginAttempt? = selectForUpdate("token_hash = ?") { it.setBytes(1, tokenHash) }

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
    private fun selectForUpdate(where: String, bind: (java.sql.PreparedStatement) -> Unit): StoredLoginAttempt? =
        tx.connection.prepareStatement(
            "select id, email, code_hash, return_to, expires_at, used_at, wrong_codes from login_attempts where $where for update",
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
    fun delete(tokenHash: ByteArray) {
        tx.connection.prepareStatement("delete from sessions where token_hash = ?").use {
            it.setBytes(1, tokenHash)
            it.executeUpdate()
        }
    }
}

private fun ResultSet.playerId(column: String) = PlayerId(getObject(column, java.util.UUID::class.java).toKotlinUuid())
