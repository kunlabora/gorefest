package gorefest.shell.web

import gorefest.core.PlayerId
import gorefest.shell.App
import gorefest.shell.AppConfig
import gorefest.shell.IdGenerator
import gorefest.shell.TestClock
import gorefest.shell.TestDatabase
import gorefest.shell.auth.DevMailbox
import gorefest.shell.bgg.FakeBggApi
import gorefest.shell.auth.Players
import gorefest.shell.auth.Secrets
import gorefest.shell.auth.Sessions
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Duration
import kotlin.uuid.Uuid

/** A fresh app per test class: sequential ids, a movable clock and an in-memory mailbox. */
class TestApp(config: AppConfig = AppConfig.dev()) {
    val clock = TestClock()
    val mailbox = DevMailbox()
    val bgg = FakeBggApi()
    val app = App(TestDatabase.db, clock, IdGenerator.sequential(), config, mailbox, bgg, bggInterval = Duration.ZERO, bggRetry = Duration.ofMillis(1))

    suspend fun reset() {
        TestDatabase.reset()
        app.start()
    }

    /**
     * Runs [block] against the app. The client keeps cookies and doesn't follow redirects;
     * with [session] every request carries that session cookie.
     */
    fun webTest(session: String? = null, block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application { web(app) }
        block(createClient {
            followRedirects = false
            install(HttpCookies)
            if (session != null) defaultRequest { header(HttpHeaders.Cookie, "$SESSION_COOKIE=$session") }
        })
    }

    /** A named player with a session, skipping the mail round trip. Returns the session token. */
    suspend fun loggedIn(name: String, id: PlayerId = PlayerId(Uuid.random())): String {
        val secrets = Secrets(app.config.secretKey)
        val token = secrets.token()
        app.db.transaction {
            val player = Players().findOrCreate("${name.lowercase()}@example.com", id)
            Players().rename(player.id, name)
            Sessions().create(secrets.hash(token), player.id, clock.instant() + Duration.ofDays(365))
        }
        return token
    }
}

suspend fun HttpClient.htmxPost(url: String, form: Parameters = Parameters.Empty, currentUrl: String? = null) =
    submitForm(url, form) {
        header("HX-Request", "true")
        if (currentUrl != null) header("HX-Current-URL", currentUrl)
    }
