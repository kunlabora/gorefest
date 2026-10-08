package gorefest.shell.web.auth

import gorefest.shell.web.components.*
import gorefest.shell.web.urlEncoded
import kotlinx.html.*
import kotlin.uuid.Uuid

/** The login's hero for the follow-up steps: wordmark bar, kicker, big heading, then the page's own copy; an optional [aside] sits beside it on desktop. */
private fun HTML.authHero(
    title: String, kicker: String, heading: String, aside: (FlowContent.() -> Unit)? = null, copy: FlowContent.() -> Unit,
) = page(title, appBar = { wordmark() }, mainClasses = "gf-landing", wide = true) {
    div("gf-hero-band") {
        div("gf-hero-band__copy") {
            p("gf-kicker") { +kicker }
            h1("gf-h1 gf-h1--landing") { +heading }
            copy()
        }
        aside?.invoke(this)
    }
}

private const val TAGLINE = "Gorefest helps your group pick the board games you'll enjoy most on a game day."

/** What a chat app shows when someone pastes the bare site address (anonymous visitors land on this page): "Gorefest" alone says nothing. */
private fun HEAD.sitePreview() {
    meta(name = "description", content = TAGLINE)
    openGraph("og:site_name", "Gorefest")
    openGraph("og:title", "Gorefest: pick board games for your game day")
    openGraph("og:description", "$TAGLINE Everyone answers a few A-or-B questions, then it suggests what suits the whole table.")
    openGraph("og:type", "website")
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
    authHero("Check your mail", "Step 2 of 2", "Check your mail", aside = { mailCard() }) {
        p("gf-lede gf-muted") { +"We sent a code to "; strong("gf-ink") { +email }; +". Type it here, or tap the link in the mail." }
        if (dev) notice("mail") {
            +"Dev mode: mail isn't sent. "
            a("/dev/mailbox", classes = "gf-link") { attributes["target"] = "_blank"; +"Open the dev mailbox" }
        }
        consumer.codeForm(attempt)
        resendLinks(email, returnTo)
    }

/** What the mail looks like, so people know what to look for in their inbox. Decoration, desktop only. */
private fun FlowContent.mailCard() = div("gf-mailcard") {
    attributes["aria-hidden"] = "true"
    p("gf-kicker") { +"Inbox · just now" }
    h3("gf-mailcard__subject") { +"Your Gorefest code: 482913" }
    p("gf-mailcard__code") { +"482913" }
    p("gf-muted gf-small") { +"Type it in the app, or tap the button. It works once, for 15 minutes." }
    span("gf-mailcard__link") { +"Log me in" }
}

fun HTML.confirmLinkPage(email: String, token: String) = authHero("Log in", "Signing in", "Almost in") {
    p("gf-lede gf-muted") { +"Tap below to log in as "; strong("gf-ink") { +email }; +"." }
    form(action = "/login/link", method = FormMethod.post, classes = "gf-signin") {
        id = "link-form"
        hiddenInput("token", token)
        button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.LARGE)) { spinner(); +"Let me in" }
    }
}

fun HTML.deadLinkPage() = authHero("Link expired", "Sign in", "This link has expired") {
    p("gf-lede gf-muted") { +"Login links work once, for 15 minutes. Ask for a fresh one and you're in." }
    div("gf-signin") { linkButton("/login", "Send me a new code", ButtonStyle.PRIMARY, ButtonSize.LARGE) }
}

fun HTML.welcomePage(returnTo: String, name: String) = authHero("Welcome", "Welcome to Gorefest", "What should we call you?") {
    p("gf-lede gf-muted") { +"Your friends see this on game days." }
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

/** The big code field with the button directly under it; htmx re-renders the form in place when the server rejects the code. */
fun TagConsumer<*>.codeForm(attempt: Uuid, error: String? = null) = singleFieldForm("code-form", "/login/code", "Let me in", error) {
    hiddenInput("attempt", attempt.toString())
    label("gf-label") { htmlFor = "code"; +"6-digit code" }
    input(InputType.text, name = "code", classes = "gf-input gf-code") {
        id = "code"
        required = true
        maxLength = "6"
        placeholder = "······"
        attributes["inputmode"] = "numeric"
        attributes["autocomplete"] = "one-time-code"
        if (error != null) attributes["aria-invalid"] = "true"
    }
    if (error != null) fieldError(error)
    p("gf-hint") { +"Opened this from WhatsApp or Signal? The code is the quicker route." }
}

fun TagConsumer<*>.nameForm(returnTo: String, name: String, error: String? = null) = singleFieldForm("name-form", "/welcome", "That's me", error, inline = true) {
    hiddenInput("returnTo", returnTo)
    label("gf-label") { htmlFor = "name"; +"Name" }
    input(InputType.text, name = "name", classes = "gf-input") {
        id = "name"
        required = true
        value = name
        placeholder = "Kim"
        attributes["autocomplete"] = "given-name"
        if (error != null) attributes["aria-invalid"] = "true"
    }
    if (error != null) fieldError(error)
}

/**
 * A one-field form in the login's layout: label, field, error, then the button (beside the field when [inline], under it otherwise).
 * htmx re-renders it in place when the server rejects the input. [content] holds the hidden inputs, the label, the input and the hint.
 */
private fun TagConsumer<*>.singleFieldForm(
    formId: String, action: String, button: String, error: String?, inline: Boolean = false, content: FORM.() -> Unit,
) = form(action = action, method = FormMethod.post, classes = listOfNotNull("gf-signin", if (inline) "gf-signin--inline" else "gf-signin--stacked", "gf-field--invalid".takeIf { error != null }).joinToString(" ")) {
    id = formId
    hx("post", action)
    hx("swap", "outerHTML")
    content()
    button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.LARGE)) { spinner(); +button }
}
