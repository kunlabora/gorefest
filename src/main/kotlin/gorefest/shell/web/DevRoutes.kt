package gorefest.shell.web

import gorefest.shell.auth.DevMailbox
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Only registered in dev mode: shows the mail the app would have sent. */
fun Route.devRoutes(mailbox: DevMailbox) {
    get("/dev/mailbox") {
        call.respondHtml { devMailboxPage(mailbox.all) }
    }
}
