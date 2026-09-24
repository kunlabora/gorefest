package gorefest.shell.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import kotlinx.html.TagConsumer
import kotlinx.html.stream.createHTML
import java.net.URI
import java.net.URLEncoder

val RoutingContext.isHtmx get() = call.request.headers["HX-Request"] == "true"

/** Full redirect for plain requests, `HX-Redirect` for htmx requests. */
suspend fun RoutingContext.redirect(to: String) {
    if (isHtmx) {
        call.response.header("HX-Redirect", to)
        call.respond(HttpStatusCode.NoContent)
    } else {
        call.respondRedirect(to)
    }
}

/** Renders a single top-level element for htmx to swap in. */
suspend fun RoutingContext.respondFragment(status: HttpStatusCode = HttpStatusCode.OK, content: TagConsumer<String>.() -> Unit) {
    val html = createHTML().apply(content).finalize()
    call.respondText(html, ContentType.Text.Html, status)
}

/** The page the user is looking at: for htmx requests that's the page that made the request. */
fun RoutingContext.currentPage(): String {
    call.request.headers["HX-Current-URL"]?.let { url ->
        val uri = runCatching { URI(url) }.getOrNull() ?: return "/"
        return uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
    }
    return if (call.request.local.method.value == "GET") call.request.uri else "/"
}

/** Only same-site paths, so `returnTo` can't send people to another site. */
fun safeReturnTo(path: String?): String =
    if (path != null && path.startsWith("/") && !path.startsWith("//") && !path.startsWith("/\\")) path else "/"

fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8)
