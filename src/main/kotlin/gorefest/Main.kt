package gorefest

import gorefest.shell.App
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
    val app = App(db, Clock.systemUTC())
    runBlocking { app.start() }

    embeddedServer(Netty, port = env("PORT", "8080").toInt(), host = env("HOST", "127.0.0.1")) {
        web(app)
    }.start(wait = true)
}
