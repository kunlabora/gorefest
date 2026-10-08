package gorefest.shell.web.bgg

import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import gorefest.shell.web.urlEncoded
import kotlinx.html.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The collection nav item is current: linking BGG is how you get a collection. */
fun HTML.bggPage(returnTo: String, linked: String?, me: CurrentPlayer) = page("Link BGG", site = SiteBar(me, Nav.COLLECTION)) {
    wrap {
        crumb(returnTo, "Back")
        if (linked != null) {
            div("gf-bgg__copy gf-bgg__done") {
                h1("gf-h1 gf-h1--landing") { +"You're linked" }
                p("gf-lede gf-muted") { +"Your BGG account is "; strong { +linked }; +". Your collection comes from there." }
                linkButton(returnTo, "Back", size = ButtonSize.LARGE)
            }
        } else {
            consumer.usernameStep(returnTo)
        }
    }
}

/** Step 1, in the login's hero pattern: the username and its button in one row on a wide screen. */
fun TagConsumer<*>.usernameStep(returnTo: String, username: String = "", error: String? = null) =
    bggForm("/bgg/username", returnTo, "gf-bgg gf-bgg--username") {
        div("gf-bgg__copy") {
            p("gf-kicker") { +"Link BoardGameGeek · step 1 of 2" }
            h1("gf-h1 gf-h1--landing") { +"Link your BGG account" }
            p("gf-lede gf-muted") { +"Then you can bring games straight from your BoardGameGeek collection." }
            div(if (error != null) "gf-signin gf-field--invalid" else "gf-signin") {
                label("gf-label") { htmlFor = "username"; +"BGG username" }
                div("gf-signin__row") {
                    input(InputType.text, name = "username", classes = "gf-input") {
                        id = "username"
                        value = username
                        attributes["autocapitalize"] = "none"
                        attributes["autocomplete"] = "username"
                        if (error != null) attributes["aria-invalid"] = "true"
                    }
                    button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.LARGE)) { spinner(); +"Next" }
                }
                if (error != null) fieldError(error)
                p("gf-hint") { a(returnTo, classes = "gf-link") { +"Not now" } }
            }
        }
    }

/** Step 2: the code to paste into the BGG profile. In dev mode with the fake BGG, a button does the pasting. */
fun TagConsumer<*>.codeStep(returnTo: String, username: String, code: String, dev: Boolean, error: String? = null) =
    bggForm("/bgg/verify", returnTo, "gf-bgg gf-bgg--code") {
        hiddenInput("username", username)
        div("gf-bgg__copy") {
            p("gf-kicker") { +"Link BoardGameGeek · step 2 of 2" }
            h1("gf-h1 gf-h1--landing") { +"Prove it's your account" }
            p("gf-lede gf-muted") {
                +"BGG has no \"sign in with\". Put this code in your BGG profile, tell us when it's there, and your collection comes along."
            }
            copyBox(code)
            if (dev) notice("dice", id = "dev-bgg") {
                +"Dev mode uses a fake BGG. "
                button(type = ButtonType.button, classes = "gf-link") {
                    hx("post", "/dev/bgg/profile")
                    hx("vals", Json.encodeToString(mapOf("username" to username, "code" to code)))
                    hx("target", "#dev-bgg")
                    hx("swap", "outerHTML")
                    +"Put the code in the fake profile"
                }
            }
            if (error != null) div("gf-field gf-field--invalid") { fieldError(error) }
            button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.LARGE)) { spinner(); +"I've added it" }
            p("gf-hint") {
                +"Checking as "; strong { +username }; +" · "
                a("/bgg?returnTo=${returnTo.urlEncoded()}", classes = "gf-link") { +"Wrong username?" }
            }
        }
        stepper {
            step(1, "Open your BGG profile settings", text = {
                +"Log in to BoardGameGeek, then open your profile settings and find the "; strong { +"Contact Information" }; +" section."
            })
            step(2, "Paste the code into \"Website\"", text = {
                +"Paste it into the "; strong { +"Website/Homepage" }; +" field, like in the picture. BGG asks for your BGG password to save contact information; that's BGG checking it's you, we never see it."
            }) {
                div("gf-bgg__shot") {
                    img(src = "/static/bgg-profile-code.webp", alt = "BGG's Contact Information form, with the code pasted into the Website/Homepage field") {
                        classes = setOf("gf-howto")
                        width = "660"; height = "195"; attributes["loading"] = "lazy"
                    }
                }
            }
            step(3, "Come back and tap the button", text = { +"Afterwards you can clear the field again." })
        }
    }

fun TagConsumer<*>.devProfileFilled() = p("gf-small") { id = "dev-bgg"; +"Done: the fake BGG profile has the code. Now tap \"I've added it\"." }

private fun TagConsumer<*>.bggForm(action: String, returnTo: String, classes: String, content: FORM.() -> Unit) =
    form(action = action, method = FormMethod.post, classes = classes) {
        id = "bgg-form"
        hx("post", action)
        hx("swap", "outerHTML")
        hiddenInput("returnTo", returnTo)
        content()
    }
