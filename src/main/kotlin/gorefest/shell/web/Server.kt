package gorefest.shell.web

import gorefest.shell.App
import gorefest.shell.auth.DevMailbox
import gorefest.shell.bgg.FakeBggApi
import gorefest.shell.web.approval.approvalRoutes
import gorefest.shell.web.account.accountRoutes
import gorefest.shell.web.privacy.privacyRoutes
import gorefest.shell.web.auth.authRoutes
import gorefest.shell.web.bgg.bggRoutes
import gorefest.shell.web.bgg.collectionRoutes
import gorefest.shell.web.bgg.devBggRoutes
import gorefest.shell.web.checkpoint.checkpointRoutes
import gorefest.shell.web.datepoll.datePollRoutes
import gorefest.shell.web.gamenight.gameNightRoutes
import gorefest.shell.web.dev.devRoutes
import gorefest.shell.web.gameday.gameDayRoutes
import gorefest.shell.web.gameday.readinessRoutes
import gorefest.shell.web.invite.inviteRoutes
import gorefest.shell.web.request.requestRoutes
import gorefest.shell.web.shelf.shelfRoutes
import gorefest.shell.web.suggestions.suggestionsRoutes
import gorefest.shell.web.taste.tasteRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.application.Application
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.http.content.ETagProvider
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.content.LastModifiedVersion
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.content.versions
import io.ktor.http.withCharset
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.webjars.Webjars
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("gorefest.web")

/** Nobody may index the site, except that Slack's link-preview bots may read pages to show a short card when someone pastes a link. */
private const val ROBOTS_TXT = "User-agent: Slackbot-LinkExpanding\nUser-agent: Slackbot\nAllow: /\n\nUser-agent: *\nDisallow: /\n"

private val NoIndex = createApplicationPlugin("NoIndex") {
    // Not onCall: Webjars answers (and completes) a call, e.g. a conditional 304, before that hook would run.
    onCallRespond { call, _ -> call.response.header("X-Robots-Tag", "noindex, nofollow, noarchive, noimageindex, nosnippet") }
}

/** Pages are personal: `no-store` keeps them out of the history cache, so Back after logout doesn't show the last person's page. Assets set their own caching. */
private val NoStorePages = createApplicationPlugin("NoStorePages") {
    onCallRespond { call, _ ->
        val path = call.request.local.uri
        val isAsset = path.startsWith("/static/") || path.startsWith("/webjars/")
        if (!isAsset && !call.response.headers.contains(HttpHeaders.CacheControl)) call.response.header(HttpHeaders.CacheControl, "no-store")
    }
}

/**
 * Assets are validated by ETag alone. Every file in the jar carries the same 1980 timestamp, so with a Last-Modified the
 * browser's If-Modified-Since always matched and answered 304 with the old file after a deploy (the old CSS stayed).
 * Runs before [ConditionalHeaders], which decides the 304.
 */
private val EtagOnlyAssets = createApplicationPlugin("EtagOnlyAssets") {
    onCallRespond { call, body ->
        val path = call.request.local.uri
        val isAsset = path.startsWith("/static/") || path.startsWith("/webjars/")
        if (isAsset && body is OutgoingContent) body.versions = body.versions.filterNot { it is LastModifiedVersion }
    }
}

/** A 404 with nothing to say (the routes' `respond(NotFound)`, or no route at all) gets the on-brand page. Pages that answer 404 with their own body, like a dead invite, are left alone. */
private val NotFoundPage = createApplicationPlugin("NotFoundPage") {
    onCallRespond { _, body ->
        if (body == HttpStatusCode.NotFound) transformBody {
            TextContent("<!DOCTYPE html>\n" + createHTML().html { notFoundPage() }, ContentType.Text.Html.withCharset(Charsets.UTF_8), HttpStatusCode.NotFound)
        }
    }
}

fun Application.web(app: App) {
    // Links carry bearer tokens in the path and query: log the method, the status and a scrubbed path, nothing else.
    install(CallLogging) {
        format { call -> "${call.response.status()}: ${call.request.httpMethod.value} ${call.request.uri.loggable()}" }
    }
    // Caddy in front sets X-Forwarded-For. Safe to trust only because the app listens on localhost.
    install(XForwardedHeaders)
    install(Webjars)
    // Private by design: no search engine or archive should list this site, so nobody finds it by accident.
    install(NoIndex)
    install(NoStorePages)
    install(AutoHeadResponse)
    install(EtagOnlyAssets)
    install(ConditionalHeaders)
    install(NotFoundPage)
    install(StatusPages) {
        // A malformed request is the client's problem: no error log, no stack trace.
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respondText("Unsupported media type", status = HttpStatusCode.UnsupportedMediaType)
        }
        exception<ContentTransformationException> { call, _ ->
            call.respondText("Bad request", status = HttpStatusCode.BadRequest)
        }
        exception<BadRequestException> { call, cause ->
            log.warn("Bad request on ${call.request.local.uri.loggable()}: ${cause.message}")
            call.respondText("Bad request", status = HttpStatusCode.BadRequest)
        }
        exception<Throwable> { call, cause ->
            log.error("Unhandled error on ${call.request.local.uri.loggable()}", cause)
            call.respondText("Something went wrong", status = HttpStatusCode.InternalServerError)
        }
    }
    routing {
        get("/health") {
            // The deploy script waits for this before switching over, so it checks the database too.
            val dbUp = runCatching { app.db.transaction { contextOf<gorefest.shell.db.Tx>().connection.isValid(2) } }.getOrDefault(false)
            if (dbUp) call.respondText("ok") else call.respondText("database unavailable", status = HttpStatusCode.ServiceUnavailable)
        }
        get("/robots.txt") { call.respondText(ROBOTS_TXT) }
        // Browsers ask for this by default, whatever the page's <link rel=icon> says.
        get("/favicon.ico") { call.respondRedirect("/static/icons/favicon.ico", permanent = false) }
        staticResources("/static", "static") {
            // The ETag (hash of the file) lets browsers revalidate (a 304) instead of re-downloading on every page.
            etag(ETagProvider.StrongSha256)
            // Font files never change under the same name (a new version gets a new file), so browsers may keep them for a year.
            modify { url, call ->
                val cacheControl = if (url.path.endsWith(".woff2")) "public, max-age=31536000, immutable" else "no-cache"
                call.response.header(HttpHeaders.CacheControl, cacheControl)
            }
        }
        context(app.login, app.config) { authRoutes() }
        context(app.login, app.config) { approvalRoutes() }
        context(app.config) { privacyRoutes() }
        context(app.login, app.config) { accountRoutes(app) }
        context(app.ids, app.clock, app.login, app.config) { gameDayRoutes(app) }
        context(app.ids, app.clock, app.login, app.config) { datePollRoutes(app) }
        context(app.login, app.config) { inviteRoutes(app, app.invites) }
        context(app.login) { bggRoutes(app) }
        context(app.login) { collectionRoutes(app) }
        context(app.login) { shelfRoutes(app) }
        context(app.login) { requestRoutes(app) }
        context(app.login) { tasteRoutes(app) }
        context(app.login) { suggestionsRoutes(app) }
        context(app.login) { gameNightRoutes(app) }
        context(app.login) { checkpointRoutes(app) }
        context(app.login, app.config) { readinessRoutes(app) }
        if (app.config.dev && app.mailer is DevMailbox) devRoutes(app.mailer)
        if (app.config.dev && app.bggApi is FakeBggApi) devBggRoutes(app.bggApi)
    }
}
