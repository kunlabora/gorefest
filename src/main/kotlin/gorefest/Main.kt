package gorefest

import gorefest.shell.App
import gorefest.shell.AppConfig
import gorefest.shell.auth.DevMailbox
import gorefest.shell.auth.HttpMailer
import gorefest.shell.bgg.DevCatalog
import gorefest.shell.bgg.FakeBggApi
import gorefest.shell.bgg.UnavailableBggApi
import gorefest.shell.bgg.XmlBggApi
import gorefest.shell.db.Database
import gorefest.shell.web.web
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.Clock

fun main() {
    fun env(name: String, default: String) = AppConfig.setting(name) ?: default

    val db = Database.connect(
        url = env("DATABASE_URL", "jdbc:postgresql://localhost:5432/gorefest"),
        user = env("DATABASE_USER", "gorefest"),
        password = env("DATABASE_PASSWORD", "gorefest"),
    )
    val config = AppConfig.fromEnv()
    // Outside dev mode, mail and BGG must be real: fail at startup rather than on the first login.
    val mailer = HttpMailer.fromEnv() ?: if (config.dev) DevMailbox() else error("Set MAIL_PROVIDER, MAIL_API_KEY and MAIL_FROM")
    // BGG's XML API needs a registered application's token. Without one, dev mode uses a fake BGG
    // and production runs with BGG switched off until the token arrives.
    val bggApi = AppConfig.setting("BGG_TOKEN")?.let(::XmlBggApi)
        ?: if (config.dev) FakeBggApi(DevCatalog.games + DevCatalog.covers) else UnavailableBggApi.also { LoggerFactory.getLogger("gorefest").warn("No BGG_TOKEN: BGG features are off") }
    val app = App(db, Clock.systemUTC(), config = config, mailer = mailer, bggApi = bggApi)
    runBlocking { app.start() }
    app.startBackgroundJobs()

    embeddedServer(Netty, port = env("PORT", "8080").toInt(), host = env("HOST", "127.0.0.1")) {
        web(app)
    }.start(wait = true)
}
