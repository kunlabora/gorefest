package gorefest.shell.web

import gorefest.shell.auth.DevMailbox
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.ul

private val LINK = Regex("""https?://\S+""")

/** Only registered in dev mode: shows the mail the app would have sent. */
fun Route.devRoutes(mailbox: DevMailbox) {
    get("/dev/mailbox") {
        call.respondHtml {
            page("Dev mailbox", appBar = { appBarTitle("Dev mailbox") }) {
                h1("gf-h1") { +"Dev mailbox" }
                val mails = mailbox.all
                if (mails.isEmpty()) emptyState("No mail yet", "Ask for a login code and it shows up here.")
                ul("gf-list") {
                    mails.forEach { mail ->
                        li("gf-row") {
                            span("gf-row__main") {
                                span("gf-row__title") { +mail.subject }
                                span("gf-row__sub") { +"To ${mail.to} · ${mail.sentAt}" }
                                pre("gf-small") { style = "white-space: pre-wrap; overflow-wrap: anywhere"; +mail.text }
                                LINK.find(mail.text)?.let { link ->
                                    div { a(link.value, classes = "gf-btn gf-btn--secondary gf-btn--sm") { +"Open the login link" } }
                                }
                            }
                        }
                    }
                }
                p("gf-small") { +"Newest first. Mail lives in memory and is gone after a restart." }
            }
        }
    }
}
