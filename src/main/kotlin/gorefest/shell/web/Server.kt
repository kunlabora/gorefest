package gorefest.shell.web

import gorefest.shell.App
import gorefest.shell.auth.DevMailbox
import gorefest.shell.bgg.FakeBggApi
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
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
        staticResources("/static", "static")
        context(app.login, app.config) { authRoutes() }
        context(app.ids, app.clock, app.login, app.config) { gameDayRoutes(app) }
        context(app.login, app.config) { inviteRoutes(app, app.invites) }
        context(app.login) { bggRoutes(app) }
        context(app.login) { collectionRoutes(app) }
        context(app.login) { bringRoutes(app) }
        context(app.login) { wizardRoutes(app) }
        context(app.login) { tableRoutes(app) }
        if (app.config.dev && app.mailer is DevMailbox) devRoutes(app.mailer)
        if (app.config.dev && app.bggApi is FakeBggApi) devBggRoutes(app.bggApi)
    }
}
