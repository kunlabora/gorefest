package gorefest.shell.web.auth

import gorefest.shell.web.components.*
import gorefest.shell.web.urlEncoded
import kotlinx.html.*
import kotlin.uuid.Uuid

private fun HTML.authPage(title: String, dock: DIV.() -> Unit, head: HEAD.() -> Unit = {}, content: MAIN.() -> Unit) =
    page(title, appBar = { wordmark() }, dock = dock, head = head) { content() }

private const val TAGLINE = "Gorefest helps your group pick the board games you'll enjoy most on a game day."

/** What a chat app shows when someone pastes the bare site address (anonymous visitors land on this page): "Gorefest" alone says nothing. */
private fun HEAD.sitePreview() {
    meta(name = "description", content = TAGLINE)
    openGraph("og:site_name", "Gorefest")
    openGraph("og:title", "Gorefest: pick board games for your game day")
    openGraph("og:description", "$TAGLINE Everyone answers a few A-or-B questions, then it suggests what suits the whole table.")
    openGraph("og:type", "website")
}

private fun FlowContent.intro(heading: String, text: P.() -> Unit) = div("gf-stack") {
    h1("gf-h1") { +heading }
    p("gf-muted") { text() }
}

private val steps = listOf(
    Triple("Pick between two games", "A quick round of this-or-that teaches Gorefest your taste.", Illustration.DUEL),
    Triple("Join a game day", "Your host sends an invite link. Everyone adds the games they're bringing.", Illustration.INVITE),
    Triple("Get three suggestions", "Tick who's at the table and Gorefest picks three that suit everyone.", Illustration.SHORTLIST),
)

private enum class Illustration { DUEL, INVITE, SHORTLIST }

/** A game's stand-in tile: two letters on one of the three box-art hues. Decoration only, so no real game is implied. */
private fun FlowContent.tile(mono: String, hue: Int, extra: String = "") =
    span("gf-minitile gf-minitile--$hue $extra".trim()) { +mono }

/** What a suggestion looks like, as three cards on the hero. Decoration for people who arrive without an invite. */
private fun FlowContent.sampleSuggestions() = div("gf-samples") {
    attributes["aria-hidden"] = "true"
    listOf(Triple("Cascadia", "Suits all 5 players", 0), Triple("Azul", "4 of 5 love it", 1), Triple("Splendor", "Quick, 30 min", 2)).forEach { (name, reason, hue) ->
        div("gf-sample") {
            tile(name.take(2), hue, "gf-minitile--big")
            div {
                h3("gf-sample__name") { +name }
                span("gf-sample__chip") { +reason }
            }
        }
    }
}

private fun FlowContent.illustration(which: Illustration) = div("gf-ill") {
    attributes["aria-hidden"] = "true"
    when (which) {
        Illustration.DUEL -> div("gf-ill__duel") { tile("Wi", 0, "gf-minitile--on"); span("gf-ill__or") { +"OR" }; tile("Co", 2) }
        Illustration.INVITE -> div("gf-ill__invite") {
            span("gf-ill__link") { +"…/join/k7f2" }
            div("gf-ill__faces") { listOf("AN" to 0, "JO" to 1, "LT" to 2, "MK" to 0).forEach { (initials, hue) -> span("gf-ill__face gf-minitile--$hue") { +initials } } }
        }
        Illustration.SHORTLIST -> div("gf-ill__three") {
            listOf("Cascadia", "Azul", "Splendor").forEachIndexed { i, name -> div { tile("${i + 1}", i, "gf-minitile--tiny"); +name } }
        }
    }
}

/** What the app is for, shown to people who arrive without an invite. */
private fun FlowContent.howItWorks() = div("gf-how-band") {
    section("gf-how") {
        h2("gf-h2") { +"How it works" }
        ol("gf-how__cols") {
            steps.forEachIndexed { i, (title, text, ill) ->
                li("gf-how__col") {
                    illustration(ill)
                    p("gf-kicker") { +"Step ${i + 1}" }
                    h3("gf-how__title") { +title }
                    p("gf-muted gf-how__text") { +text }
                }
            }
        }
    }
}

/** The sign-in landing: brand and form first, then what the app does. Invitees skip the pitch and the sample cards. */
fun HTML.emailPage(returnTo: String) {
    val landing = returnTo == "/"
    page(
        "Sign in · Gorefest",
        appBar = { wordmark() },
        head = { if (landing) sitePreview() },
        mainClasses = "gf-landing",
        wide = true,
    ) {
        div("gf-hero-band") {
            div("gf-hero-band__copy") {
                p("gf-kicker") { +"For board game groups" }
                h1("gf-h1 gf-h1--landing") { +"Find the game your whole table wants to play." }
                p("gf-lede gf-muted") { +"Gorefest learns what each player likes, then suggests three games for your next game day." }
                consumer.emailForm(returnTo)
            }
            if (landing) sampleSuggestions()
        }
        if (landing) howItWorks()
    }
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
    id = "resend"
    hx("post", "/login")
    hx("target", "this")
    hx("swap", "outerHTML")
    hiddenInput("resend", "1")
    hiddenInput("email", email)
    hiddenInput("returnTo", returnTo)
    +"No mail after a minute? "
    button(type = ButtonType.submit, classes = "gf-link") { +"Send it again" }
    +" · "
    a("/login?returnTo=${returnTo.urlEncoded()}", classes = "gf-link") { +"Change email" }
}

/** Email and button share one row on a wide screen; htmx re-renders the form in place when the server rejects the input. */
fun TagConsumer<*>.emailForm(returnTo: String, email: String = "", error: String? = null) =
    form(action = "/login", method = FormMethod.post, classes = if (error != null) "gf-signin gf-field--invalid" else "gf-signin") {
        id = "email-form"
        hx("post", "/login")
        hx("swap", "outerHTML")
        hiddenInput("returnTo", returnTo)
        label("gf-label") { htmlFor = "email"; +"Email address" }
        div("gf-signin__row") {
            input(InputType.email, name = "email", classes = "gf-input") {
                id = "email"
                required = true
                value = email
                placeholder = "you@example.com"
                attributes["inputmode"] = "email"
                attributes["autocomplete"] = "email"
                if (error != null) attributes["aria-invalid"] = "true"
            }
            button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.LARGE)) { spinner(); +"Send me a code" }
        }
        if (error != null) fieldError(error)
        p("gf-hint") { +"We'll email a sign-in link and a 6-digit code. No password." }
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
