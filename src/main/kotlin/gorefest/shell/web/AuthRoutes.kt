package gorefest.shell.web

import gorefest.core.auth.LoginCheck
import gorefest.shell.AppConfig
import gorefest.shell.auth.Login
import gorefest.shell.auth.LoginResult
import io.ktor.http.Cookie
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.span
import kotlinx.html.strong
import kotlin.uuid.Uuid

private val EMAIL = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
private const val MAX_NAME_LENGTH = 40

context(login: Login, config: AppConfig)
fun Route.authRoutes() {
    get("/login") {
        val returnTo = safeReturnTo(call.parameters["returnTo"])
        if (sessionPlayer() != null) return@get redirect(returnTo)
        call.respondHtml {
            authPage("Log in", dock = { submit("email-form", "Send me a code") }) {
                div("gf-stack") {
                    h1("gf-h1") { +"What's your email?" }
                    p("gf-muted") { +"We'll send a link and a 6-digit code. No password to remember." }
                }
                consumer.emailForm(returnTo)
            }
        }
    }

    post("/login") {
        val form = call.receiveParameters()
        val returnTo = safeReturnTo(form["returnTo"])
        val email = form["email"].orEmpty().trim().lowercase()
        if (!EMAIL.matches(email)) {
            return@post respondFragment { emailForm(returnTo, form["email"].orEmpty(), "That doesn't look like an email address.") }
        }
        val attempt = login.request(email, returnTo)
        redirect("/login/code?attempt=$attempt")
    }

    get("/login/code") {
        val attempt = call.parameters["attempt"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        val (email, returnTo) = attempt?.let { login.pending(it) } ?: return@get redirect("/login")
        call.respondHtml {
            authPage("Check your mail", dock = { submit("code-form", "Let me in") }) {
                div("gf-stack") {
                    h1("gf-h1") { +"Check your mail" }
                    p("gf-muted") {
                        +"We sent a code to "
                        strong { +email }
                        +". Type it here, or tap the link in the mail."
                    }
                }
                if (config.dev) devMailboxNotice()
                consumer.codeForm(attempt)
                resendLinks(email, returnTo)
            }
        }
    }

    post("/login/code") {
        val form = call.receiveParameters()
        val attempt = form["attempt"]?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: return@post redirect("/login")
        when (val result = login.withCode(attempt, form["code"].orEmpty())) {
            is LoginResult.LoggedIn -> startSession(result)
            is LoginResult.Failed -> respondFragment { codeForm(attempt, result.check.message()) }
            LoginResult.UnknownAttempt -> redirect("/login")
        }
    }

    get("/login/link") {
        val token = call.parameters["token"].orEmpty()
        val email = login.emailForLink(token) ?: return@get call.respondHtml { deadLinkPage() }
        call.respondHtml {
            authPage("Log in", dock = { submit("link-form", "Let me in") }) {
                div("gf-stack") {
                    h1("gf-h1") { +"Almost in" }
                    p("gf-muted") {
                        +"Tap below to log in as "
                        strong { +email }
                        +"."
                    }
                }
                form(action = "/login/link", method = FormMethod.post) {
                    id = "link-form"
                    input(InputType.hidden, name = "token") { value = token }
                }
            }
        }
    }

    post("/login/link") {
        when (val result = login.withLink(call.receiveParameters()["token"].orEmpty())) {
            is LoginResult.LoggedIn -> startSession(result)
            else -> call.respondHtml { deadLinkPage() }
        }
    }

    get("/welcome") {
        val player = sessionPlayer() ?: return@get redirect("/login")
        val returnTo = safeReturnTo(call.parameters["returnTo"])
        call.respondHtml {
            authPage("Welcome", dock = { submit("name-form", "That's me") }) {
                div("gf-stack") {
                    h1("gf-h1") { +"What should we call you?" }
                    p("gf-muted") { +"Your friends see this on game days." }
                }
                consumer.nameForm(returnTo, player.name.orEmpty())
            }
        }
    }

    post("/welcome") {
        val player = sessionPlayer() ?: return@post redirect("/login")
        val form = call.receiveParameters()
        val returnTo = safeReturnTo(form["returnTo"])
        val name = form["name"].orEmpty().trim()
        val error = when {
            name.isEmpty() -> "Pick a name your friends will recognise."
            name.length > MAX_NAME_LENGTH -> "That's a bit long. Keep it under $MAX_NAME_LENGTH characters."
            else -> null
        }
        if (error != null) return@post respondFragment { nameForm(returnTo, name, error) }
        login.rename(player.id, name)
        redirect(returnTo)
    }

    post("/logout") {
        call.request.cookies[SESSION_COOKIE]?.let { login.logout(it) }
        call.response.cookies.append(sessionCookie(value = "", maxAgeSeconds = 0))
        redirect("/login")
    }
}

context(config: AppConfig)
private suspend fun RoutingContext.startSession(result: LoginResult.LoggedIn) {
    call.response.cookies.append(sessionCookie(result.sessionToken, Login.SESSION_TTL.seconds.toInt()))
    redirect(if (result.needsName) "/welcome?returnTo=${result.returnTo.urlEncoded()}" else result.returnTo)
}

context(config: AppConfig)
private fun sessionCookie(value: String, maxAgeSeconds: Int) = Cookie(
    name = SESSION_COOKIE,
    value = value,
    maxAge = maxAgeSeconds,
    path = "/",
    httpOnly = true,
    secure = config.secureCookies,
    extensions = mapOf("SameSite" to "Lax"),
)

private fun LoginCheck.message() = when (this) {
    is LoginCheck.WrongCode -> "That code doesn't match. ${triesLeft.inWords()} more ${if (triesLeft == 1) "try" else "tries"}, then you'll need a fresh code."
    LoginCheck.TooManyTries -> "Too many wrong codes. Send it again for a fresh one."
    LoginCheck.Expired -> "That code has expired. Codes work for 15 minutes, so send it again for a fresh one."
    LoginCheck.AlreadyUsed -> "That code was already used. Send it again for a fresh one."
    LoginCheck.Ok -> error("Not a failure")
}

private fun Int.inWords() = listOf("No", "One", "Two", "Three", "Four").getOrElse(this) { toString() }

private fun kotlinx.html.HTML.authPage(title: String, dock: DIV.() -> Unit, content: kotlinx.html.MAIN.() -> Unit) =
    page(title, appBar = { appBarTitle("Gorefest") }, dock = dock) { content() }

private fun kotlinx.html.HTML.deadLinkPage() =
    authPage("Link expired", dock = { a("/login", classes = "gf-btn gf-btn--primary gf-btn--lg gf-btn--block") { +"Send me a new code" } }) {
        div("gf-stack") {
            h1("gf-h1") { +"This link has expired" }
            p("gf-muted") { +"Login links work once, for 15 minutes. Ask for a fresh one and you're in." }
        }
    }

/** The dock's primary button, submitting a form elsewhere on the page. */
private fun DIV.submit(formId: String, label: String) =
    button(type = ButtonType.submit, classes = "gf-btn gf-btn--primary gf-btn--lg gf-btn--block") {
        attributes["form"] = formId
        span("gf-spin") { attributes["aria-hidden"] = "true" }
        +label
    }

/** "Send it again" posts the same email; "Change email" starts over. Both keep where the player was going. */
private fun FlowContent.resendLinks(email: String, returnTo: String) = form(action = "/login", method = FormMethod.post, classes = "gf-small") {
    hx("post", "/login")
    input(InputType.hidden, name = "email") { value = email }
    input(InputType.hidden, name = "returnTo") { value = returnTo }
    +"No mail after a minute? "
    button(type = ButtonType.submit, classes = "gf-link") { +"Send it again" }
    +" · "
    a("/login?returnTo=${returnTo.urlEncoded()}", classes = "gf-link") { +"Change email" }
}

private fun FlowContent.devMailboxNotice() = div("gf-notice") {
    icon("mail")
    p {
        +"Dev mode: mail isn't sent. "
        a("/dev/mailbox", classes = "gf-link") { attributes["target"] = "_blank"; +"Open the dev mailbox" }
    }
}

private fun TagConsumer<*>.emailForm(returnTo: String, email: String = "", error: String? = null) =
    singleFieldForm("email-form", "/login", error) {
        input(InputType.hidden, name = "returnTo") { value = returnTo }
        label("gf-label") { htmlFor = "email"; +"Email" }
        input(InputType.email, name = "email", classes = "gf-input") {
            id = "email"
            required = true
            value = email
            attributes["inputmode"] = "email"
            attributes["autocomplete"] = "email"
            if (error != null) attributes["aria-invalid"] = "true"
        }
    }

private fun TagConsumer<*>.codeForm(attempt: Uuid, error: String? = null) =
    singleFieldForm("code-form", "/login/code", error, hint = "Opened this from WhatsApp or Signal? The code is the quicker route.") {
        input(InputType.hidden, name = "attempt") { value = attempt.toString() }
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
    }

private fun TagConsumer<*>.nameForm(returnTo: String, name: String, error: String? = null) =
    singleFieldForm("name-form", "/welcome", error) {
        input(InputType.hidden, name = "returnTo") { value = returnTo }
        label("gf-label") { htmlFor = "name"; +"Name" }
        input(InputType.text, name = "name", classes = "gf-input") {
            id = "name"
            required = true
            value = name
            placeholder = "Kim"
            attributes["autocomplete"] = "given-name"
            if (error != null) attributes["aria-invalid"] = "true"
        }
    }

/** A form with one field that htmx re-renders in place when the server rejects the input. */
private fun TagConsumer<*>.singleFieldForm(
    formId: String,
    action: String,
    error: String?,
    hint: String? = null,
    field: DIV.() -> Unit,
) = form(action = action, method = FormMethod.post, classes = "gf-section") {
    id = formId
    hx("post", action)
    hx("swap", "outerHTML")
    div(if (error != null) "gf-field gf-field--invalid" else "gf-field") {
        field()
        if (hint != null) p("gf-hint") { +hint }
        if (error != null) p("gf-error") {
            role = "alert"
            icon("alert")
            +error
        }
    }
}

