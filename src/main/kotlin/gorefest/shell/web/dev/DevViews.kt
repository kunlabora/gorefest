package gorefest.shell.web.dev

import gorefest.shell.auth.Mail
import gorefest.shell.web.components.*
import kotlinx.html.*

private val LINK = Regex("""https?://\S+""")

fun HTML.devMailboxPage(mails: List<Mail>) = page("Dev mailbox", appBar = { wordmark() }, mainClasses = "gf-landing", wide = true) { wrap {
    h1("gf-h1") { +"Dev mailbox" }
    if (mails.isEmpty()) emptyState("No mail yet", "Ask for a login code and it shows up here.")
    ul("gf-list") {
        mails.forEach { mail ->
            li("gf-row") {
                span("gf-row__main") {
                    span("gf-row__title") { +mail.subject }
                    span("gf-row__sub") { +"To ${mail.to} · ${mail.sentAt}" }
                    pre("gf-small") { style = "white-space: pre-wrap; overflow-wrap: anywhere"; +mail.text }
                    LINK.find(mail.text)?.let { link -> div { linkButton(link.value, "Open the login link", ButtonStyle.SECONDARY, ButtonSize.SMALL) } }
                }
            }
        }
    }
    p("gf-small") { +"Newest first. Mail lives in memory and is gone after a restart." }
} }
