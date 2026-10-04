package gorefest.shell.web.suggestions

import gorefest.core.PlayerId
import gorefest.core.gameday.GameDaySummary
import gorefest.core.matching.Matcher
import gorefest.core.matching.Suggestions
import gorefest.shell.web.components.*
import kotlinx.html.*

/** Time options: label to budget in minutes (null: any). */
/** Hidden marker in the table form: present-but-empty `players` means nobody is ticked, not everybody. */
const val TABLE_SET = "tableSet"

val TIMES = listOf("Any" to null, "45m" to 45, "90m" to 90, "2h" to 120, "3h+" to 240)

/** The table picker: who's playing, how long, and the suggestions for that. [chosen] is null for "everyone". */
data class TableView(
    val gameDay: GameDaySummary,
    val names: Map<PlayerId, String>,
    val chosen: Set<String>?,
    val time: String,
    val suggestions: Suggestions,
    val tableSize: Int,
    val me: PlayerId,
)

fun HTML.tablePage(view: TableView) = page(
    "Pick a game",
    appBar = { backLink("/gamedays/${view.gameDay.id}", view.gameDay.title); appBarTitle("${view.gameDay.title} · Pick a game") },
) {
    form(classes = "gf-section") {
        id = "table-form"
        hx("get", "/gamedays/${view.gameDay.id}/suggestions")
        hx("trigger", "change")
        hx("target", "#suggestions")
        hx("swap", "outerHTML")
        hiddenInput(TABLE_SET, "1")
        h2("gf-h2") { +"Who's at the table?" }
        playerChips(view.gameDay.players, view.names, checked = { view.chosen == null || it.toString() in view.chosen })
        segmented("time", "Time", TIMES.map { it.first }, checked = view.time, idPrefix = "time")
    }
    consumer.suggestions(view.suggestions, view.tableSize, view.gameDay, view.names, view.me)
}

fun TagConsumer<*>.suggestions(suggestions: Suggestions, tableSize: Int, gameDay: GameDaySummary, names: Map<PlayerId, String>, me: PlayerId) = section("gf-section") {
    id = "suggestions"
    attributes["aria-live"] = "polite"
    when (suggestions) {
        is Suggestions.Found -> {
            h2("gf-h2") { +"Play this" }
            if (suggestions.unknown.isNotEmpty()) unknownTaste(suggestions.unknown, tableSize, gameDay, names, me)
            suggestions.top.forEachIndexed { i, s ->
                rankedGame(i + 1, s.game, s.reason) { icon("clock"); +"~${s.minutes} min for ${if (i == 0) "the $tableSize of you" else "$tableSize"}" }
            }
        }
        is Suggestions.NothingFits -> emptyState(suggestions.title, suggestions.text) {
            val relax = suggestions.relaxTo ?: return@emptyState
            val label = TIMES.firstOrNull { it.second == relax }?.first ?: "Any"
            div("gf-stack") {
                style = "width: 100%"
                relaxButton(label, "Try ${Matcher.duration(relax)}: ${suggestions.fitsThen} ${if (suggestions.fitsThen == 1) "game fits" else "games fit"}")
            }
        }
    }
}

/** Picks that time option in the form above, which re-asks for suggestions. */
private fun FlowContent.relaxButton(timeLabel: String, text: String) = button(type = ButtonType.button, classes = buttonClasses(ButtonStyle.PRIMARY, block = true)) {
    hyperscript("on click set #time-${timeLabel.idSlug()}.checked to true then send change to #table-form")
    +text
}

/**
 * Says what the picks lean on when taste is missing, and lets the viewer fix their own part:
 * a few pairs turn "fits the table" into "you'll like it".
 */
private fun FlowContent.unknownTaste(unknown: List<PlayerId>, tableSize: Int, gameDay: GameDaySummary, names: Map<PlayerId, String>, me: PlayerId) {
    val text = if (unknown.size == tableSize) {
        "${if (tableSize == 1) "We don't know your taste yet" else "Nobody at this table has done a taste round yet"}, so these picks go by player count, playing time and who's keen to play what."
    } else {
        val who = unknown.map { if (it == me) "you" else names[it] ?: "someone" }.sortedBy { it != "you" }.joinAnd()
        "Picks for $who go by player count and playing time: we haven't learned ${if (me in unknown) "your" else "their"} taste yet."
    }
    notice("spark") { +text }
    if (me in unknown) linkButton("/gamedays/${gameDay.id}/taste", "Take the 2-minute taste round", ButtonStyle.SECONDARY)
}

private fun List<String>.joinAnd(): String = if (size < 2) joinToString() else dropLast(1).joinToString(", ") + " and " + last()
