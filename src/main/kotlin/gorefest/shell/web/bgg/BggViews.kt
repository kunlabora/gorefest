package gorefest.shell.web.bgg

import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import kotlinx.html.*

/** The collection nav item is current: linking BGG is how you get a collection. */
fun HTML.bggPage(returnTo: String, linked: String?, me: CurrentPlayer) = page("Link BGG", site = SiteBar(me, Nav.COLLECTION)) {
    wrap {
        crumb(returnTo, "Back")
        if (linked != null) {
            div("gf-bgg__copy gf-bgg__done") {
                pageHeading("You're linked")
                p("gf-lede gf-muted") { +"Your BGG account is "; strong { +linked }; +". Your collection comes from there." }
                linkButton(returnTo, "Back", size = ButtonSize.LARGE)
                form(action = "/bgg/unlink", method = FormMethod.post, classes = "gf-bgg__unlink") {
                    confirmOnSubmit("Unlink your BGG account? Your collection here is deleted.")
                    p("gf-hint") { +"Linked the wrong one? "; textButton("Unlink my BGG account", submit = true) }
                }
            }
        } else {
            consumer.linkForm(returnTo)
        }
    }
}

/** In the login's hero pattern: the username and its button in one row on a wide screen. */
fun TagConsumer<*>.linkForm(returnTo: String, username: String = "", error: String? = null) =
    form(action = "/bgg/username", method = FormMethod.post, classes = "gf-bgg") {
        id = "bgg-form"
        announce("Link your BGG account")
        hx("post", "/bgg/username")
        hx("swap", "outerHTML")
        hiddenInput("returnTo", returnTo)
        div("gf-bgg__copy") {
            p("gf-kicker") { +"Link BoardGameGeek" }
            pageHeading("Link your BGG account")
            p("gf-lede gf-muted") { +"Type your BGG username and your collection comes along, so you can bring games straight from it." }
            div(if (error != null) "gf-signin gf-field--invalid" else "gf-signin") {
                signinField(
                    "username", "BGG username", error = error,
                    hint = "We don't check it's yours, so link your own. Sharing a collection at home? You can both link the same account.",
                    action = { actionButton("Link", ButtonStyle.PRIMARY, ButtonSize.LARGE, submit = true, spinner = true) },
                ) {
                    fieldInput("username") {
                        value = username
                        attributes["autocapitalize"] = "none"
                        attributes["autocomplete"] = "username"
                    }
                }
                p("gf-hint") { a(returnTo, classes = "gf-link") { +"Not now" } }
            }
        }
    }
