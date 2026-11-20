package gorefest.shell.web

import gorefest.contract.Obligation
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import org.jsoup.nodes.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Escaping is the first line against injected script; the Content-Security-Policy is the second. */
class SecurityHeadersTest {
    private val test = TestApp()

    @BeforeEach
    fun reset() = kotlinx.coroutines.runBlocking { test.reset() }

    @Test
    @Obligation("WEB-21")
    fun `pages run scripts only from the site's own files and can't be framed`() = test.webTest { client ->
        val csp = client.get("/login").headers["Content-Security-Policy"]!!
        csp shouldContain "script-src 'self';"
        csp shouldNotContain "unsafe-eval"
        csp.substringAfter("script-src").substringBefore(";") shouldNotContain "unsafe-inline"
        csp shouldContain "frame-ancestors 'none'"
        csp shouldContain "object-src 'none'"
        csp shouldContain "base-uri 'none'"
        client.get("/login").headers["X-Content-Type-Options"] shouldBe "nosniff"
    }

    @Test
    @Obligation("WEB-21")
    fun `static files and missing pages carry the policy too`() = test.webTest { client ->
        client.get("/static/js/forms.js").headers["Content-Security-Policy"] shouldBe CONTENT_SECURITY_POLICY
        client.get("/no-such-page").headers["Content-Security-Policy"] shouldBe CONTENT_SECURITY_POLICY
    }

    @Test
    @Obligation("WEB-22")
    fun `a signed-out page's scripts all load files, and nothing carries an inline handler`() = test.webTest { client ->
        listOf("/login", "/privacy", "/release-notes").forEach { path -> client.get(path).page().shouldHaveNoInlineScript(path) }
    }

    @Test
    @Obligation("WEB-22")
    fun `a signed-in page's scripts all load files, and nothing carries an inline handler`() =
        test.webTest(session = kotlinx.coroutines.runBlocking { test.loggedIn("Tim") }) { client ->
            listOf("/", "/account", "/gamedays/new").forEach { path -> client.get(path).page().shouldHaveNoInlineScript(path) }
        }

    private fun Document.shouldHaveNoInlineScript(path: String) {
        val scripts = select("script")
        scripts.shouldNotBeEmpty()
        scripts.filter { it.attr("src").isBlank() || it.data().isNotBlank() }.map { "$path: ${it.outerHtml()}" }.shouldBeEmpty()
        allElements.flatMap { el -> el.attributes().filter { it.key.startsWith("on") }.map { "$path: <${el.tagName()} ${it.key}>" } }.shouldBeEmpty()
    }
}
