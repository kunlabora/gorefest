package gorefest.shell.web.bgg

import gorefest.core.game.Game
import gorefest.shell.bgg.CollectionSyncState
import gorefest.shell.bgg.SyncStatus
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import gorefest.shell.web.request.bringRequestsSwitch
import kotlinx.html.*

data class CollectionView(val sync: CollectionSyncState?, val games: List<Game>)

/** [onward] is where the player was going before linking BGG (bringing games, say): it gets the page's one brand button. */
fun HTML.collectionPage(view: CollectionView, me: CurrentPlayer, bringRequestsAllowed: Boolean, onward: String? = null) = page("Your collection", site = SiteBar(me, Nav.COLLECTION)) {
    wrap("gf-collection") {
        p("gf-kicker") { +"BoardGameGeek" }
        pageHeading("Your collection")
        if (onward != null) div("gf-continue") {
            linkButton(onward, onwardLabel(onward), ButtonStyle.PRIMARY, ButtonSize.LARGE)
            p("gf-small gf-muted") { +"Your games are on their way from BGG; you can carry on while they load." }
        }
        consumer.bringRequestsSwitch(bringRequestsAllowed)
        consumer.collection(view)
    }
}

private fun onwardLabel(path: String) = if (path.endsWith("/bring")) "Back to bringing games" else "Carry on"

/** Polls every 2 s while BGG is still working, then shows the games. */
fun TagConsumer<*>.collection(view: CollectionView) = div("gf-stack") {
    id = "collection"
    val sync = view.sync
    announce(
        when (sync?.status) {
            SyncStatus.QUEUED, null -> sync?.message ?: "Asking BGG for your collection"
            SyncStatus.FAILED -> sync.message ?: "That didn't work."
            SyncStatus.DONE -> "${view.games.size.of("game")} from BGG"
        },
    )
    when (sync?.status) {
        SyncStatus.QUEUED, null -> {
            hx("get", "/collection/status")
            hx("trigger", "every 2s")
            hx("swap", "outerHTML")
            busyNotice(sync?.message ?: "Asking BGG for your collection")
            p("gf-small gf-muted") { +"BGG can take a few minutes for big collections. You can leave this page; it carries on." }
        }
        SyncStatus.FAILED -> {
            errorNotice(sync.message ?: "That didn't work.")
            syncButton("Try again")
        }
        SyncStatus.DONE -> {
            p("gf-muted") { +"${view.games.size} games from ${sync.username} on BGG." }
            if (view.games.isEmpty()) emptyState("No games in your BGG collection", "Mark games as owned on BGG, then sync again.")
            else ul("gf-list gf-collection__list") { view.games.forEach { li { gameRow(it, level = Level.H2) } } }
            syncButton("Sync again")
        }
    }
}

private fun FlowContent.syncButton(label: String) = actionButton(label, ButtonStyle.SECONDARY, block = true, icon = "refresh") {
    hx("post", "/collection/sync")
    hx("target", "#collection")
    hx("swap", "outerHTML")
}
