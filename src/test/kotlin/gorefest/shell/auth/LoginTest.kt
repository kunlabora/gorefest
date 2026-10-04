package gorefest.shell.auth

import gorefest.core.auth.LoginCheck
import gorefest.shell.IdGenerator
import gorefest.shell.TestClock
import gorefest.shell.TestDatabase
import gorefest.shell.db.Tx
import gorefest.shell.web.TestApp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Duration
import kotlin.uuid.Uuid

class LoginTest {
    private val test = TestApp()
    private val clock: TestClock = test.clock
    private val mailbox = test.mailbox
    private val login = test.app.login
    private val email = "kim@example.com"

    @BeforeEach
    fun reset() = runBlocking { test.reset() }

    private suspend fun ask(address: String = "a"): Login.Requested = login.request(email, "/", address)
    private suspend fun attemptId() = ask().shouldBeInstanceOf<Login.Requested.Sent>().attempt
    private fun code() = mailbox.latestTo(email)!!.subject.substringAfter("code: ")
    private fun token() = mailbox.latestTo(email)!!.text.substringAfter("token=").lineSequence().first().trim()

    private suspend fun count(table: String): Int = TestDatabase.db.transaction {
        contextOf<Tx>().connection.createStatement().use { st ->
            st.executeQuery("select count(*) from $table").use { it.next(); it.getInt(1) }
        }
    }

    @Test
    fun `a code works once`() = runBlocking<Unit> {
        val id = attemptId()
        login.withCode(id, code()).shouldBeInstanceOf<LoginResult.LoggedIn>()
        login.withCode(id, code()).shouldBeInstanceOf<LoginResult.Failed>().check shouldBe LoginCheck.AlreadyUsed
    }

    @Test
    fun `a link works once, and kills the code`() = runBlocking<Unit> {
        val id = attemptId()
        login.withLink(token()).shouldBeInstanceOf<LoginResult.LoggedIn>()
        login.withLink(token()).shouldBeInstanceOf<LoginResult.Failed>().check shouldBe LoginCheck.AlreadyUsed
        login.withCode(id, code()).shouldBeInstanceOf<LoginResult.Failed>().check shouldBe LoginCheck.AlreadyUsed
    }

    @Test
    fun `the link is dead after the code was used`() = runBlocking<Unit> {
        val id = attemptId()
        login.withCode(id, code()).shouldBeInstanceOf<LoginResult.LoggedIn>()
        login.withLink(token()).shouldBeInstanceOf<LoginResult.Failed>().check shouldBe LoginCheck.AlreadyUsed
        login.emailForLink(token()) shouldBe null
    }

    @Test
    fun `an attempt expires after 15 minutes`() = runBlocking<Unit> {
        val id = attemptId()
        clock.advance(Duration.ofMinutes(15))
        login.withCode(id, code()).shouldBeInstanceOf<LoginResult.Failed>().check shouldBe LoginCheck.Expired
        login.withLink(token()).shouldBeInstanceOf<LoginResult.Failed>().check shouldBe LoginCheck.Expired
    }

    @Test
    fun `five wrong codes lock the attempt, even for the right code`() = runBlocking<Unit> {
        val id = attemptId()
        val wrong = if (code() == "000000") "111111" else "000000"
        repeat(4) { login.withCode(id, wrong).shouldBeInstanceOf<LoginResult.Failed>().check.shouldBeInstanceOf<LoginCheck.WrongCode>() }
        login.withCode(id, wrong).shouldBeInstanceOf<LoginResult.Failed>().check shouldBe LoginCheck.TooManyTries
        login.withCode(id, code()).shouldBeInstanceOf<LoginResult.Failed>().check shouldBe LoginCheck.TooManyTries
        login.withLink(token()).shouldBeInstanceOf<LoginResult.Failed>().check shouldBe LoginCheck.TooManyTries
    }

    @Test
    fun `an unknown attempt or token is unknown`() = runBlocking<Unit> {
        login.withCode(Uuid.random(), "123456") shouldBe LoginResult.UnknownAttempt
        login.withLink("nope") shouldBe LoginResult.UnknownAttempt
    }

    @Test
    fun `checking the pending page or link does not use the attempt up`() = runBlocking<Unit> {
        val id = attemptId()
        login.pending(id)!!.email shouldBe email
        login.emailForLink(token()) shouldBe email
        login.withLink(token()).shouldBeInstanceOf<LoginResult.LoggedIn>()
    }

    @Test
    fun `at most five mails per email per hour`() = runBlocking<Unit> {
        repeat(5) { ask("a$it").shouldBeInstanceOf<Login.Requested.Sent>() }
        ask("fresh") shouldBe Login.Requested.TooMany
        clock.advance(Duration.ofMinutes(61))
        ask("fresh").shouldBeInstanceOf<Login.Requested.Sent>()
    }

    @Test
    fun `parallel requests cannot get past the per-email limit`() = runBlocking<Unit> {
        val results = (1..12).map { i -> async(Dispatchers.Default) { ask("addr$i") } }.awaitAll()
        results.count { it is Login.Requested.Sent } shouldBe Login.MAILS_PER_EMAIL_PER_HOUR
        count("login_attempts") shouldBe Login.MAILS_PER_EMAIL_PER_HOUR
    }

    @Test
    fun `a mail that fails to send does not count against the limit`() = runBlocking<Unit> {
        val failing = Login(TestDatabase.db, test.app.commands, { error("provider down") }, test.app.config, clock, IdGenerator.sequential())
        repeat(10) { shouldThrow<IllegalStateException> { failing.request(email, "/", "a$it") } }
        count("login_attempts") shouldBe 0
        repeat(5) { ask("b$it").shouldBeInstanceOf<Login.Requested.Sent>() }
    }

    @Test
    fun `purge drops expired sessions and day-old attempts only`() = runBlocking<Unit> {
        val id = attemptId()
        login.withCode(id, code()).shouldBeInstanceOf<LoginResult.LoggedIn>() // one live session
        clock.advance(Duration.ofHours(2))
        ask("x") // a fresh attempt
        count("login_attempts") shouldBe 2
        TestDatabase.db.transaction {
            contextOf<Tx>().connection.prepareStatement("insert into sessions (token_hash, player_id, expires_at) select ?, id, ? from players limit 1").use {
                it.setBytes(1, byteArrayOf(1))
                it.setTimestamp(2, Timestamp.from(clock.instant() - Duration.ofMinutes(1)))
                it.executeUpdate()
            }
        }
        count("sessions") shouldBe 2
        login.purge() shouldBe 1 // only the expired session
        clock.advance(Duration.ofHours(23))
        login.purge() shouldBe 1 // the first attempt is now over a day old
        count("login_attempts") shouldBe 1
        count("sessions") shouldBe 1
    }

    @Test
    fun `the per-address limiter forgets idle keys`() {
        val limiter = RateLimiter(2, Duration.ofHours(1), clock)
        repeat(50) { limiter.tryAcquire("k$it") }
        limiter.trackedKeys shouldBe 50
        clock.advance(Duration.ofHours(2))
        limiter.tryAcquire("new")
        limiter.trackedKeys shouldBe 1
    }
}
