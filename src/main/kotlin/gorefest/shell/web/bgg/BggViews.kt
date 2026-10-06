package gorefest.shell.web.bgg

import gorefest.shell.web.components.*
import kotlinx.html.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun HTML.bggPage(returnTo: String, linked: String?) = page("Link BGG", appBar = { backLink(returnTo, "Back"); appBarTitle("BoardGameGeek") }) {
    if (linked != null) {
        div("gf-stack") {
            h1("gf-h1") { +"You're linked" }
            p("gf-muted") { +"Your BGG account is "; strong { +linked }; +". Your collection comes from there." }
            linkButton(returnTo, "Back", block = true)
        }
    } else {
        div("gf-stack") {
            h1("gf-h1") { +"Link your BGG account" }
            p("gf-muted") { +"Then you can bring games straight from your BoardGameGeek collection." }
        }
        consumer.usernameStep(returnTo)
        p("gf-small") { a(returnTo, classes = "gf-link") { +"Not now" } }
    }
}

fun TagConsumer<*>.usernameStep(returnTo: String, username: String = "", error: String? = null) = bggForm("/bgg/username", returnTo) {
    field("username", "BGG username", error = error) {
        input(InputType.text, name = "username", classes = "gf-input") {
            id = "username"
            value = username
            attributes["autocapitalize"] = "none"
            attributes["autocomplete"] = "username"
            if (error != null) attributes["aria-invalid"] = "true"
        }
    }
    primaryButton("Next")
}

/** Step 2: the code to paste into the BGG profile. In dev mode with the fake BGG, a button does the pasting. */
fun TagConsumer<*>.codeStep(returnTo: String, username: String, code: String, dev: Boolean, error: String? = null) = bggForm("/bgg/verify", returnTo) {
    hiddenInput("username", username)
    p("gf-muted") { +"To prove "; strong { +username }; +" is you, put this code in your BGG profile:" }
    copyBox(code)
    ol("gf-small") {
        li { +"On boardgamegeek.com, open your profile settings and find the "; strong { +"Contact Information" }; +" section." }
        li { +"Paste the code into the "; strong { +"Website/Homepage" }; +" field, like in the picture." }
        li { +"Submit. BGG asks for your BGG password to save contact information; that's BGG checking it's you, we never see it." }
        li { +"Come back and tap Verify. Afterwards you can clear the field again." }
    }
    img(src = "/static/bgg-profile-code.webp", alt = "BGG's Contact Information form, with the code pasted into the Website/Homepage field") {
        classes = setOf("gf-howto")
        width = "660"; height = "195"; attributes["loading"] = "lazy"
    }
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
    primaryButton("Verify")
}

fun TagConsumer<*>.devProfileFilled() = p("gf-small") { id = "dev-bgg"; +"Done: the fake BGG profile has the code. Now tap Verify." }

private fun TagConsumer<*>.bggForm(action: String, returnTo: String, content: FORM.() -> Unit) =
    form(action = action, method = FormMethod.post, classes = "gf-section") {
        id = "bgg-form"
        hx("post", action)
        hx("swap", "outerHTML")
        hiddenInput("returnTo", returnTo)
        content()
    }
