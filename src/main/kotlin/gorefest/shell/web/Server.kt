package gorefest.shell.web

import gorefest.shell.App
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.webjars.Webjars
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("gorefest.web")

fun Application.web(app: App) {
    install(CallLogging)
    install(Webjars)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Unhandled error on ${call.request.local.uri}", cause)
            call.respondText("Something went wrong", status = HttpStatusCode.InternalServerError)
        }
    }
    routing {
        get("/health") { call.respondText("ok") }
        context(app.ids, app.clock) { gameDayRoutes(app) }
    }
}
