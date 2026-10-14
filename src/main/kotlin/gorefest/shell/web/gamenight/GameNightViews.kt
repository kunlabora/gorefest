package gorefest.shell.web.gamenight

import gorefest.core.PlannedGame
import gorefest.core.PlayerId
import gorefest.core.game.Game
import gorefest.core.gamenight.GameNightSummary
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import kotlinx.html.*
import java.time.LocalDate

/** Everything the game night page shows: who's coming, the plan with who brings what, and what nobody can bring. */
data class GameNightView(
    val night: GameNightSummary,
    val names: Map<PlayerId, String>,
    val plan: List<Pair<Game, PlannedGame>>,
    val missing: List<Game>,
    val today: LocalDate,
    val me: CurrentPlayer,
)

fun HTML.gameNightPage(view: GameNightView) {
    val night = view.night
    fun name(id: PlayerId) = if (id == view.me.id) "you" else view.names[id] ?: SOMEONE
    page(night.title, site = SiteBar(view.me, Nav.GAME_DAYS)) {
        wrap {
            crumb("/", "All game days")
            p("gf-kicker") { +"Game night" }
            h1("gf-h1 gf-h1--landing") { +night.title }
            ul("gf-hubfacts") {
                li { icon("calendar"); +night.date.long() }
                if (night.place.isNotBlank()) li { icon("pin"); +night.place }
                li { icon("users"); +"${night.players.size} coming" }
            }
            if (night.plan.isNotEmpty()) linkButton("/gamenights/${night.id}/table", "Pick a game for my table", ButtonStyle.SECONDARY, ButtonSize.LARGE, icon = "dice")
        }
        band(sunk = true) {
            section("gf-stack") {
                id = "plan"
                h2("gf-h2 gf-h2--section") { +"The plan · ${view.plan.size}" }
                if (view.plan.isEmpty()) emptyState("No games planned", "Nobody coming said they'd bring one of the poll's games. Bring something anyway?")
                else ul("gf-list") {
                    view.plan.forEach { (game, planned) ->
                        li {
                            val wanted = planned.wantedBy.map(::name)
                            gameRow(game, meta = buildString {
                                append("${name(planned.bringer).replaceFirstChar(Char::uppercase)} ${if (planned.bringer == view.me.id) "bring" else "brings"} it")
                                if (wanted.isNotEmpty()) append(" · ${wanted.joinAnd()} ${if (wanted.size == 1 && planned.wantedBy.single() != view.me.id) "wants" else "want"} to play it")
                            })
                        }
                    }
                }
                if (view.missing.isNotEmpty()) div("gf-stack") {
                    id = "missing"
                    h3("gf-h3") { +"Nobody can bring" }
                    p("gf-small gf-muted") { +"These were in the poll, but nobody coming said they'd bring them. Got one? Bring it along." }
                    ul("gf-list") { view.missing.forEach { li { gameRow(it) } } }
                }
            }
        }
        wrap {
            section("gf-section") {
                id = "players"
                h2("gf-h2") { +"Who's coming" }
                ul("gf-list") {
                    night.players.forEach { player ->
                        li("gf-row") {
                            avatar(player, view.names[player])
                            span("gf-row__main") {
                                span("gf-row__title") { +(view.names[player] ?: SOMEONE) }
                                val brings = view.plan.filter { it.second.bringer == player }.map { it.first.name }
                                span("gf-row__sub") { +if (brings.isEmpty()) "Bringing nothing from the plan" else "Brings ${brings.joinAnd()}" }
                            }
                            if (player == night.host) span("gf-tag") { +"Host" }
                        }
                    }
                }
            }
        }
    }
}

/** A game night on the home page. */
fun FlowContent.gameNightCard(night: GameNightSummary, today: LocalDate) = dayCard(
    href = "/gamenights/${night.id}",
    month = if (night.date == today) "Today" else night.date.monthShort(),
    day = night.date.dayOfMonth.toString(),
    title = night.title,
    meta = listOf("Game night", night.place, "${night.players.size} coming").filter { it.isNotBlank() }.joinToString(" · "),
)

private fun List<String>.joinAnd(): String = if (size < 2) joinToString() else dropLast(1).joinToString(", ") + " and " + last()
