package gorefest.shell.web.shelf

import gorefest.core.PlayerId
import gorefest.core.game.Game
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.MAX_WISHES
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import gorefest.shell.web.request.OwnerRequest
import gorefest.shell.web.request.bringRequests
import gorefest.shell.web.urlEncoded
import kotlinx.html.*

/** A game as it appears on the bring page: am I bringing it. */
data class BringRow(val game: Game, val bringing: Boolean, val owned: Boolean = true)

/** A game on the shelf as it appears under "Really want to play something?": do I want it, and how many people do. */
data class WishRow(val game: Game, val wants: Boolean, val wishers: Int)

/** Who brings how many games to the shelf. */
data class ShelfOwner(val id: PlayerId, val name: String, val count: Int)

/** The shelf as the sidebar and tray show it: the table's total, who brought what, and what I'm adding. */
data class ShelfSummary(val total: Int, val byPlayer: List<ShelfOwner>, val mine: List<Game>)

fun HTML.bringPage(
    me: CurrentPlayer, gameDay: GameDaySummary, rows: List<BringRow>, wishes: List<WishRow>, linked: Boolean, summary: ShelfSummary,
    requests: List<OwnerRequest> = emptyList(),
) = page(
    "Bring games",
    site = SiteBar(me, Nav.GAME_DAYS),
    mainClasses = "gf-body--bring",
) {
    wrap {
        crumb("/gamedays/${gameDay.id}", gameDay.title)
        div("gf-bring__head") {
            heading(Level.H1, "gf-h1") { +"What are you bringing?" }
            if (!linked) notice("dice") {
                +"Link your BGG account to pick from your collection. "
                a("/bgg?returnTo=${"/gamedays/${gameDay.id}/bring".urlEncoded()}", classes = "gf-link") { +"Link BGG" }
            }
            if (linked && !gameDay.closed) p("gf-small gf-muted") {
                +"Bought something new? "
                a("/collection", classes = "gf-link") { +"Sync your BGG collection" }
                +" on the collection page."
            }
            if (gameDay.closed) p("gf-muted") { +if (gameDay.cancelled) "This game day was cancelled, so the shelf is final." else "This game day has ended, so the shelf is final." }
            else div("gf-bring__search") {
                searchField(
                    placeholder = if (linked) "Search your collection or BGG" else "Search BGG",
                    label = "Search games", url = "/gamedays/${gameDay.id}/bring/search", target = "#search-results",
                )
            }
        }
        div("gf-bring") {
            div("gf-bring__main") {
                if (!gameDay.closed) consumer.searchResults(gameDay, emptyList(), bggUnavailable = false)
                if (!gameDay.closed) consumer.bringRequests(gameDay, requests)
                consumer.bringList(gameDay, rows, linked)
                if (rows.isEmpty() && linked) emptyState("Your collection is empty", "Mark games as owned on BGG, sync them on the collection page, or search for them.", level = Level.H3)
            }
            aside("gf-bring__side") {
                consumer.bringSummary(gameDay, summary)
                if (!gameDay.ended) section("gf-card gf-bring__card gf-bring__wishes") {
                    heading(Level.H2, "gf-label") { +"Really want to play something?" }
                    p("gf-muted gf-small") { +"Heart up to $MAX_WISHES games on the shelf. Each heart nudges a game up the suggestions." }
                    consumer.wishList(gameDay, wishes)
                }
            }
        }
    }
}

/** Sent by the server (HX-Trigger) when a game was brought from a search result, so the list picks it up. */
const val BRING_LIST_CHANGED = "bring-list-changed"

/** Sent by the server (HX-Trigger) when a game was brought or dropped from the list itself, so the wish list picks it up. */
const val SHELF_CHANGED = "shelf-changed"

/**
 * The summary beside the grid (desktop: games on the table's shelf, who brought what, the Done button) and the tray on the phone.
 * Fetched again whenever the shelf changes, so the counts and the fan follow the ticks.
 */
fun TagConsumer<*>.bringSummary(gameDay: GameDaySummary, summary: ShelfSummary) = div("gf-bring__summary") {
    id = "bring-summary"
    refreshesQuietly()
    hx("get", "/gamedays/${gameDay.id}/bring/summary")
    // Debounced: several ticks in a burst each fire a trigger, and only the last answer is the whole truth.
    hx("trigger", "$BRING_LIST_CHANGED from:body delay:150ms, $SHELF_CHANGED from:body delay:150ms")
    hx("swap", "outerHTML")
    val mine = summary.mine.size
    section("gf-card gf-bring__card gf-bring__shelf") {
        p("gf-kicker") { +"On the shelf now" }
        div("gf-bring__count") {
            b("gf-bring__total") { +"${summary.total}" }
            span("gf-muted gf-small") {
                +if (summary.total == 0) "Nothing yet. Tick what goes in the bag." else "${if (summary.total == 1) "game" else "games"} from ${summary.byPlayer.size.of("player")}."
                if (mine > 0) +" You're adding $mine."
            }
        }
        if (summary.byPlayer.isNotEmpty()) ul("gf-bring__who") {
            summary.byPlayer.forEach { owner ->
                li {
                    avatar(owner.id, owner.name, size = "gf-avatar--sm")
                    span("gf-bring__who-name") { +owner.name }
                    span("gf-muted gf-small") { +owner.count.of("game") }
                }
            }
        }
        fan(summary.mine)
    }
    div("gf-bring__done") { linkButton("/gamedays/${gameDay.id}", if (mine == 0) "Done" else "Done: ${mine.of("game")}", ButtonStyle.PRIMARY, ButtonSize.LARGE, block = true) }
    div("gf-tray") {
        fan(summary.mine)
        b("gf-tray__count") { +mine.of("game") }
        linkButton("/gamedays/${gameDay.id}", "Done", ButtonStyle.PRIMARY)
    }
}

