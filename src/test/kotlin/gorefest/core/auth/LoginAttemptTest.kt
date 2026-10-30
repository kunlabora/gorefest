package gorefest.core.auth

import gorefest.contract.Obligation
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class LoginAttemptTest {
    private val now = Instant.parse("2026-09-24T10:00:00Z")
    private val fresh = LoginAttempt.startingAt(now)

    @Test
    @Obligation("ACC-5")
    fun `a right code within 15 minutes logs in and uses up the attempt`() {
        val (check, attempt) = fresh.checkCode(now + Duration.ofMinutes(14), codeMatches = true)

        check shouldBe LoginCheck.Ok
        attempt.usedAt shouldBe now + Duration.ofMinutes(14)
    }

    @Test
    @Obligation("ACC-5")
    fun `a used attempt works for neither code nor link`() {
        val used = fresh.checkLink(now).attempt

        used.checkCode(now, codeMatches = true).check shouldBe LoginCheck.AlreadyUsed
        used.checkLink(now).check shouldBe LoginCheck.AlreadyUsed
    }

    @Test
    @Obligation("ACC-6")
    fun `after 15 minutes the attempt has expired`() {
        val later = now + LoginAttempt.TTL

        fresh.checkCode(later, codeMatches = true).check shouldBe LoginCheck.Expired
        fresh.checkLink(later).check shouldBe LoginCheck.Expired
    }

    @Test
    @Obligation("ACC-7")
    fun `wrong codes count down, and the fifth locks the attempt for the link too`() {
        var attempt = fresh
        val checks = (1..5).map { attempt.checkCode(now, codeMatches = false).also { attempt = it.attempt }.check }

        checks shouldBe listOf(
            LoginCheck.WrongCode(4), LoginCheck.WrongCode(3), LoginCheck.WrongCode(2), LoginCheck.WrongCode(1),
            LoginCheck.TooManyTries,
        )
        attempt.checkCode(now, codeMatches = true).check shouldBe LoginCheck.TooManyTries
        attempt.checkLink(now).check shouldBe LoginCheck.TooManyTries
    }

    @Test
    @Obligation("ACC-7")
    fun `failed checks on a dead attempt don't change it`() {
        val expired = now + LoginAttempt.TTL
        fresh.checkCode(expired, codeMatches = false).attempt shouldBe fresh
    }
}
