package gorefest.shell.web.shelf

import gorefest.core.game.Game
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.MAX_WISHES
import gorefest.shell.web.bgg.collection
import gorefest.shell.web.components.*
import gorefest.shell.web.urlEncoded
import kotlinx.html.*

/** A game as it appears on the bring page: am I bringing it. */
data class BringRow(val game: Game, val bringing: Boolean)

/** A game on the shelf as it appears under "What do you want to play?": do I want it, and how many people do. */
data class WishRow(val game: Game, val wants: Boolean, val wishers: Int)

fun HTML.bringPage(gameDay: GameDaySummary, rows: List<BringRow>, wishes: List<WishRow>, linked: Boolean) = page(
    "Bring games",
    appBar = { backLink("/gamedays/${gameDay.id}", gameDay.title); appBarTitle(gameDay.title) },
    dock = { primaryLink("/gamedays/${gameDay.id}", "Done") },
) {
    div("gf-stack") {
        h1("gf-h1") { +"What are you bringing?" }
        p("gf-muted") { +if (linked) "From your collection. Tick what goes in the bag." else "Search for the games you're bringing." }
    }
    if (!linked) notice("dice") {
        +"Link your BGG account to pick from your collection. "
        a("/bgg?returnTo=${"/gamedays/${gameDay.id}/bring".urlEncoded()}", classes = "gf-link") { +"Link BGG" }
    }
    if (gameDay.ended) p("gf-muted") { +"This game day has ended, so the shelf is final." }
    else {
        searchField(
            placeholder = if (linked) "Search your collection or BGG" else "Search BGG",
            label = "Search games", url = "/gamedays/${gameDay.id}/bring/search", target = "#search-results",
        )
        consumer.searchResults(gameDay, emptyList(), bggUnavailable = false)
    }
    consumer.bringList(gameDay, rows)
    if (rows.isEmpty() && linked) emptyState("Your collection is empty", "Mark games as owned on BGG, or search for them.")
    if (!gameDay.ended) div("gf-section") {
        h2("gf-h2") { +"What do you want to play?" }
        p("gf-muted") { +"Heart up to $MAX_WISHES games on the shelf you're really keen on. Each heart nudges a game up the suggestions." }
        consumer.wishList(gameDay, wishes)
    }
}

/** Sent by the server (HX-Trigger) when a game was brought from a search result, so the list picks it up. */
const val BRING_LIST_CHANGED = "bring-list-changed"

/** Sent by the server (HX-Trigger) when a game was brought or dropped from the list itself, so the wish list picks it up. */
const val SHELF_CHANGED = "shelf-changed"

fun TagConsumer<*>.bringList(gameDay: GameDaySummary, rows: List<BringRow>) = ul("gf-list") {
    id = "bring-list"
    hx("get", "/gamedays/${gameDay.id}/bring/list")
    hx("trigger", "$BRING_LIST_CHANGED from:body")
    hx("swap", "outerHTML")
    rows.forEach { bringItem(gameDay, it, readOnly = gameDay.ended) }
}

fun TagConsumer<*>.searchResults(gameDay: GameDaySummary, rows: List<BringRow>, bggUnavailable: Boolean) = div {
    id = "search-results"
    if (bggUnavailable) p("gf-small gf-muted") { +"BGG isn't answering right now, so these are only games we already know." }
    if (rows.isNotEmpty()) ul("gf-list") { rows.forEach { consumer.bringItem(gameDay, it, readOnly = false, prefix = "search") } }
}

/** One row with the bringing switch. Search results use [prefix] "search" so they don't share ids with the same game in the list. */
fun TagConsumer<*>.bringItem(gameDay: GameDaySummary, row: BringRow, readOnly: Boolean, error: String? = null, prefix: String = "bring") = li {
    val rowId = "$prefix-${row.game.id}"
    id = rowId
    gameRow(row.game) {
        input(InputType.checkBox, classes = "gf-switch", name = "bringing") {
            attributes["role"] = "switch"
            value = "true"
            checked = row.bringing
            disabled = readOnly
            attributes["aria-label"] = "Bringing ${row.game.name}"
            hx("post", "/gamedays/${gameDay.id}/shelf/${row.game.id}")
            hx("trigger", "change")
            hx("target", "#$rowId")
            hx("swap", "outerHTML")
            hx("vals", """{"row": "$prefix"}""")
        }
    }
    if (error != null) fieldError(error)
}

/** Every game on the shelf with a heart: any player can say which ones they really want to play. */
fun TagConsumer<*>.wishList(gameDay: GameDaySummary, rows: List<WishRow>) = ul("gf-list") {
    id = "wish-list"
    hx("get", "/gamedays/${gameDay.id}/bring/wishes")
    hx("trigger", "$BRING_LIST_CHANGED from:body, $SHELF_CHANGED from:body")
    hx("swap", "outerHTML")
    if (rows.isEmpty()) li { p("gf-small gf-muted") { +"Nothing on the shelf yet. Tick what you're bringing above." } }
    rows.forEach { wishItem(gameDay, it) }
}

fun TagConsumer<*>.wishItem(gameDay: GameDaySummary, row: WishRow, error: String? = null) = li {
    val rowId = "wish-${row.game.id}"
    id = rowId
    gameRow(row.game)
    div("gf-wish") {
        button(type = ButtonType.button, classes = "gf-chip gf-chip--text") {
            attributes["aria-pressed"] = row.wants.toString()
            hx("post", "/gamedays/${gameDay.id}/shelf/${row.game.id}/wish")
            hx("vals", """{"wants": "${!row.wants}"}""")
            hx("target", "#$rowId")
            hx("swap", "outerHTML")
            icon("heart")
            span { +if (row.wants) "I really want to play this" else "I want to play this" }
        }
        if (row.wishers > 0) span("gf-small gf-muted") { +"${row.wishers.of("person", "people")} want${if (row.wishers == 1) "s" else ""} this" }
    }
    if (error != null) fieldError(error)
}
