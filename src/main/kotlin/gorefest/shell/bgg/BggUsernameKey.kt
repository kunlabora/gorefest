package gorefest.shell.bgg

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * What the log stores in place of a BGG username: a keyed hash of the lowercased name, so the log can tell that a
 * player links the same account again without holding the name. Rotating `SECRET_KEY` changes every key; then the
 * same account linked again counts as a different one, and the player has to unlink first.
 */
class BggUsernameKey(secretKey: String) {
    private val key = SecretKeySpec(("bgg:" + secretKey).toByteArray(), "HmacSHA256")

    fun of(username: String): String =
        Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal("username:${username.lowercase()}".toByteArray())
            .joinToString("") { "%02x".format(it) }
}
