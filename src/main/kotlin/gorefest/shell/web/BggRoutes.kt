package gorefest.shell.web

import gorefest.core.eventsourcing.Decision
import gorefest.core.player.LinkBggAccount
import gorefest.core.player.LinkBggAccountDecider
import gorefest.shell.App
import gorefest.shell.auth.Login
import gorefest.shell.bgg.BggPriority
import gorefest.shell.bgg.BggUnavailable
import gorefest.shell.bgg.FakeBggApi
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.html.ButtonType
import kotlinx.html.DIV
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
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.stream.createHTML

context(login: Login)
fun Route.bggRoutes(app: App) {
    get("/bgg") {
        authenticated {
            val returnTo = safeReturnTo(call.parameters["returnTo"])
            val linked = app.bggAccounts.state[contextOf<CurrentPlayer>().id]
            call.respondHtml {
                page("Link BGG", appBar = { backLink(returnTo, "Back"); appBarTitle("BoardGameGeek") }) {
                    if (linked != null) {
                        div("gf-stack") {
                            h1("gf-h1") { +"You're linked" }
                            p("gf-muted") { +"Your BGG account is "; strong { +linked }; +". Your collection comes from there." }
                            a(returnTo, classes = "gf-btn gf-btn--primary gf-btn--block") { +"Back" }
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
            }
        }
    }

    post("/bgg/username") {
        authenticated {
            val form = call.receiveParameters()
            val returnTo = safeReturnTo(form["returnTo"])
            val typed = form["username"].orEmpty().trim()
            if (typed.isEmpty()) return@authenticated respondFragment { usernameStep(returnTo, typed, "Type your BGG username.") }
            val user = try {
                app.bgg.call(BggPriority.LINK) { user(typed) }
            } catch (_: BggUnavailable) {
                return@authenticated respondFragment { usernameStep(returnTo, typed, "BGG isn't answering right now. Try again in a minute.") }
            } ?: return@authenticated respondFragment { usernameStep(returnTo, typed, "We can't find that BGG username.") }
            val code = app.bggVerification.code(contextOf<CurrentPlayer>().id, user.name)
            respondFragment { codeStep(returnTo, user.name, code, dev = app.config.dev && app.bggApi is FakeBggApi) }
        }
    }

    post("/bgg/verify") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val form = call.receiveParameters()
            val returnTo = safeReturnTo(form["returnTo"])
            val username = form["username"].orEmpty()
            val code = app.bggVerification.code(me.id, username)
            val dev = app.config.dev && app.bggApi is FakeBggApi
            val user = try {
                app.bgg.call(BggPriority.LINK) { user(username) }
            } catch (_: BggUnavailable) {
                return@authenticated respondFragment { codeStep(returnTo, username, code, dev, "BGG isn't answering right now. Try again in a minute.") }
            }
            if (user == null || !app.bggVerification.isOnProfile(user, code)) {
                return@authenticated respondFragment {
                    codeStep(returnTo, username, code, dev, "We can't see the code on your profile yet. Saved it? Then try again in a minute.")
                }
            }
            when (val decision = app.commands.handle(LinkBggAccountDecider, LinkBggAccount(me.id, user.name))) {
                is Decision.Accepted -> {
                    app.collectionSync.request(me.id, user.name, BggPriority.LINK)
                    redirect("/collection")
                }
                is Decision.Rejected -> respondFragment { codeStep(returnTo, username, code, dev, decision.reason) }
            }
        }
    }
}

/** Dev mode with the fake BGG: stands in for editing your profile on boardgamegeek.com. */
fun Route.devBggRoutes(fake: FakeBggApi) {
    post("/dev/bgg/profile") {
        val form = call.receiveParameters()
        fake.putInProfile(form["username"].orEmpty(), form["code"].orEmpty())
        call.respondText(createHTML().p("gf-small") { id = "dev-bgg"; +"Done: the fake BGG profile has the code. Now tap Verify." }, io.ktor.http.ContentType.Text.Html)
    }
}

private fun TagConsumer<*>.usernameStep(returnTo: String, username: String = "", error: String? = null) =
    bggForm("/bgg/username", returnTo) {
        div(if (error != null) "gf-field gf-field--invalid" else "gf-field") {
            label("gf-label") { htmlFor = "username"; +"BGG username" }
            input(InputType.text, name = "username", classes = "gf-input") {
                id = "username"
                value = username
                attributes["autocapitalize"] = "none"
                attributes["autocomplete"] = "username"
                if (error != null) attributes["aria-invalid"] = "true"
            }
            error(error)
        }
        submit("Next")
    }

private fun TagConsumer<*>.codeStep(returnTo: String, username: String, code: String, dev: Boolean, error: String? = null) =
    bggForm("/bgg/verify", returnTo) {
        input(InputType.hidden, name = "username") { value = username }
        p("gf-muted") { +"To prove "; strong { +username }; +" is you, put this code in your BGG profile:" }
        div("gf-copy") {
            span("gf-copy__value") { +code }
            button(type = ButtonType.button, classes = "gf-btn gf-btn--secondary gf-btn--sm") {
                attributes["data-code"] = code
                hyperscript("on click call navigator.clipboard.writeText(@data-code) then put 'Copied' into me")
                +"Copy"
            }
        }
        ol("gf-small") {
            li { +"On boardgamegeek.com, open "; strong { +"Account → Edit profile" }; +"." }
            li { +"Paste the code into "; strong { +"Web address" }; +" (or any other field shown on your profile) and save." }
            li { +"Come back and tap Verify. You can remove the code afterwards." }
        }
        if (dev) div("gf-notice") {
            id = "dev-bgg"
            icon("dice")
            p {
                +"Dev mode uses a fake BGG. "
                button(type = ButtonType.button, classes = "gf-link") {
                    hx("post", "/dev/bgg/profile")
                    hx("vals", """{"username": "$username", "code": "$code"}""")
                    hx("target", "#dev-bgg")
                    hx("swap", "outerHTML")
                    +"Put the code in the fake profile"
                }
            }
        }
        if (error != null) div("gf-field gf-field--invalid") { error(error) }
        submit("Verify")
    }

private fun TagConsumer<*>.bggForm(action: String, returnTo: String, content: kotlinx.html.FORM.() -> Unit) =
    form(action = action, method = FormMethod.post, classes = "gf-section") {
        id = "bgg-form"
        hx("post", action)
        hx("swap", "outerHTML")
        input(InputType.hidden, name = "returnTo") { value = returnTo }
        content()
    }

private fun DIV.error(message: String?) {
    if (message != null) p("gf-error") {
        role = "alert"
        icon("alert")
        +message
    }
}

private fun kotlinx.html.FORM.submit(label: String) = button(type = ButtonType.submit, classes = "gf-btn gf-btn--primary gf-btn--lg gf-btn--block") {
    span("gf-spin") { attributes["aria-hidden"] = "true" }
    +label
}
