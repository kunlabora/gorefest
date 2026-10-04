package gorefest.shell.web

import gorefest.shell.App
import gorefest.shell.auth.DevMailbox
import gorefest.shell.bgg.FakeBggApi
import gorefest.shell.web.auth.authRoutes
import gorefest.shell.web.bgg.bggRoutes
import gorefest.shell.web.bgg.collectionRoutes
import gorefest.shell.web.bgg.devBggRoutes
import gorefest.shell.web.checkpoint.checkpointRoutes
import gorefest.shell.web.datepoll.datePollRoutes
import gorefest.shell.web.dev.devRoutes
import gorefest.shell.web.gameday.gameDayRoutes
import gorefest.shell.web.gameday.readinessRoutes
import gorefest.shell.web.invite.inviteRoutes
import gorefest.shell.web.shelf.shelfRoutes
import gorefest.shell.web.suggestions.suggestionsRoutes
import gorefest.shell.web.taste.tasteRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.webjars.Webjars
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("gorefest.web")

private val NoIndex = createApplicationPlugin("NoIndex") {
    // Not onCall: Webjars answers (and completes) a call, e.g. a conditional 304, before that hook would run.
    onCallRespond { call, _ -> call.response.header("X-Robots-Tag", "noindex, nofollow, noarchive, noimageindex, nosnippet") }
}

fun Application.web(app: App) {
    install(CallLogging)
    // Caddy in front sets X-Forwarded-For. Safe to trust only because the app listens on localhost.
    install(XForwardedHeaders)
    install(Webjars)
    // Private by design: no search engine or archive should list this site, so nobody finds it by accident.
    install(NoIndex)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Unhandled error on ${call.request.local.uri}", cause)
            call.respondText("Something went wrong", status = HttpStatusCode.InternalServerError)
        }
    }
    routing {
        get("/health") {
            // The deploy script waits for this before switching over, so it checks the database too.
            val dbUp = runCatching { app.db.transaction { contextOf<gorefest.shell.db.Tx>().connection.isValid(2) } }.getOrDefault(false)
            if (dbUp) call.respondText("ok") else call.respondText("database unavailable", status = HttpStatusCode.ServiceUnavailable)
        }
        get("/robots.txt") { call.respondText("User-agent: *\nDisallow: /\n") }
        // Browsers ask for this by default, whatever the page's <link rel=icon> says.
        get("/favicon.ico") { call.respondRedirect("/static/icons/favicon.ico", permanent = false) }
        staticResources("/static", "static") {
            // Font files never change under the same name (a new version gets a new file), so browsers may keep them for a year.
            modify { url, call -> if (url.path.endsWith(".woff2")) call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable") }
        }
        context(app.login, app.config) { authRoutes() }
        context(app.ids, app.clock, app.login, app.config) { gameDayRoutes(app) }
        context(app.ids, app.clock, app.login, app.config) { datePollRoutes(app) }
        context(app.login, app.config) { inviteRoutes(app, app.invites) }
        context(app.login) { bggRoutes(app) }
        context(app.login) { collectionRoutes(app) }
        context(app.login) { shelfRoutes(app) }
        context(app.login) { tasteRoutes(app) }
        context(app.login) { suggestionsRoutes(app) }
        context(app.login) { checkpointRoutes(app) }
        context(app.login, app.config) { readinessRoutes(app) }
        if (app.config.dev && app.mailer is DevMailbox) devRoutes(app.mailer)
        if (app.config.dev && app.bggApi is FakeBggApi) devBggRoutes(app.bggApi)
    }
}
