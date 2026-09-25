package gorefest.shell.web

import gorefest.shell.web.components.*
import kotlinx.html.*

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
        li { +"On boardgamegeek.com, open "; strong { +"Account → Edit profile" }; +"." }
        li { +"Paste the code into "; strong { +"Web address" }; +" (or any other field shown on your profile) and save." }
        li { +"Come back and tap Verify. You can remove the code afterwards." }
    }
    if (dev) notice("dice", id = "dev-bgg") {
        +"Dev mode uses a fake BGG. "
        button(type = ButtonType.button, classes = "gf-link") {
            hx("post", "/dev/bgg/profile")
            hx("vals", """{"username": "$username", "code": "$code"}""")
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
