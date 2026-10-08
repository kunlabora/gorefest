package gorefest.shell.web.suggestions

import gorefest.core.PlayerId
import gorefest.core.gameday.GameDaySummary
import gorefest.core.matching.Suggestions
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import kotlinx.html.*

/** Hidden marker in the table form: present-but-empty `players` means nobody is ticked, not everybody. */
const val TABLE_SET = "tableSet"

/** Time options: label to budget in minutes (null: any). */
val TIMES = listOf("Any" to null, "45m" to 45, "90m" to 90, "2h" to 120, "3h+" to 240)

/** The table picker: who's playing, how long, and the suggestions for that. [chosen] is null for "everyone"; [table] is who that comes to. */
data class TableView(
    val gameDay: GameDaySummary,
    val names: Map<PlayerId, String>,
    val chosen: Set<String>?,
    val table: List<PlayerId>,
    val time: String,
    val suggestions: Suggestions,
    val tableSize: Int,
    val me: CurrentPlayer,
)

fun HTML.tablePage(view: TableView) = page(
    "Pick a game",
    site = SiteBar(view.me, Nav.GAME_DAYS),
    mainClasses = "gf-body--pick",
) {
    wrap {
        crumb("/gamedays/${view.gameDay.id}", view.gameDay.title)
        h1("gf-h1 gf-picker__title") { +"Pick a game" }
        div("gf-picker") {
            form(classes = "gf-picker__side") {
                id = "table-form"
                hx("get", "/gamedays/${view.gameDay.id}/suggestions")
                hx("trigger", "change")
                hx("target", "#suggestions")
                hx("swap", "outerHTML")
                hiddenInput(TABLE_SET, "1")
                section("gf-picker__panel") {
                    h2("gf-h2") { +"Who's at the table?" }
                    playerChips(view.gameDay.players, view.names, checked = { view.chosen == null || it.toString() in view.chosen })
                    h2("gf-h2") { +"How long?" }
                    segmented("time", "Time", TIMES.map { it.first }, checked = view.time, idPrefix = "time")
                }
            }
            consumer.suggestions(view)
        }
    }
}

/**
 * The answer, swapped whenever the table form changes: the collapsed table bar (phone) and the picks.
 * The root lays out as the page's own grid cells (`display: contents`), so the bar sits above the form on the phone.
 */
fun TagConsumer<*>.suggestions(view: TableView) = section("gf-picker__answer") {
    id = "suggestions"
    attributes["aria-live"] = "polite"
    tableBar(view)
    div("gf-picker__results") {
        when (val suggestions = view.suggestions) {
            is Suggestions.Found -> {
                h2("gf-h2") { +"Play this" }
                if (suggestions.unknown.isNotEmpty()) unknownTaste(suggestions.unknown, view.tableSize, view.gameDay, view.names, view.me.id)
                suggestions.top.take(1).forEach { s ->
                    rankedGame(1, s.game, s.reason.text()) { icon("clock"); +"~${s.minutes} min for the ${view.tableSize} of you" }
                }
                if (suggestions.top.size > 1) div("gf-picker__more") {
                    suggestions.top.drop(1).forEachIndexed { i, s ->
                        rankedGame(i + 2, s.game, s.reason.text()) { icon("clock"); +"~${s.minutes} min for ${view.tableSize}" }
                    }
                }
            }
            is Suggestions.NothingFits -> emptyState(suggestions.title(), suggestions.text()) {
                if (suggestions !is Suggestions.NothingFits.NoGameInTime) return@emptyState
                val relax = suggestions.relaxTo ?: return@emptyState
                val fitsThen = suggestions.fitsThen
                val label = TIMES.firstOrNull { it.second == relax }?.first ?: "Any"
                div("gf-stack") {
                    style = "width: 100%"
                    relaxButton(label, "Try ${relax.asDuration()}: $fitsThen ${if (fitsThen == 1) "game fits" else "games fit"}")
                }
            }
        }
    }
}

/** On the phone the table form folds away: this bar says who and how long, and "Change" unfolds it. */
private fun FlowContent.tableBar(view: TableView) = div("gf-tablebar") {
    avatarStack(view.table, view.names, max = 4)
    div("gf-tablebar__text") {
        b { +if (view.tableSize == 0) "Nobody at the table" else "${view.tableSize} at the table" }
        span("gf-muted gf-small") {
            +when (view.time) {
                "Any" -> "Any length"
                "3h+" -> "3 hours or more"
                else -> TIMES.firstOrNull { it.first == view.time }?.second?.let { "Up to ${it.asDuration()}" } ?: "Any length"
            }
        }
    }
    button(type = ButtonType.button, classes = buttonClasses(ButtonStyle.SECONDARY, ButtonSize.SMALL)) {
        attributes["aria-controls"] = "table-form"
        hyperscript("on click toggle .is-open on #table-form")
        +"Change"
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
