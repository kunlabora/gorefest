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
        actionButton(
            logSummary(view, Parameters.Empty).dock, ButtonStyle.PRIMARY, ButtonSize.LARGE, block = true, submit = true, spinner = true,
            labelSpan = { attributes["data-log"] = "dock" },
        ) { attributes["form"] = "log-form" }
    },
) {
    wrap {
        div("gf-log__head") {
            crumb("/gamedays/${view.gameDay.id}", view.gameDay.title)
            p("gf-kicker") { +checkpoint }
            pageHeading("What did you play?")
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
        if (error != null) errorNotice(error)
        stepper("gf-stepper--sections") {
            formStep(1, "The game") {
                if (view.games.isEmpty()) p("gf-muted") { +"Nothing on the shelf yet. Search for the game you played." }
                val chosenGame = values["game"] ?: view.games.firstOrNull()?.id?.toString()
                gameTiles("Which game did you play?", "game") { view.games.forEach { gameTileChoice(it, checked = it.id.toString() == chosenGame) } }
                div("gf-log__search") {
                    heading(Level.H3, "gf-log__sub") { +"Played something else?" }
                    // Enter would submit the whole log form from here.
                    searchField("Search BGG", "Search for the game you played", "/gamedays/${view.gameDay.id}/log/search", "#log-search-results") {
                        hyperscript("on keydown[key=='Enter'] halt the event")
                    }
                    consumer.logSearchResults(emptyList(), bggUnavailable = false)
                }
            }
            formStep(2, "Who played?") {
                val chosen = values.getAll("players")?.toSet() ?: setOf(me.id.toString())
                playerChips(view.gameDay.players, view.names, checked = { it.toString() in chosen })
            }
            formStep(3, "Roughly how long?") {
                segmented("duration", "Duration", DURATIONS.map { it.first }, checked = values["duration"] ?: "45m")
            }
        }
        aside("gf-log__summary") {
            p("gf-kicker") { +"You're logging" }
            heading(Level.H2, "gf-log__game") { attributes["data-log"] = "game"; +(summary.game ?: "Pick a game") }
            p("gf-muted gf-log__about") { attributes["data-log"] = "about"; +summary.about }
            actionButton("Log it", ButtonStyle.PRIMARY, ButtonSize.LARGE, block = true, submit = true, spinner = true)
            p("gf-hint") { +"Then one quick question about it." }
        }
    }

/** Games found by the search above the form: more tiles in the same radio group. */
fun TagConsumer<*>.logSearchResults(
    games: List<Game>, bggUnavailable: Boolean, nothingFor: String? = null, id: String = "log-search-results", question: String = "Which game did you play?",
) = div {
    this.id = id
    announce(searchAnnouncement(games.size, nothingFor))
    if (bggUnavailable) p("gf-small gf-muted") { +"BGG isn't answering right now, so these are only games we already know." }
    if (games.isNotEmpty()) gameTiles(question, "game") { games.forEach { gameTileChoice(it, checked = false) } }
    else if (nothingFor != null) p("gf-small gf-muted gf-search-empty") { +"Nothing found for \u201c$nothingFor\u201d. Try another spelling." }
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
            heading(Level.H1, "gf-h1") { +"Which did you enjoy more?" }
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

/** Where the watched page's search puts its tiles. */
const val WATCHED_RESULTS = "watched-search-results"

/** What the watched form's tiles answer, for the shelf and for a search alike. */
const val WATCHED_QUESTION = "Which game did you watch?"

/** Watched a table play (U9): which game. The whole shelf, minus what you've played. */
data class WatchedView(val gameDay: GameDaySummary, val games: List<Game>)

fun HTML.watchedPage(view: WatchedView, checkpoint: String, me: CurrentPlayer) = page("What did you watch?", site = SiteBar(me, Nav.GAME_DAYS)) {
    wrap {
        div("gf-log__head") {
            crumb("/gamedays/${view.gameDay.id}", view.gameDay.title)
            p("gf-kicker") { +checkpoint }
            pageHeading("What did you watch?")
            p("gf-lede") { +"Seen a table play something you haven't? Pick it, and one question tells us what you made of it. It counts as if you'd played it." }
        }
        form(action = "/gamedays/${view.gameDay.id}/watched/compare", method = FormMethod.get, classes = "gf-stack") {
            id = "watched-form"
            if (view.games.isEmpty()) p("gf-muted") { +"Nothing on the shelf you haven't played. Search for the game you watched." }
            gameTiles(WATCHED_QUESTION, "game") { view.games.forEachIndexed { i, game -> gameTileChoice(game, checked = i == 0) } }
            div("gf-log__search") {
                heading(Level.H2, "gf-log__sub") { +"Watched something else?" }
                // Enter would submit the form from here.
                searchField("Search BGG", "Search for the game you watched", "/gamedays/${view.gameDay.id}/watched/search", "#$WATCHED_RESULTS") {
                    hyperscript("on keydown[key=='Enter'] halt the event")
                }
                consumer.logSearchResults(emptyList(), bggUnavailable = false, id = WATCHED_RESULTS, question = WATCHED_QUESTION)
            }
            div("gf-continue") { primaryButton("Next") }
        }
    }
}

/** One WATCHED comparison: the game you watched against one you've played. */
data class WatchedCompareView(val gameDay: GameDaySummary, val watched: Game, val other: Game)

fun HTML.watchedComparePage(view: WatchedCompareView, me: CurrentPlayer) = page(
    "One quick question",
    appBar = { appBarAction("/gamedays/${view.gameDay.id}", "Skip") },
    mainClasses = "gf-wizard",
    site = SiteBar(me, Nav.GAME_DAYS),
) {
    wrap("gf-log__compare") {
        div("gf-stack") {
            p("gf-kicker") { +"You watched ${view.watched.name}" }
            heading(Level.H1, "gf-h1") { +"Now that you've seen it, which would you rather play?" }
        }
        form(action = "/gamedays/${view.gameDay.id}/watched/compare", method = FormMethod.post, classes = "gf-wizard__form") {
            hiddenInput("a", view.watched.id.toString())
            hiddenInput("b", view.other.id.toString())
            duel(view.watched, view.other)
            duelAnswers(firstLabel = "Can't tell yet")
        }
    }
}

/** Watched a game, but there's nothing you've played to hold it against yet. */
fun HTML.watchedNothingPage(gameDay: GameDaySummary, watched: Game, me: CurrentPlayer) = page("Nothing to compare yet", site = SiteBar(me, Nav.GAME_DAYS)) {
    wrap {
        crumb("/gamedays/${gameDay.id}", gameDay.title)
        emptyState("Nothing to compare yet", "We'd ask you to hold ${watched.name} up against a game you've played, and we don't know any yet. Log a game you played, or do a taste round first.", level = Level.H1)
    }
}

