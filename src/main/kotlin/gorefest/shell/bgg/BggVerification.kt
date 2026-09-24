package gorefest.shell.bgg

import gorefest.core.PlayerId
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The code a player pastes into their BGG profile. Derived from player and username with the
 * server key, so nothing is stored and it stays the same until they've pasted it.
 */
class BggVerification(secretKey: String) {
    private val key = SecretKeySpec(("bgg:" + secretKey).toByteArray(), "HmacSHA256")

    fun code(player: PlayerId, username: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal("$player:${username.lowercase()}".toByteArray())
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O, 1/I
        return "GORE-" + (0 until 6).map { alphabet[(mac[it].toInt() and 0xff) % alphabet.length] }.joinToString("")
    }

    fun isOnProfile(user: BggUser, code: String) = user.profileText.contains(code, ignoreCase = true)
}
