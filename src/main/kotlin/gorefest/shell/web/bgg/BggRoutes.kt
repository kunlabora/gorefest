package gorefest.shell.web.bgg

import gorefest.core.eventsourcing.Decision
import gorefest.core.player.LinkBggAccount
import gorefest.core.player.LinkBggAccountDecider
import gorefest.core.player.UnlinkBggAccount
import gorefest.core.player.UnlinkBggAccountDecider
import gorefest.shell.App
import gorefest.shell.auth.Login
import gorefest.shell.bgg.BggPriority
import gorefest.shell.bgg.BggUnavailable
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.message
import gorefest.shell.web.authenticated
import gorefest.shell.web.redirect
import gorefest.shell.web.urlEncoded
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
            val me = contextOf<CurrentPlayer>().id
            val linkedAs = app.collectionSync.status(me)?.username ?: "your account".takeIf { app.bggAccounts.isLinked(me) }
            call.respondHtml { bggPage(safeReturnTo(call.parameters["returnTo"]), linkedAs, contextOf<CurrentPlayer>()) }
        }
    }

    post("/bgg/unlink") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            app.commands.handle(UnlinkBggAccountDecider, UnlinkBggAccount(me.id))
            app.collectionSync.forget(me.id)
            redirect("/bgg")
        }
    }

    post("/bgg/username") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val form = call.receiveParameters()
            val returnTo = safeReturnTo(form["returnTo"])
            val typed = form["username"].orEmpty().trim()
            if (typed.isEmpty()) return@authenticated respondFragment { linkForm(returnTo, typed, "Type your BGG username.") }
            val user = try {
                app.bgg.call(BggPriority.LINK) { user(typed) }
            } catch (_: BggUnavailable) {
                return@authenticated respondFragment { linkForm(returnTo, typed, "BGG isn't answering right now. Try again in a minute.") }
            } ?: return@authenticated respondFragment { linkForm(returnTo, typed, "We can't find that BGG username.") }
            when (val decision = app.commands.handle(LinkBggAccountDecider, LinkBggAccount(me.id, user.name, app.bggUsernameKey.of(user.name)))) {
                is Decision.Accepted -> {
                    app.collectionSync.request(me.id, user.name, BggPriority.LINK)
                    // Back to where the player was heading (bringing games, say): the collection page offers the way on.
                    redirect(if (returnTo == "/") "/collection" else "/collection?returnTo=${returnTo.urlEncoded()}")
                }
                is Decision.Rejected -> respondFragment { linkForm(returnTo, typed, decision.reason.message()) }
            }
        }
    }
}
