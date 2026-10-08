package gorefest.shell.web.gameday

import gorefest.core.PlayerId
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.Gap
import gorefest.core.gameday.PlayerReadiness
import gorefest.core.gameday.Readiness
import gorefest.core.gameday.TasteStatus
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import kotlinx.html.*

data class ReadinessView(val gameDay: GameDaySummary, val readiness: Readiness, val names: Map<PlayerId, String>, val daysToGo: Long, val tasteLink: String)

context(me: CurrentPlayer)
fun HTML.readinessPage(view: ReadinessView) {
    val (gameDay, readiness) = view
    page("How ready are we?", site = SiteBar(me, Nav.GAME_DAYS), mainClasses = "gf-landing") {
        wrap {
            div("gf-split__copy gf-readiness") {
                crumb("/gamedays/${gameDay.id}", gameDay.title)
                p("gf-kicker") {
                    +when {
                        view.daysToGo > 1 -> "${view.daysToGo} days to go"
                        view.daysToGo == 1L -> "Tomorrow"
                        view.daysToGo == 0L -> "Today"
                        else -> "Over"
                    }
                }
                h1("gf-h1 gf-h1--landing") { +"How ready are we?" }
                div("gf-stats") {
                    stat("${readiness.joined}", "joined")
                    stat("${readiness.shelf}", "on the shelf")
                    stat("${readiness.tasteReady}/${readiness.joined}", "taste ready")
                }
                readiness.gaps.lastOrNull()?.let { gap -> notice("users") { gapText(gap) } }
                if (readiness.shelf == 0) notice("dice") { strong { +"The shelf is empty." }; +" Tick what you're bringing and ask the others to do the same." }
            }
        }
        band(sunk = true) {
            div("gf-stack") {
                h2("gf-h2") { +"Players" }
                ul("gf-list") { readiness.players.forEach { playerRow(it, view) } }
                p("gf-hint") { +"Nudge copies a friendly message with a link to the taste round. Paste it in the chat." }
            }
        }
    }
}

context(me: CurrentPlayer)
private fun UL.playerRow(p: PlayerReadiness, view: ReadinessView) = li("gf-row") {
    val name = view.names[p.player] ?: "Unknown player"
    avatar(p.player, view.names[p.player])
    span("gf-row__main") {
        span("gf-row__title") { +name }
        span("gf-row__sub") {
            +when (p.status) {
                TasteStatus.NONE -> "Joined, no answers yet"
                TasteStatus.THIN -> "${p.answers.of("answer")}: picks will be guesses"
                TasteStatus.READY -> "${p.answers} answers · brought ${p.brought}"
            }
        }
    }
    if (p.status == TasteStatus.READY) span("gf-tag gf-tag--olive") { icon("check"); +"Ready" }
    // No email invites: the host pastes this into the chat.
    else if (p.player != me.id) copyButton(
        text = "Hey ${name.substringBefore(' ')}, got 2 minutes before ${view.gameDay.title}? Tell the app what you like, so it can pick games you'll enjoy: ${view.tasteLink}",
        label = "Nudge",
        ariaLabel = "Copy a nudge for $name",
    )
}

private fun P.gapText(gap: Gap) {
    strong { +"Nothing plays well at ${gap.players}." }
    when (gap.games.size) {
        0 -> +" No game on the shelf suits ${gap.players} players. Ask for one that does, or plan to split tables."
        1 -> +" Only ${gap.games[0].name} works for ${gap.players}. Ask for another ${gap.players}-player game."
        else -> +" Only ${gap.games.joinToString(" and ") { it.name }} work for ${gap.players}. Ask for another ${gap.players}-player game."
    }
}
