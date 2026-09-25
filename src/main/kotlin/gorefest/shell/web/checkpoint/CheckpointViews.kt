package gorefest.shell.web.checkpoint

import gorefest.core.PlayerId
import gorefest.core.game.Game
import gorefest.core.gameday.GameDaySummary
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import io.ktor.http.Parameters
import kotlinx.html.*

/** Duration options: label to minutes. */
val DURATIONS = listOf("15m" to 15, "30m" to 30, "45m" to 45, "1h" to 60, "90m" to 90, "2h" to 120, "3h+" to 180)

/** Step 1 of a checkpoint: what, who, how long. */
data class LogFormView(val gameDay: GameDaySummary, val games: List<Game>, val names: Map<PlayerId, String>)

fun HTML.logPage(view: LogFormView, checkpoint: String, me: CurrentPlayer) = page(
    "What did you play?",
    appBar = { backLink("/gamedays/${view.gameDay.id}", view.gameDay.title); appBarTitle(view.gameDay.title) },
    dock = { primaryButton("Next", form = "log-form") },
) {
    div("gf-stack") {
        p("gf-kicker") { +checkpoint }
        h1("gf-h1") { +"What did you play?" }
    }
    consumer.logForm(view, me, Parameters.Empty, error = null)
}

fun TagConsumer<*>.logForm(view: LogFormView, me: CurrentPlayer, values: Parameters, error: String?) =
    form(action = "/gamedays/${view.gameDay.id}/log", method = FormMethod.post, classes = "gf-stack") {
        id = "log-form"
        hx("post", "/gamedays/${view.gameDay.id}/log")
        hx("swap", "outerHTML")
        if (error != null) errorNotice(error)
        if (view.games.isEmpty()) p("gf-muted") { +"Nothing on the shelf yet. Bring the game first, then log it." }
        val chosenGame = values["game"] ?: view.games.firstOrNull()?.id?.toString()
        div("gf-tiles") { view.games.forEach { gameTileChoice(it, "game", checked = it.id.toString() == chosenGame) } }
        div("gf-section") {
            h2("gf-h2") { +"Who played?" }
            val chosen = values.getAll("players")?.toSet() ?: setOf(me.id.toString())
            playerChips(view.gameDay.players, view.names, checked = { it.toString() in chosen })
        }
        div("gf-section") {
            h2("gf-h2") { +"Roughly how long?" }
            segmented("duration", "Duration", DURATIONS.map { it.first }, checked = values["duration"] ?: "45m")
        }
    }

/** Step 2: one PLAYED comparison between what was just played and something else they played. */
data class LogCompareView(val gameDay: GameDaySummary, val played: Game, val other: Game, val players: Int, val minutes: String, val next: String)

fun HTML.logComparePage(view: LogCompareView) = page(
    "One quick question",
    appBar = { appBarTitle(view.gameDay.title); appBarAction(view.next, "Skip") },
    mainClasses = "gf-wizard",
) {
    notice("check", NoticeKind.GOOD) { +"Logged "; strong { +view.played.name }; +": ${view.players.of("player")}, ${view.minutes} min." }
    div("gf-stack") {
        p("gf-kicker") { +"One quick question" }
        h1("gf-h1") { +"Which did you enjoy more?" }
    }
    form(action = "/gamedays/${view.gameDay.id}/log/compare", method = FormMethod.post, classes = "gf-wizard__form") {
        hiddenInput("a", view.played.id.toString())
        hiddenInput("b", view.other.id.toString())
        hiddenInput("next", view.next)
        duel(view.played, view.other)
        duelAnswers()
    }
}
