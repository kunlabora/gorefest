package gorefest.shell.web

import gorefest.shell.App
import gorefest.shell.auth.Login
import gorefest.shell.bgg.BggPriority
import gorefest.shell.bgg.CollectionSyncState
import gorefest.shell.bgg.SyncStatus
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.html.TagConsumer
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.span
import kotlinx.html.ul

context(login: Login)
fun Route.collectionRoutes(app: App) {
    get("/collection") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            if (me.id !in app.bggAccounts.state) return@authenticated redirect("/bgg?returnTo=%2Fcollection")
            val view = collectionView(app, me)
            call.respondHtml {
                page("Your collection", appBar = { backLink("/", "Your game days"); appBarTitle("Your collection") }) {
                    h1("gf-h1") { +"Your collection" }
                    consumer.collection(view)
                }
            }
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

private data class CollectionView(val sync: CollectionSyncState?, val games: List<gorefest.core.bgg.Game>)

private suspend fun collectionView(app: App, me: CurrentPlayer) = CollectionView(
    sync = app.collectionSync.status(me.id),
    games = app.collectionSync.collection(me.id).mapNotNull(app.catalog::get).sortedBy { it.name.lowercase() },
)

/** Polls every 2 s while BGG is still working, then shows the games. */
private fun TagConsumer<*>.collection(view: CollectionView) = div("gf-stack") {
    id = "collection"
    val sync = view.sync
    when (sync?.status) {
        SyncStatus.QUEUED, null -> {
            hx("get", "/collection/status")
            hx("trigger", "every 2s")
            hx("swap", "outerHTML")
            div("gf-notice") {
                attributes["aria-live"] = "polite"
                span("gf-spin") { attributes["aria-hidden"] = "true"; attributes["style"] = "display: inline-block" }
                p { +(sync?.message ?: "Asking BGG for your collection") }
            }
            p("gf-small gf-muted") { +"BGG can take a few minutes for big collections. You can leave this page; it carries on." }
        }
        SyncStatus.FAILED -> {
            div("gf-notice gf-notice--error") { role = "alert"; icon("alert"); p { +(sync.message ?: "That didn't work.") } }
            syncButton("Try again")
        }
        SyncStatus.DONE -> {
            p("gf-muted") { +"${view.games.size} games from ${sync.username} on BGG." }
            if (view.games.isEmpty()) emptyState("No games in your BGG collection", "Mark games as owned on BGG, then sync again.")
            else ul("gf-list") { view.games.forEach { li { gameRow(it) } } }
            syncButton("Sync again")
        }
    }
}

private fun kotlinx.html.FlowContent.syncButton(label: String) = button(classes = "gf-btn gf-btn--secondary gf-btn--block") {
    hx("post", "/collection/sync")
    hx("target", "#collection")
    hx("swap", "outerHTML")
    icon("refresh")
    +label
}
