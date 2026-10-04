package gorefest.shell.gameday

import gorefest.core.DatePollId
import gorefest.core.GameDayId
import gorefest.core.gameday.GameDaySummary
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.uuid.Uuid

/**
 * Invite tokens: `{game day id as hex}.{version}.{signature}`, signed with the server key.
 * Nothing is stored; rotating bumps the version, so earlier tokens stop matching.
 */
class InviteLinks(secretKey: String) {
    private val key = SecretKeySpec(("invite:" + secretKey).toByteArray(), "HmacSHA256")
    private val base64 = Base64.getUrlEncoder().withoutPadding()

    fun token(gameDay: GameDaySummary): String = token(gameDay.id, gameDay.inviteVersion)

    fun token(gameDay: GameDayId, version: Int): String {
        val payload = "${gameDay.value.toHexString()}.$version"
        return "$payload.${sign(payload)}"
    }

    /** The game day and version a token was signed for, or null if it's not ours. */
    fun parse(token: String): Pair<GameDayId, Int>? {
        val parts = token.split('.')
        if (parts.size != 3) return null
        val (hex, version, signature) = parts
        if (!MessageDigest.isEqual(sign("$hex.$version").toByteArray(), signature.toByteArray())) return null
        val id = runCatching { Uuid.parseHex(hex) }.getOrNull() ?: return null
        return GameDayId(id) to (version.toIntOrNull() ?: return null)
    }

    /**
     * A date poll's invite token: `{poll id as hex}.{signature}`. Signed under its own prefix, so a game day
     * token can never pass for a poll token (or the other way round). A poll link works until the date is picked.
     */
    fun token(poll: DatePollId): String {
        val hex = poll.value.toHexString()
        return "$hex.${sign("poll:$hex")}"
    }

    fun parsePoll(token: String): DatePollId? {
        val parts = token.split('.')
        if (parts.size != 2) return null
        val (hex, signature) = parts
        if (!MessageDigest.isEqual(sign("poll:$hex").toByteArray(), signature.toByteArray())) return null
        return runCatching { Uuid.parseHex(hex) }.getOrNull()?.let(::DatePollId)
    }

    private fun sign(payload: String): String =
        base64.encodeToString(Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(payload.toByteArray()).copyOf(16))
}
