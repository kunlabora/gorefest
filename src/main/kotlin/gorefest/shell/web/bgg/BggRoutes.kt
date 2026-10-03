package gorefest.shell.web.bgg

import gorefest.core.eventsourcing.Decision
import gorefest.core.player.LinkBggAccount
import gorefest.core.player.LinkBggAccountDecider
import gorefest.shell.App
import gorefest.shell.auth.Login
import gorefest.shell.bgg.BggPriority
import gorefest.shell.bgg.BggUnavailable
import gorefest.shell.bgg.FakeBggApi
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.authenticated
import gorefest.shell.web.redirect
import gorefest.shell.web.respondFragment
import gorefest.shell.web.safeReturnTo
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

context(login: Login)
fun Route.bggRoutes(app: App) {
    get("/bgg") {
        authenticated {
            call.respondHtml { bggPage(safeReturnTo(call.parameters["returnTo"]), app.bggAccounts.usernameOf(contextOf<CurrentPlayer>().id)) }
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
        respondFragment { devProfileFilled() }
    }
}
