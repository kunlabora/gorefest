package gorefest.shell.web.shelf

import gorefest.core.game.Game
import gorefest.core.gameday.GameDaySummary
import gorefest.shell.web.bgg.collection
import gorefest.shell.web.components.*
import gorefest.shell.web.urlEncoded
import kotlinx.html.*

/** A game as it appears on the bring page: am I bringing it, do I really want to play it. */
data class BringRow(val game: Game, val bringing: Boolean, val wants: Boolean)

fun HTML.bringPage(gameDay: GameDaySummary, rows: List<BringRow>, linked: Boolean) = page(
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
    if (gameDay.closed) p("gf-muted") { +if (gameDay.cancelled) "This game day was cancelled, so the shelf is final." else "This game day has ended, so the shelf is final." }
    else {
        searchField(
            placeholder = if (linked) "Search your collection or BGG" else "Search BGG",
            label = "Search games", url = "/gamedays/${gameDay.id}/bring/search", target = "#search-results",
        )
        consumer.searchResults(gameDay, emptyList(), bggUnavailable = false)
    }
    consumer.bringList(gameDay, rows)
    if (rows.isEmpty() && linked) emptyState("Your collection is empty", "Mark games as owned on BGG, or search for them.")
}

/** Sent by the server (HX-Trigger) when a game was brought from a search result, so the list picks it up. */
const val BRING_LIST_CHANGED = "bring-list-changed"

fun TagConsumer<*>.bringList(gameDay: GameDaySummary, rows: List<BringRow>) = ul("gf-list") {
    id = "bring-list"
    hx("get", "/gamedays/${gameDay.id}/bring/list")
    hx("trigger", "$BRING_LIST_CHANGED from:body")
    hx("swap", "outerHTML")
    rows.forEach { bringItem(gameDay, it, readOnly = gameDay.closed) }
}

fun TagConsumer<*>.searchResults(gameDay: GameDaySummary, rows: List<BringRow>, bggUnavailable: Boolean) = div {
    id = "search-results"
    if (bggUnavailable) p("gf-small gf-muted") { +"BGG isn't answering right now, so these are only games we already know." }
    if (rows.isNotEmpty()) ul("gf-list") { rows.forEach { consumer.bringItem(gameDay, it, readOnly = false, prefix = "search") } }
}

/**
 * One row with the bringing switch; once brought, a "really want to play" toggle appears.
 * Search results use [prefix] "search" so they don't share ids with the same game in the list.
 */
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
    if (row.bringing && !readOnly) wishToggle(gameDay, row, rowId, prefix)
    if (error != null) fieldError(error)
}

private fun FlowContent.wishToggle(gameDay: GameDaySummary, row: BringRow, rowId: String, prefix: String) = div("gf-wish") {
    button(type = ButtonType.button, classes = "gf-chip gf-chip--text") {
        attributes["aria-pressed"] = row.wants.toString()
        hx("post", "/gamedays/${gameDay.id}/shelf/${row.game.id}/wish")
        hx("vals", """{"wants": "${!row.wants}", "row": "$prefix"}""")
        hx("target", "#$rowId")
        hx("swap", "outerHTML")
        icon("heart")
        span { +if (row.wants) "Itching to play this" else "I really want to play this" }
    }
    span("gf-small gf-muted") {
        +if (row.wants) "It gets a nudge in the suggestions. Tap again to undo." else "Gives it a nudge in the suggestions."
    }
}
