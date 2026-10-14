package gorefest.shell.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.Parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/** Links are bearer tokens: they must never reach the logs, which journald keeps. */
class LogSafetyTest {
    private val test = TestApp()

    @BeforeEach
    fun reset() = kotlinx.coroutines.runBlocking { test.reset() }

    @Test
    fun `a request line keeps the route and drops the query and the token`() {
        "/login/link?token=SECRET".loggable() shouldBe "/login/link"
        "/i/SECRET2".loggable() shouldBe "/i/{token}"
        "/i/SECRET2/og.png".loggable() shouldBe "/i/{token}/og.png"
        "/i/SECRET2?join=SECRET3".loggable() shouldBe "/i/{token}"
        "/p/SECRET4/join".loggable() shouldBe "/p/{token}/join"
        "/gamedays/abc/shelf".loggable() shouldBe "/gamedays/abc/shelf"
    }

    @Test
    fun `no token, code or return path reaches the log`() = test.webTest { client ->
        val captured = ListAppender<ILoggingEvent>().also { it.start() }
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        root.addAppender(captured)
        try {
            client.get("/login/link?token=SECRET")
            client.get("/i/SECRET2")
            client.get("/i/SECRET2/og.png")
            client.get("/p/SECRET4")
            client.get("/login/code?attempt=SECRET5&returnTo=SECRET6")
            client.submitForm("/login/code", Parameters.build { append("attempt", "SECRET5"); append("code", "123456") })
        } finally {
            root.detachAppender(captured)
        }

        val lines = captured.list.map { it.formattedMessage }
        lines.shouldNotBeEmpty()
        lines.joinToString("\n").let { log ->
            log shouldContain "/login/link"
            log shouldNotContain "SECRET"
            log shouldNotContain "123456"
        }
    }

    @Test
    fun `a login mail leaves neither the address nor the code in the log`() = test.webTest { client ->
        val captured = ListAppender<ILoggingEvent>().also { it.start() }
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        root.addAppender(captured)
        try {
            client.submitForm("/login", Parameters.build { append("email", "nina@example.com"); append("returnTo", "/") })
        } finally {
            root.detachAppender(captured)
        }

        val code = Regex("\\d{6}").find(test.mailbox.latestTo("nina@example.com")!!.subject)!!.value
        captured.list.joinToString("\n") { it.formattedMessage }.let { log ->
            log shouldContain "dev mailbox"
            log shouldNotContain "nina@example.com"
            log shouldNotContain code
        }
    }
}
