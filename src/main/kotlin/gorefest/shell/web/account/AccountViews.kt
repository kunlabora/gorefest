package gorefest.shell.web.account

import gorefest.shell.account.OpenRequest
import gorefest.shell.account.RequestKind
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import kotlinx.html.*

/** What the account page needs: who you are, which requests are open, and any upcoming game days you host. */
data class AccountView(
    val name: String,
    val email: String,
    val pending: Set<RequestKind>,
    val hosting: List<String>,
    val emailChanged: Boolean = false,
    val emailError: String? = null,
)

context(me: CurrentPlayer)
fun HTML.accountPage(view: AccountView) = page("Your account", site = SiteBar(me, null), mainClasses = "gf-landing") {
    wrap {
        div("gf-split__copy") {
            p("gf-kicker") { +"Your account" }
            h1("gf-h1 gf-h1--landing") { +view.name }
            ul("gf-facts") { li { +view.email } }
        }
    }
    wrap {
        div("gf-stack") {
            h2("gf-h2") { +"Your email" }
            if (view.emailChanged) notice("check", NoticeKind.GOOD) { +"Done. That's your address now." }
            p("gf-muted") { +"We send a code to the new address first, and the old one hears about the change." }
            form(action = "/account/email", method = FormMethod.post, classes = if (view.emailError != null) "gf-signin gf-field--invalid" else "gf-signin") {
                id = "email-change"
                label("gf-label") { htmlFor = "email"; +"New email address" }
                div("gf-signin__row") {
                    input(InputType.email, name = "email", classes = "gf-input") {
                        id = "email"; required = true; placeholder = "you@example.com"
                        attributes["autocomplete"] = "email"
                        if (view.emailError != null) attributes["aria-invalid"] = "true"
                    }
                    button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.LARGE)) { +"Send a code" }
                }
                if (view.emailError != null) fieldError(view.emailError)
            }
        }
    }
    band(sunk = true) {
        div("gf-stack") {
            h2("gf-h2") { +"Your data" }
            p("gf-muted") { +"Both of these go to me by message, and I do them by hand. See what's kept on the "; a("/privacy", classes = "gf-link") { +"privacy page" }; +"." }
            request(RequestKind.EXPORT, "Send me my data", "I'll mail you a file with everything Boardfest has about you.", view.pending)
            request(
                RequestKind.DELETE, "Delete my account",
                "Your email, name, collection and sign-ins are removed. Your answers stay in the game history under a number that points at nobody.",
                view.pending, confirm = "Ask for your account to be deleted?", danger = true,
            ) {
                if (view.hosting.isNotEmpty()) {
                    p("gf-small gf-muted") {
                        +"You host ${view.hosting.joinToString(", ")}. It stays for the others, with a host nobody can see anymore. Cancel it first if it shouldn't happen."
                    }
                }
            }
        }
    }
}

private fun FlowContent.request(
    kind: RequestKind,
    label: String,
    text: String,
    pending: Set<RequestKind>,
    confirm: String? = null,
    danger: Boolean = false,
    extra: FlowContent.() -> Unit = {},
) = section("gf-card gf-account-request") {
    id = "request-${kind.name.lowercase()}"
    h3("gf-h3") { +label }
    p { +text }
    extra()
    if (kind in pending) {
        notice("check", NoticeKind.GOOD) { +"Asked. I'll get back to you." }
    } else {
        form(action = "/account/${kind.name.lowercase()}", method = FormMethod.post) {
            if (confirm != null) confirmOnSubmit(confirm)
            button(type = ButtonType.submit, classes = buttonClasses(if (danger) ButtonStyle.SECONDARY else ButtonStyle.PRIMARY, ButtonSize.NORMAL)) { +label }
        }
    }
}

context(me: CurrentPlayer)
fun HTML.requestsPage(open: List<OpenRequest>) = page("Account requests", site = SiteBar(me, Nav.GAME_DAYS), mainClasses = "gf-landing") {
    wrap {
        div("gf-split__copy") {
            p("gf-kicker") { +"Admin" }
            h1("gf-h1 gf-h1--landing") { +"Account requests" }
            p("gf-lede") { +"People who asked to delete their account or to get their data." }
        }
    }
    band(sunk = true) {
        div("gf-stack") {
            if (open.isEmpty()) emptyState("Nothing open", "Requests show up here, and in the channel.")
            else ul("gf-list") { open.forEach { row(it) } }
        }
    }
}

private fun UL.row(request: OpenRequest) = li("gf-row gf-row--approval") {
    val player = request.player
    avatar(player.id, player.name)
    span("gf-row__main") {
        span("gf-row__title") { +player.name.orEmpty() }
        span("gf-row__sub") { +"${if (request.kind == RequestKind.DELETE) "Delete the account" else "Send the data"} · ${player.email}" }
    }
    div("gf-row__actions") {
        val base = "/admin/requests/${player.id.value}"
        when (request.kind) {
            RequestKind.DELETE -> form(action = "$base/erase", method = FormMethod.post) {
                confirmOnSubmit("Delete ${player.name ?: "this"}'s account for good?")
                button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.NORMAL)) { +"Delete account" }
            }
            RequestKind.EXPORT -> {
                a("$base/export", classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.NORMAL)) { +"Download their data" }
                form(action = "$base/export/done", method = FormMethod.post) {
                    button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.GHOST, ButtonSize.NORMAL)) { +"Sent it" }
                }
            }
        }
    }
}

context(me: CurrentPlayer)
fun HTML.emailCodePage(newEmail: String, error: String? = null) = page("Confirm your email", site = SiteBar(me, null), mainClasses = "gf-landing") {
    wrap {
        crumb("/account", "Your account")
        div("gf-split__copy") {
            p("gf-kicker") { +"Your email" }
            h1("gf-h1 gf-h1--landing") { +"Check your mail" }
            p("gf-lede gf-muted") { +"We sent a 6-digit code to "; strong { +newEmail }; +". It works for 15 minutes." }
            form(action = "/account/email/confirm", method = FormMethod.post, classes = if (error != null) "gf-signin gf-field--invalid" else "gf-signin") {
                id = "email-code"
                label("gf-label") { htmlFor = "code"; +"6-digit code" }
                div("gf-signin__row") {
                    input(InputType.text, name = "code", classes = "gf-input gf-code") {
                        id = "code"; required = true; maxLength = "6"; placeholder = "······"
                        attributes["inputmode"] = "numeric"; attributes["autocomplete"] = "one-time-code"
                        if (error != null) attributes["aria-invalid"] = "true"
                    }
                    button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.LARGE)) { +"Change my email" }
                }
                if (error != null) fieldError(error)
            }
        }
    }
}
