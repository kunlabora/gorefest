package gorefest.core.auth

import java.time.Duration
import java.time.Instant

/**
 * One "send me a code" request: a magic link and a 6-digit code, sent in the same mail.
 * Either one works once; a used, expired or locked attempt is dead for both.
 */
data class LoginAttempt(val expiresAt: Instant, val usedAt: Instant? = null, val wrongCodes: Int = 0) {
    companion object {
        val TTL: Duration = Duration.ofMinutes(15)
        const val MAX_WRONG_CODES = 5

        fun startingAt(now: Instant) = LoginAttempt(expiresAt = now + TTL)
    }
}

sealed interface LoginCheck {
    data object Ok : LoginCheck
    data object Expired : LoginCheck
    data object AlreadyUsed : LoginCheck
    data object TooManyTries : LoginCheck
    data class WrongCode(val triesLeft: Int) : LoginCheck
}

/** The outcome of a check, and the attempt as it must be stored afterwards. */
data class Verification(val check: LoginCheck, val attempt: LoginAttempt)

fun LoginAttempt.checkLink(now: Instant): Verification =
    deadReason(now)?.let { Verification(it, this) } ?: Verification(LoginCheck.Ok, copy(usedAt = now))

fun LoginAttempt.checkCode(now: Instant, codeMatches: Boolean): Verification {
    deadReason(now)?.let { return Verification(it, this) }
    if (codeMatches) return Verification(LoginCheck.Ok, copy(usedAt = now))
    val wrong = copy(wrongCodes = wrongCodes + 1)
    val triesLeft = LoginAttempt.MAX_WRONG_CODES - wrong.wrongCodes
    return Verification(if (triesLeft > 0) LoginCheck.WrongCode(triesLeft) else LoginCheck.TooManyTries, wrong)
}

private fun LoginAttempt.deadReason(now: Instant): LoginCheck? = when {
    usedAt != null -> LoginCheck.AlreadyUsed
    wrongCodes >= LoginAttempt.MAX_WRONG_CODES -> LoginCheck.TooManyTries
    !now.isBefore(expiresAt) -> LoginCheck.Expired
    else -> null
}
