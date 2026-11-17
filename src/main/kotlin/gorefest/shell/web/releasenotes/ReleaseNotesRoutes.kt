package gorefest.shell.web.releasenotes

import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Public, like the privacy notice: it says nothing about people, and the footer links to it from signed-out pages too. */
fun Route.releaseNotesRoutes(notes: ReleaseNotes = ReleaseNotes.bundled) {
    get("/release-notes") {
        call.respondHtml { releaseNotesPage(notes) }
    }
}
