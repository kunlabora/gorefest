package gorefest

import gorefest.shell.App
import gorefest.shell.AppConfig
import gorefest.shell.auth.DevMailbox
import gorefest.shell.db.Database
import gorefest.shell.web.web
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import java.time.Clock

fun main() {
    fun env(name: String, default: String) = System.getenv(name) ?: default

    val db = Database.connect(
        url = env("DATABASE_URL", "jdbc:postgresql://localhost:5432/gorefest"),
        user = env("DATABASE_USER", "gorefest"),
        password = env("DATABASE_PASSWORD", "gorefest"),
    )
    val config = AppConfig.fromEnv()
    // A real mail provider is wired up with the deploy story (S1); until then only dev mode runs.
    check(config.dev) { "No mail provider configured yet: run with GOREFEST_ENV=dev" }
    val app = App(db, Clock.systemUTC(), config = config, mailer = DevMailbox())
    runBlocking { app.start() }

    embeddedServer(Netty, port = env("PORT", "8080").toInt(), host = env("HOST", "127.0.0.1")) {
        web(app)
    }.start(wait = true)
}
