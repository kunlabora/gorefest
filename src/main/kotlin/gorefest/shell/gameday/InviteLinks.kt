package gorefest.shell.gameday

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

    private fun sign(payload: String): String =
        base64.encodeToString(Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(payload.toByteArray()).copyOf(16))
}
