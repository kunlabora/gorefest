package gorefest.shell.web.bgg

import gorefest.core.gameday.SetBringRequestsAllowed
import gorefest.core.gameday.SetBringRequestsAllowedDecider
import gorefest.core.BggId
import gorefest.core.player.SetCanExplain
import gorefest.core.player.SetCanExplainDecider
import gorefest.shell.web.components.explainAnnouncement
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import gorefest.shell.App
import gorefest.shell.auth.Login
import gorefest.shell.bgg.BggPriority
import gorefest.shell.bgg.CollectionSyncState
import gorefest.shell.bgg.SyncStatus
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.authenticated
import gorefest.shell.web.redirect
import gorefest.shell.web.request.bringRequestsSwitch
import gorefest.shell.web.respondFragment
import gorefest.shell.web.safeReturnTo
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

context(login: Login)
fun Route.collectionRoutes(app: App) {
    get("/collection") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            if (!app.bggAccounts.isLinked(me.id)) return@authenticated redirect("/bgg?returnTo=%2Fcollection")
            val view = collectionView(app, me)
            call.respondHtml { collectionPage(view, me, app.bringRequests.accepts(me.id), onward = call.parameters["returnTo"]?.let(::safeReturnTo)?.takeIf { it != "/" && it != "/collection" }) }
        }
    }

    get("/collection/status") {
        authenticated {
            val view = collectionView(app, contextOf<CurrentPlayer>())
            respondFragment { collection(view) }
        }
    }

    /** The switch for being asked to bring games; on until turned off. */
    post("/collection/bring-requests") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val allowed = call.receiveParameters()["allowed"] != "false"
            app.commands.handle(SetBringRequestsAllowedDecider, SetBringRequestsAllowed(me.id, allowed))
            respondFragment { bringRequestsSwitch(app.bringRequests.accepts(me.id)) }
        }
    }

    /** "I can explain this" on a game in my collection (U22). */
    post("/collection/{game}/explain") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val canExplain = call.receiveParameters()["canExplain"] == "true"
            // Marking needs the game in my collection; taking a mark back works for any game I marked (from a shelf, or since sold).
            val id = call.parameters["game"]?.toIntOrNull()?.let(::BggId)
                ?.takeIf { it in app.collectionSync.collection(me.id) || (!canExplain && app.explainers.canExplain(me.id, it)) }
            val game = id?.let { app.catalog[it] } ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            app.commands.handle(SetCanExplainDecider, SetCanExplain(me.id, game.id, canExplain))
            respondFragment { collectionItem(game, app.explainers.canExplain(me.id, game.id), explainAnnouncement(game, canExplain)) }
        }
    }

    post("/collection/sync") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val username = app.collectionSync.status(me.id)?.username ?: return@authenticated redirect("/bgg?returnTo=%2Fcollection")
            app.collectionSync.request(me.id, username, BggPriority.LINK)
            respondFragment { collection(CollectionView(CollectionSyncState(username, SyncStatus.QUEUED, "Asking BGG for your collection", null), emptyList())) }
        }
    }
}

private suspend fun collectionView(app: App, me: CurrentPlayer): CollectionView {
    val owned = app.collectionSync.collection(me.id)
    val explains = app.explainers.gamesOf(me.id)
    fun games(ids: Set<BggId>) = ids.mapNotNull(app.catalog::get).sortedBy { it.name.lowercase() }
    return CollectionView(app.collectionSync.status(me.id), games(owned), explains, explainsElsewhere = games(explains - owned))
}
