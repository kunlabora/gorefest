package gorefest.shell.web.bgg

import gorefest.shell.App
import gorefest.shell.auth.Login
import gorefest.shell.bgg.BggPriority
import gorefest.shell.bgg.CollectionSyncState
import gorefest.shell.bgg.SyncStatus
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.authenticated
import gorefest.shell.web.redirect
import gorefest.shell.web.respondFragment
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

context(login: Login)
fun Route.collectionRoutes(app: App) {
    get("/collection") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            if (me.id !in app.bggAccounts.state) return@authenticated redirect("/bgg?returnTo=%2Fcollection")
            val view = collectionView(app, me)
            call.respondHtml { collectionPage(view) }
        }
    }

    get("/collection/status") {
        authenticated {
            val view = collectionView(app, contextOf<CurrentPlayer>())
            respondFragment { collection(view) }
        }
    }

    post("/collection/sync") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val username = app.bggAccounts.state[me.id] ?: return@authenticated redirect("/bgg?returnTo=%2Fcollection")
            app.collectionSync.request(me.id, username, BggPriority.LINK)
            respondFragment { collection(CollectionView(CollectionSyncState(username, SyncStatus.QUEUED, "Asking BGG for your collection", null), emptyList())) }
        }
    }
}

private suspend fun collectionView(app: App, me: CurrentPlayer) = CollectionView(
    sync = app.collectionSync.status(me.id),
    games = app.collectionSync.collection(me.id).mapNotNull(app.catalog::get).sortedBy { it.name.lowercase() },
)