private fun FlowContent.fan(games: List<Game>) {
    if (games.isEmpty()) return
    decoration("gf-fan") {
        games.take(4).forEachIndexed { i, game -> art(game, extraStyle = "--r:${listOf(-6, 3, -2, 5)[i]}deg") }
    }
}

fun TagConsumer<*>.bringList(gameDay: GameDaySummary, rows: List<BringRow>, linked: Boolean) = div("gf-bring__list") {
    id = "bring-list"
    refreshesQuietly()
    hx("get", "/gamedays/${gameDay.id}/bring/list")
    hx("trigger", "$BRING_LIST_CHANGED from:body")
    hx("swap", "outerHTML")
    if (rows.isNotEmpty() || linked) heading(Level.H2, "gf-h2") { +"${if (linked) "Your collection" else "Your games"} · ${if (linked) rows.count { it.owned } else rows.size}" }
    ul("gf-bring-grid") { rows.forEach { bringItem(gameDay, it, readOnly = gameDay.closed) } }
}

/** Search results land in a grid like the collection's, above it; empty until something is searched. */
fun TagConsumer<*>.searchResults(gameDay: GameDaySummary, rows: List<BringRow>, bggUnavailable: Boolean, nothingFor: String? = null) = div("gf-bring__results") {
    id = "search-results"
    announce(searchAnnouncement(rows.size, nothingFor))
    if (bggUnavailable) p("gf-small gf-muted") { +"BGG isn't answering right now, so these are only games we already know." }
    if (rows.isNotEmpty()) {
        sectionHeading("Found")
        ul("gf-bring-grid") { rows.forEach { consumer.bringItem(gameDay, it, readOnly = false, prefix = "search") } }
    } else if (nothingFor != null) p("gf-small gf-muted gf-search-empty") { +"Nothing found for \u201c$nothingFor\u201d. Try another spelling." }
}

/** One game as a tile with its tick. Search results use [prefix] "search" so they don't share ids with the same game in the list. */
fun TagConsumer<*>.bringItem(gameDay: GameDaySummary, row: BringRow, readOnly: Boolean, error: String? = null, prefix: String = "bring") = li {
    val rowId = "$prefix-${row.game.id}"
    id = rowId
    announce(if (row.bringing) "Bringing ${row.game.name}" else "Not bringing ${row.game.name}")
    switchTile("bringing", "Bringing ${row.game.name}", checked = row.bringing, classes = "gf-tile gf-bringtile", id = "$rowId-switch", disabled = readOnly, configure = {
        hx("post", "/gamedays/${gameDay.id}/shelf/${row.game.id}")
        hx("trigger", "change")
        hx("target", "#$rowId")
        hx("swap", "outerHTML")
        hx("vals", """{"row": "$prefix"}""")
    }) {
        art(row.game)
        span("gf-tile__name") { bggNameLink(row.game) }
    }
    if (error != null) fieldError(error)
}

/** Every game on the shelf with a heart: any player can say which ones they really want to play. */
fun TagConsumer<*>.wishList(gameDay: GameDaySummary, rows: List<WishRow>) = ul("gf-list") {
    id = "wish-list"
    refreshesQuietly()
    hx("get", "/gamedays/${gameDay.id}/bring/wishes")
    hx("trigger", "$BRING_LIST_CHANGED from:body, $SHELF_CHANGED from:body")
    hx("swap", "outerHTML")
    if (rows.isEmpty()) li { p("gf-small gf-muted") { +"Nothing on the shelf yet. Tick the games you're bringing." } }
    rows.forEach { wishItem(gameDay, it) }
}

fun TagConsumer<*>.wishItem(gameDay: GameDaySummary, row: WishRow, error: String? = null) = li {
    val rowId = "wish-${row.game.id}"
    id = rowId
    announce(if (row.wants) "You really want to play ${row.game.name}" else "No heart on ${row.game.name}")
    gameRow(row.game)
    div("gf-wish") {
        chipToggle(if (row.wants) "I really want to play this" else "I want to play this", pressed = row.wants, icon = "heart", says = "I want to play ${row.game.name}") {
            // The id keeps focus on the heart when htmx swaps the row for the new state.
            id = "$rowId-heart"
            hx("post", "/gamedays/${gameDay.id}/shelf/${row.game.id}/wish")
            hx("vals", """{"wants": "${!row.wants}"}""")
            hx("target", "#$rowId")
            hx("swap", "outerHTML")
        }
        if (row.wishers > 0) span("gf-small gf-muted") { +"${row.wishers.of("person", "people")} want${if (row.wishers == 1) "s" else ""} this" }
    }
    if (error != null) fieldError(error)
}
