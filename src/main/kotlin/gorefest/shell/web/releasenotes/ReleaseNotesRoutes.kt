package gorefest.shell.web.releasenotes

import gorefest.shell.auth.Login
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.sessionPlayer
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Public, like the privacy notice: it says nothing about people, and the footer links to it from signed-out pages too. A signed-in reader keeps the site bar. */
context(login: Login)
fun Route.releaseNotesRoutes(notes: ReleaseNotes = ReleaseNotes.bundled) {
    get("/release-notes") {
        val me = sessionPlayer()?.let { player -> player.name?.let { CurrentPlayer(player.id, it) } }
        call.respondHtml { releaseNotesPage(notes, me) }
    }
}
