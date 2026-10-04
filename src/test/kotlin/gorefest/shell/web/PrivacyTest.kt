package gorefest.shell.web

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The site is for one friend group: search engines are told to stay away, and BGG is credited. */
class PrivacyTest {
    private val test = TestApp()

    @BeforeEach
    fun reset() = kotlinx.coroutines.runBlocking { test.reset() }

    @Test
    fun `robots txt disallows everything`() = test.webTest { client ->
        val response = client.get("/robots.txt")
        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldContain "Disallow: /"
    }

    @Test
    fun `every response says noindex`() = test.webTest { client ->
        val header = client.get("/login").headers["X-Robots-Tag"]
        header.shouldContainAll("noindex", "nofollow", "noarchive")
    }

    @Test
    fun `pages carry a robots meta tag and credit BoardGameGeek`() = test.webTest { client ->
        val page = client.get("/login").page()
        page.one("meta[name=robots]").attr("content") shouldContain "noindex"
        page.one(".gf-credit a").attr("href") shouldBe "https://boardgamegeek.com"
    }

    @Test
    fun `pages link a favicon and the default favicon path leads to one`() = test.webTest { client ->
        val page = client.get("/login").page()
        page.select("link[rel=icon]").map { it.attr("href") } shouldBe listOf("/static/icons/favicon.svg", "/static/icons/favicon.ico")
        page.one("link[rel=apple-touch-icon]").attr("href") shouldBe "/static/icons/apple-touch-icon.png"
        client.get("/favicon.ico").headers[HttpHeaders.Location] shouldBe "/static/icons/favicon.ico"
        client.get("/static/icons/favicon.ico").status shouldBe HttpStatusCode.OK
        client.get("/static/icons/favicon.svg").status shouldBe HttpStatusCode.OK
        client.get("/static/icons/apple-touch-icon.png").status shouldBe HttpStatusCode.OK
    }

    private fun String?.shouldContainAll(vararg parts: String) = parts.forEach { this.orEmpty() shouldContain it }
}
