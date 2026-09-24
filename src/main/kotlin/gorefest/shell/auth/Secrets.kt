package gorefest.shell.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.uuid.Uuid

/**
 * Login tokens, codes and session tokens. These must be unpredictable, so unlike ids they don't
 * go through [gorefest.shell.IdGenerator]; tests read them from the mailbox or a cookie instead.
 */
class Secrets(secretKey: String) {
    private val random = SecureRandom()
    private val key = SecretKeySpec(secretKey.toByteArray(), "HmacSHA256")

    /** 256 random bits, URL-safe. */
    fun token(): String = ByteArray(32).also(random::nextBytes).let(Base64.getUrlEncoder().withoutPadding()::encodeToString)

    fun code(): String = random.nextInt(1_000_000).toString().padStart(6, '0')

    /** Tokens have enough entropy for a plain hash. */
    fun hash(token: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())

    /** A 6-digit code doesn't: without the server key, a leaked hash would fall to a million guesses. */
    fun codeHash(attempt: Uuid, code: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal("$attempt:$code".toByteArray())

    fun matches(a: ByteArray, b: ByteArray) = MessageDigest.isEqual(a, b)
}
