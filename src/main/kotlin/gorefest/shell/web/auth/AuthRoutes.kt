package gorefest.shell.web.auth

import gorefest.core.auth.LoginCheck
import gorefest.shell.AppConfig
import gorefest.shell.auth.Login
import gorefest.shell.auth.LoginResult
import gorefest.shell.web.SESSION_COOKIE
import gorefest.shell.web.components.inWords
import gorefest.shell.web.components.long
import gorefest.shell.web.components.of
import gorefest.shell.web.redirect
import gorefest.shell.web.respondFragment
import gorefest.shell.web.safeReturnTo
import gorefest.shell.web.sessionPlayer
import gorefest.shell.web.urlEncoded
import io.ktor.http.Cookie
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveParameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.uuid.Uuid

private val EMAIL = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
private const val MAX_NAME_LENGTH = 40

context(login: Login, config: AppConfig)
fun Route.authRoutes() {
    get("/login") {
        val returnTo = safeReturnTo(call.parameters["returnTo"])
        if (sessionPlayer() != null) return@get redirect(returnTo)
        call.respondHtml { emailPage(returnTo) }
    }

    post("/login") {
        val form = call.receiveParameters()
        val returnTo = safeReturnTo(form["returnTo"])
        val email = form["email"].orEmpty().trim().lowercase()
        if (!EMAIL.matches(email)) {
            return@post respondFragment { emailForm(returnTo, form["email"].orEmpty(), "That doesn't look like an email address.") }
        }
        when (val requested = login.request(email, returnTo, address = call.request.origin.remoteAddress)) {
            is Login.Requested.Sent -> redirect("/login/code?attempt=${requested.attempt}")
            Login.Requested.TooMany ->
                respondFragment { emailForm(returnTo, form["email"].orEmpty(), "That's a lot of codes. Wait a bit, then try again.") }
        }
    }

    get("/login/code") {
        val attempt = call.parameters["attempt"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        val (email, returnTo) = attempt?.let { login.pending(it) } ?: return@get redirect("/login")
        call.respondHtml { codePage(attempt, email, returnTo, dev = config.dev) }
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
        call.respondHtml { confirmLinkPage(email, token) }
    }

    post("/login/link") {
        when (val result = login.withLink(call.receiveParameters()["token"].orEmpty())) {
            is LoginResult.LoggedIn -> startSession(result)
            else -> call.respondHtml { deadLinkPage() }
        }
    }

    get("/welcome") {
        val player = sessionPlayer() ?: return@get redirect("/login")
        call.respondHtml { welcomePage(safeReturnTo(call.parameters["returnTo"]), player.name.orEmpty()) }
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
