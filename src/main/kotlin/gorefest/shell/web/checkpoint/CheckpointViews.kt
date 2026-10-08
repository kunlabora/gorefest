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

/** What the summary card and the phone dock say: the chosen game, how many are at the table, and the time. */
private data class LogSummary(val game: String?, val players: Int, val duration: String) {
    val about: String get() = "${players.of("player")} · ${durationText(duration)}"
    val dock: String get() = if (game == null) "Log it" else "Log $game · ${players.of("player")} · $duration"
}

/** "45m" is "about 45 min", "2h" is "about 2 h", "3h+" is "3 h or more". The form's hyperscript says the same. */
private fun durationText(label: String): String {
    val m = Regex("""(\d+)(m|h)(\+)?""").matchEntire(label) ?: return "no time chosen"
    val (n, unit, more) = m.destructured
    return if (unit == "h") (if (more.isNotEmpty()) "$n h or more" else "about $n h") else "about $n min"
}

private fun logSummary(view: LogFormView, values: Parameters): LogSummary {
    val chosen = values["game"] ?: view.games.firstOrNull()?.id?.toString()
    return LogSummary(view.games.firstOrNull { it.id.toString() == chosen }?.name, values.getAll("players")?.size ?: 1, values["duration"] ?: "45m")
}

fun HTML.logPage(view: LogFormView, checkpoint: String, me: CurrentPlayer) = page(
    "What did you play?",
    site = SiteBar(me, Nav.GAME_DAYS),
    dock = {
        classes = classes + "gf-phone-only"
        button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.LARGE, block = true)) {
            attributes["form"] = "log-form"
            spinner()
            span { attributes["data-log"] = "dock"; +logSummary(view, Parameters.Empty).dock }
        }
    },
) {
    wrap {
        div("gf-log__head") {
            crumb("/gamedays/${view.gameDay.id}", view.gameDay.title)
            p("gf-kicker") { +checkpoint }
            h1("gf-h1 gf-h1--landing") { +"What did you play?" }
        }
        consumer.logForm(view, me, Parameters.Empty, error = null)
    }
}

/** Updates the summary card and the dock label as choices change, in the browser: no round trip. */
private val LOG_SUMMARY_SCRIPT = """def update()
  js(me)
    const g = me.querySelector('input[name=game]:checked');
    const name = g ? g.closest('label').querySelector('.gf-tile__name').textContent : '';
    const n = me.querySelectorAll('input[name=players]:checked').length;
    const d = (me.querySelector('input[name=duration]:checked') || {}).value || '';
    const m = /^(\d+)(m|h)(\+)?$/.exec(d);
    const time = !m ? 'no time chosen' : m[2] === 'h' ? (m[3] ? m[1] + ' h or more' : 'about ' + m[1] + ' h') : 'about ' + m[1] + ' min';
    const who = n + (n === 1 ? ' player' : ' players');
    const say = function (key, text) { document.querySelectorAll('[data-log=' + key + ']').forEach(function (el) { el.textContent = text; }); };
    say('game', name || 'Pick a game');
    say('about', who + ' · ' + time);
    say('dock', name ? 'Log ' + name + ' · ' + who + ' · ' + d : 'Log it');
  end
end
init call update()
on change call update()"""

fun TagConsumer<*>.logForm(view: LogFormView, me: CurrentPlayer, values: Parameters, error: String?) =
    form(action = "/gamedays/${view.gameDay.id}/log", method = FormMethod.post, classes = "gf-log") {
        id = "log-form"
        hx("post", "/gamedays/${view.gameDay.id}/log")
        hx("swap", "outerHTML")
        hyperscript(LOG_SUMMARY_SCRIPT)
        val summary = logSummary(view, values)
        div("gf-log__steps") {
            if (error != null) errorNotice(error)
            logStep(1, "The game") {
                if (view.games.isEmpty()) p("gf-muted") { +"Nothing on the shelf yet. Search for the game you played." }
                val chosenGame = values["game"] ?: view.games.firstOrNull()?.id?.toString()
                div("gf-tiles") { view.games.forEach { gameTileChoice(it, "game", checked = it.id.toString() == chosenGame) } }
                div("gf-log__search") {
                    h3("gf-log__sub") { +"Played something else?" }
                    // Enter would submit the whole log form from here.
                    searchField("Search BGG", "Search for the game you played", "/gamedays/${view.gameDay.id}/log/search", "#log-search-results") {
                        hyperscript("on keydown[key=='Enter'] halt the event")
                    }
                    consumer.logSearchResults(emptyList(), bggUnavailable = false)
                }
            }
            logStep(2, "Who played?") {
                val chosen = values.getAll("players")?.toSet() ?: setOf(me.id.toString())
                playerChips(view.gameDay.players, view.names, checked = { it.toString() in chosen })
            }
            logStep(3, "Roughly how long?") {
                segmented("duration", "Duration", DURATIONS.map { it.first }, checked = values["duration"] ?: "45m")
            }
        }
        aside("gf-log__summary") {
            p("gf-kicker") { +"You're logging" }
            h2("gf-log__game") { attributes["data-log"] = "game"; +(summary.game ?: "Pick a game") }
            p("gf-muted gf-log__about") { attributes["data-log"] = "about"; +summary.about }
            button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.LARGE, block = true)) { spinner(); +"Log it" }
            p("gf-hint") { +"Then one quick question about it." }
        }
    }

/** One numbered question of the form, with the same number disc as the login's "how it works". */
private fun FlowContent.logStep(number: Int, title: String, body: FlowContent.() -> Unit) = section("gf-log__step") {
    span("gf-log__num") { attributes["aria-hidden"] = "true"; +"$number" }
    h2("gf-log__title") { +title }
    div("gf-log__body") { body() }
}

/** Games found by the search above the form: more tiles in the same radio group. */
fun TagConsumer<*>.logSearchResults(games: List<Game>, bggUnavailable: Boolean) = div {
    id = "log-search-results"
    if (bggUnavailable) p("gf-small gf-muted") { +"BGG isn't answering right now, so these are only games we already know." }
    if (games.isNotEmpty()) div("gf-tiles") { games.forEach { gameTileChoice(it, "game", checked = false) } }
}

/** Step 2: one PLAYED comparison between what was just played and something else they played. */
data class LogCompareView(val gameDay: GameDaySummary, val played: Game, val other: Game, val players: Int, val minutes: String, val next: String)

fun HTML.logComparePage(view: LogCompareView, me: CurrentPlayer) = page(
    "One quick question",
    appBar = { appBarAction(view.next, "Skip") },
    mainClasses = "gf-wizard",
    site = SiteBar(me, Nav.GAME_DAYS),
) {
    wrap("gf-log__compare") {
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
}
