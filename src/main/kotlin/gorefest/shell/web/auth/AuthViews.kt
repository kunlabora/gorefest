package gorefest.shell.web.auth

import gorefest.shell.web.components.*
import gorefest.shell.web.urlEncoded
import kotlinx.html.*
import kotlin.uuid.Uuid

private fun HTML.authPage(title: String, dock: DIV.() -> Unit, content: MAIN.() -> Unit) =
    page(title, appBar = { appBarTitle("Gorefest") }, dock = dock) { content() }

private fun FlowContent.intro(heading: String, text: P.() -> Unit) = div("gf-stack") {
    h1("gf-h1") { +heading }
    p("gf-muted") { text() }
}

private val steps = listOf(
    "Say what you'd rather play" to "A quick round of A-or-B picks teaches it your taste.",
    "Join a game day" to "Your host shares an invite link, and everyone brings their games.",
    "Get three suggestions" to "Tick who's at the table and it picks what suits them all.",
)

/** What the app is for, shown to people who arrive without an invite. */
private fun FlowContent.howItWorks() = section("gf-section") {
    p("gf-lede") { +"Gorefest helps your group pick the board games you'll enjoy most on a game day." }
    ol("gf-steps") {
        steps.forEach { (title, text) ->
            li { strong { +title }; br; span("gf-muted") { +text } }
        }
    }
}

fun HTML.emailPage(returnTo: String) = authPage("Log in", dock = { primaryButton("Send me a code", form = "email-form") }) {
    if (returnTo == "/") howItWorks()
    intro("What's your email?") { +"We'll send a link and a 6-digit code. No password to remember." }
    consumer.emailForm(returnTo)
}

fun HTML.codePage(attempt: Uuid, email: String, returnTo: String, dev: Boolean) =
    authPage("Check your mail", dock = { primaryButton("Let me in", form = "code-form") }) {
        intro("Check your mail") { +"We sent a code to "; strong { +email }; +". Type it here, or tap the link in the mail." }
        if (dev) notice("mail") {
            +"Dev mode: mail isn't sent. "
            a("/dev/mailbox", classes = "gf-link") { attributes["target"] = "_blank"; +"Open the dev mailbox" }
        }
        consumer.codeForm(attempt)
        resendLinks(email, returnTo)
    }

fun HTML.confirmLinkPage(email: String, token: String) = authPage("Log in", dock = { primaryButton("Let me in", form = "link-form") }) {
    intro("Almost in") { +"Tap below to log in as "; strong { +email }; +"." }
    form(action = "/login/link", method = FormMethod.post) {
        id = "link-form"
        hiddenInput("token", token)
    }
}

fun HTML.deadLinkPage() = authPage("Link expired", dock = { primaryLink("/login", "Send me a new code") }) {
    intro("This link has expired") { +"Login links work once, for 15 minutes. Ask for a fresh one and you're in." }
}

fun HTML.welcomePage(returnTo: String, name: String) = authPage("Welcome", dock = { primaryButton("That's me", form = "name-form") }) {
    intro("What should we call you?") { +"Your friends see this on game days." }
    consumer.nameForm(returnTo, name)
}

/** "Send it again" posts the same email; "Change email" starts over. Both keep where the player was going. */
private fun FlowContent.resendLinks(email: String, returnTo: String) = form(action = "/login", method = FormMethod.post, classes = "gf-small") {
    hx("post", "/login")
    hiddenInput("email", email)
    hiddenInput("returnTo", returnTo)
    +"No mail after a minute? "
    button(type = ButtonType.submit, classes = "gf-link") { +"Send it again" }
    +" · "
    a("/login?returnTo=${returnTo.urlEncoded()}", classes = "gf-link") { +"Change email" }
}

fun TagConsumer<*>.emailForm(returnTo: String, email: String = "", error: String? = null) = singleFieldForm("email-form", "/login") {
    hiddenInput("returnTo", returnTo)
    field("email", "Email", error = error) {
        input(InputType.email, name = "email", classes = "gf-input") {
            id = "email"
            required = true
            value = email
            attributes["inputmode"] = "email"
            attributes["autocomplete"] = "email"
            if (error != null) attributes["aria-invalid"] = "true"
        }
    }
}

fun TagConsumer<*>.codeForm(attempt: Uuid, error: String? = null) = singleFieldForm("code-form", "/login/code") {
    hiddenInput("attempt", attempt.toString())
    field("code", "6-digit code", hint = "Opened this from WhatsApp or Signal? The code is the quicker route.", error = error) {
        input(InputType.text, name = "code", classes = "gf-input gf-code") {
            id = "code"
            required = true
            maxLength = "6"
            placeholder = "······"
            attributes["inputmode"] = "numeric"
            attributes["autocomplete"] = "one-time-code"
            if (error != null) attributes["aria-invalid"] = "true"
        }
    }
}

fun TagConsumer<*>.nameForm(returnTo: String, name: String, error: String? = null) = singleFieldForm("name-form", "/welcome") {
    hiddenInput("returnTo", returnTo)
    field("name", "Name", error = error) {
        input(InputType.text, name = "name", classes = "gf-input") {
            id = "name"
            required = true
            value = name
            placeholder = "Kim"
            attributes["autocomplete"] = "given-name"
            if (error != null) attributes["aria-invalid"] = "true"
        }
    }
}

/** A form that htmx re-renders in place when the server rejects the input. Its submit button sits in the dock. */
private fun TagConsumer<*>.singleFieldForm(formId: String, action: String, content: FORM.() -> Unit) =
    form(action = action, method = FormMethod.post, classes = "gf-section") {
        id = formId
        hx("post", action)
        hx("swap", "outerHTML")
        content()
    }
