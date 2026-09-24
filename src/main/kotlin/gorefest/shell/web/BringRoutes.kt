package gorefest.shell.web

import gorefest.core.BggId
import gorefest.core.GameDayId
import gorefest.core.bgg.Game
import gorefest.core.eventsourcing.Decision
import gorefest.core.gameday.BringGame
import gorefest.core.gameday.BringGameDecider
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.SetPlayWish
import gorefest.core.gameday.SetPlayWishDecider
import gorefest.core.gameday.ShelfGame
import gorefest.shell.App
import gorefest.shell.auth.Login
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.ul

/** A game as it appears on the bring page: am I bringing it, do I really want to play it. */
data class BringRow(val game: Game, val bringing: Boolean, val wants: Boolean)

context(login: Login)
fun Route.bringRoutes(app: App) {
    get("/gamedays/{id}/bring") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val linked = me.id in app.bggAccounts.state
            val owned = if (linked) app.collectionSync.collection(me.id) else emptySet()
            val shelf = app.shelves.state[gameDay.id].orEmpty()
            // My collection, plus anything I brought that isn't in it (found by search).
            val ids = owned + shelf.filter { me.id in it.owners }.map { it.game }
            val rows = ids.mapNotNull(app.catalog::get).sortedBy { it.name.lowercase() }.map { bringRow(it, shelf, me) }
            call.respondHtml {
                page(
                    "Bring games",
                    appBar = { backLink("/gamedays/${gameDay.id}", gameDay.title); appBarTitle(gameDay.title) },
                    dock = { a("/gamedays/${gameDay.id}", classes = "gf-btn gf-btn--primary gf-btn--lg gf-btn--block") { +"Done" } },
                ) {
                    div("gf-stack") {
                        h1("gf-h1") { +"What are you bringing?" }
                        p("gf-muted") { +if (linked) "From your collection. Tick what goes in the bag." else "Search for the games you're bringing." }
                    }
                    if (!linked) div("gf-notice") {
                        icon("dice")
                        p {
                            +"Link your BGG account to pick from your collection. "
                            a("/bgg?returnTo=${"/gamedays/${gameDay.id}/bring".urlEncoded()}", classes = "gf-link") { +"Link BGG" }
                        }
                    }
                    if (gameDay.ended) p("gf-muted") { +"This game day has ended, so the shelf is final." }
                    else {
                        div("gf-search") {
                            icon("search")
                            input(InputType.search, classes = "gf-input", name = "q") {
                                placeholder = if (linked) "Search your collection or BGG" else "Search BGG"
                                attributes["aria-label"] = "Search games"
                                attributes["autocomplete"] = "off"
                                hx("get", "/gamedays/${gameDay.id}/bring/search")
                                hx("trigger", "input changed delay:300ms, search")
                                hx("target", "#search-results")
                                hx("swap", "outerHTML")
                            }
                        }
                        consumer.searchResults(gameDay, emptyList(), bggUnavailable = false)
                    }
                    ul("gf-list") {
                        id = "bring-list"
                        rows.forEach { consumer.bringItem(gameDay, it, readOnly = gameDay.ended) }
                    }
                    if (rows.isEmpty() && linked) emptyState("Your collection is empty", "Mark games as owned on BGG, or search for them.")
                }
            }
        }
    }

    get("/gamedays/{id}/bring/search") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val result = app.gameSearch.search(call.parameters["q"].orEmpty())
            val shelf = app.shelves.state[gameDay.id].orEmpty()
            respondFragment { searchResults(gameDay, result.games.map { bringRow(it, shelf, me) }, result.bggUnavailable) }
        }
    }

    post("/gamedays/{id}/shelf/{game}") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val (gameDay, game) = shelfTarget(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val bringing = form["bringing"] == "true"
            val decision = app.commands.handle(BringGameDecider, BringGame(gameDay.id, me.id, game.id, bringing))
            respondBringDecision(app, gameDay, game, me, decision, rowPrefix = form["row"] ?: "bring")
        }
    }

    post("/gamedays/{id}/shelf/{game}/wish") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val (gameDay, game) = shelfTarget(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val wants = form["wants"] == "true"
            val decision = app.commands.handle(SetPlayWishDecider, SetPlayWish(gameDay.id, me.id, game.id, wants))
            respondBringDecision(app, gameDay, game, me, decision, rowPrefix = form["row"] ?: "bring")
        }
    }
}


private fun RoutingContext.shelfTarget(app: App, me: CurrentPlayer): Pair<GameDaySummary, Game>? {
    val gameDay = memberGameDay(app, me) ?: return null
    val game = call.parameters["game"]?.toIntOrNull()?.let { app.catalog[BggId(it)] } ?: return null
    return gameDay to game
}

private suspend fun RoutingContext.respondBringDecision(
    app: App, gameDay: GameDaySummary, game: Game, me: CurrentPlayer, decision: Decision<*>, rowPrefix: String,
) {
    val row = bringRow(game, app.shelves.state[gameDay.id].orEmpty(), me)
    val prefix = if (rowPrefix == "search") "search" else "bring"
    respondFragment(if (decision is Decision.Rejected) HttpStatusCode.Conflict else HttpStatusCode.OK) {
        bringItem(gameDay, row, readOnly = gameDay.ended, error = (decision as? Decision.Rejected)?.reason, prefix = prefix)
    }
}

private fun TagConsumer<*>.searchResults(gameDay: GameDaySummary, rows: List<BringRow>, bggUnavailable: Boolean) = div {
    id = "search-results"
    if (bggUnavailable) p("gf-small gf-muted") { +"BGG isn't answering right now, so these are only games we already know." }
    if (rows.isNotEmpty()) ul("gf-list") { rows.forEach { consumer.bringItem(gameDay, it, readOnly = false, prefix = "search") } }
}

private fun bringRow(game: Game, shelf: List<ShelfGame>, me: CurrentPlayer): BringRow {
    val entry = shelf.firstOrNull { it.game == game.id }
    return BringRow(game, bringing = entry != null && me.id in entry.owners, wants = entry != null && me.id in entry.eager)
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
            role("switch")
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
    if (error != null) p("gf-error") { attributes["role"] = "alert"; icon("alert"); +error }
}

private fun FlowContent.wishToggle(gameDay: GameDaySummary, row: BringRow, rowId: String, prefix: String) =
    button(type = ButtonType.button, classes = if (row.wants) "gf-chip gf-chip--text" else "gf-chip") {
        attributes["aria-pressed"] = row.wants.toString()
        attributes["style"] = "margin: 0 0 var(--space-2) var(--space-4)"
        hx("post", "/gamedays/${gameDay.id}/shelf/${row.game.id}/wish")
        hx("vals", """{"wants": "${!row.wants}", "row": "$prefix"}""")
        hx("target", "#$rowId")
        hx("swap", "outerHTML")
        icon("heart")
        span { +if (row.wants) "Itching to play this" else "I really want to play this" }
    }

private fun kotlinx.html.INPUT.role(value: String) {
    attributes["role"] = value
}
