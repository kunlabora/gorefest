package gorefest.shell.web.datepoll

import gorefest.core.BggId
import gorefest.core.PlayerId
import gorefest.core.datepoll.DatePollSummary
import gorefest.core.datepoll.MAX_POLL_DATES
import gorefest.core.datepoll.MAX_POLL_GAMES
import gorefest.core.datepoll.MIN_POLL_DATES
import gorefest.core.game.Game
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.MAX_WISHES
import gorefest.core.gamenight.GameNightSummary
import gorefest.core.gameday.MAX_PLACE_LENGTH
import gorefest.core.gameday.MAX_TITLE_LENGTH
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import io.ktor.http.Parameters
import kotlinx.html.*
import java.time.LocalDate

/** The form that starts a poll: a title, a place and a few dates (with "add another"). */
/**
 * The games the form offers when it asks about games: [suggested] is the host's own collection, likely favourites first;
 * [found] are games ticked earlier from a search, kept when the form comes back with an error.
 */
data class PollFormGames(val suggested: List<Game>, val found: List<Game>, val ticked: Set<BggId>, val linked: Boolean, val tasteKnown: Boolean = false) {
    companion object {
        val none = PollFormGames(emptyList(), emptyList(), emptySet(), linked = false)
    }
}

/** How many of the host's likeliest favourites show before "All your games". */
private const val SUGGESTED_SHOWN = 8

fun HTML.datePollFormPage(site: SiteBar, values: Parameters = Parameters.Empty, picks: PollFormGames = PollFormGames.none) =
    page("Find a date", site = site) {
        wrap {
            split("gf-split--pollform") {
                div("gf-split__copy") {
                    crumb("/", "All game days")
                    p("gf-kicker") { +"Find a date" }
                    h1("gf-h1 gf-h1--landing") { +"Find a date" }
                    p("gf-lede gf-muted") { +"Pick a few dates that could work, share the link, and see who can make which. You choose the winner, and the evening is set up for you." }
                }
                consumer.datePollForm(values = values, picks = picks)
            }
        }
    }

fun TagConsumer<*>.datePollForm(error: String? = null, values: Parameters = Parameters.Empty, picks: PollFormGames = PollFormGames.none) =
    form(action = "/polls", method = FormMethod.post, classes = "gf-poll-form") {
        id = "poll-form"
        hx("post", "/polls")
        hx("swap", "outerHTML")
        if (error != null) errorNotice(error)
        field("title", "Title", hint = "Whatever your group calls it.") {
            input(InputType.text, name = "title", classes = "gf-input") { id = "title"; placeholder = "Gorefest #7"; required = true; maxLength = MAX_TITLE_LENGTH.toString(); value = values["title"].orEmpty() }
        }
        field("place", "Where", hint = "Only people who have joined see it. Leave out the house number if you like.") {
            input(InputType.text, name = "place", classes = "gf-input") { id = "place"; maxLength = MAX_PLACE_LENGTH.toString(); value = values["place"].orEmpty() }
        }
        div("gf-section") {
            h2("gf-h2") { +"Which dates could work?" }
            val given = values.getAll("dates").orEmpty()
            div("gf-stack") {
                id = "poll-dates"
                (given + List((MIN_POLL_DATES + 1 - given.size).coerceAtLeast(0)) { "" }).forEach { dateField(it) }
            }
            button(type = ButtonType.button, classes = buttonClasses(ButtonStyle.GHOST, ButtonSize.SMALL)) {
                hx("get", "/polls/new/date")
                hx("target", "#poll-dates")
                hx("swap", "beforeend")
                // The server only allows MAX_POLL_DATES: stop offering more once the form has that many fields.
                disabled = given.size >= MAX_POLL_DATES
                hyperscript("on htmx:afterRequest if #poll-dates.querySelectorAll('input').length >= $MAX_POLL_DATES set my.disabled to true")
                icon("plus")
                +"Add another date"
            }
        }
        label("gf-row gf-poll-form__games") {
            span("gf-row__main") {
                span("gf-row__title") { +"Ask about games as well" }
                span("gf-row__sub") { +"Put a few games to the group and see who'd play what and who can bring it. The date you pick makes a small game night instead of a game day." }
            }
            input(InputType.checkBox, classes = "gf-switch", name = "games") {
                attributes["role"] = "switch"
                value = "true"
                attributes["aria-label"] = "Ask about games as well"
                checked = values["games"] == "true"
            }
        }
        pollFormGamePicker(picks)
        browserTimeZone()
        primaryButton("Start the poll")
    }

/** Shown once "Ask about games as well" is on (CSS, no round trip): the host's games to tick, likely favourites first, and a BGG search. */
private fun FlowContent.pollFormGamePicker(picks: PollFormGames) = div("gf-poll-form__gamepick") {
    // Attributes go before any child (the HTML streams). Stops a 13th tick here, so the limit isn't only news after "Start the poll" (BUG-034).
    hyperscript("on change[target.name == 'game' and target.checked] if (<input[name='game']:checked/> in me).length > $MAX_POLL_GAMES set target.checked to false then put 'That makes more than $MAX_POLL_GAMES: untick one first.' into #poll-form-games-limit else put '' into #poll-form-games-limit end")
    h2("gf-h2") { +"Which games could you play?" }
    p("gf-small gf-muted") { +"Tick a few to put to the group, up to $MAX_POLL_GAMES. You can add more once the poll is up." }
    p("gf-small gf-error") { id = "poll-form-games-limit"; attributes["aria-live"] = "polite" }
    val (shown, rest) = picks.suggested.withIndex().partition { (i, game) -> i < SUGGESTED_SHOWN || game.id in picks.ticked }.let { (a, b) -> a.map { it.value } to b.map { it.value } }
    // Without any taste answers the order is only alphabetical: don't claim favourites.
    if (picks.suggested.isNotEmpty()) h3("gf-label") { +if (picks.tasteKnown) "Your games, likely favourites first" else "Your games" }
    else p("gf-small") {
        +if (picks.linked) "Your collection is still on its way from BGG. Search for games below." else "Link your BGG account to pick from your own games. Or search below. "
        if (!picks.linked) a("/bgg?returnTo=%2Fpolls%2Fnew", classes = "gf-link") { +"Link BGG" }
    }
    div("gf-tiles") {
        id = "poll-form-picked"
        picks.found.forEach { gameTileChoice(it, "game", checked = true, multiple = true) }
        shown.forEach { gameTileChoice(it, "game", checked = it.id in picks.ticked, multiple = true) }
    }
    if (rest.isNotEmpty()) details("gf-pollgames__pool") {
        summary { +"More of your games (${rest.size})" }
        div("gf-tiles") { rest.forEach { gameTileChoice(it, "game", checked = false, multiple = true) } }
    }
    h3("gf-label") { +"Something else?" }
    // Enter would submit the whole form from here.
    searchField("Search BGG", "Search BGG for a game to put to the group", "/polls/new/games/search", "#poll-form-search-results") {
        hyperscript("on keydown[key=='Enter'] halt the event")
    }
    consumer.pollFormSearchResults(emptyList(), bggUnavailable = false, nothingFor = null)
}

/** BGG results on the poll form: ticking one moves it up with the picked games, so the next search can't wipe it. */
fun TagConsumer<*>.pollFormSearchResults(games: List<Game>, bggUnavailable: Boolean, nothingFor: String?, inYourGames: List<String> = emptyList()) = div {
    id = "poll-form-search-results"
    hyperscript("on change if event.target.checked put event.target.closest('label') at the end of #poll-form-picked end")
    if (bggUnavailable) p("gf-small gf-muted") { +"BGG isn't answering right now, so these are only games we already know." }
    if (inYourGames.isNotEmpty()) p("gf-small gf-muted") { +"${inYourGames.joinAnd()} ${if (inYourGames.size == 1) "is" else "are"} in your games above." }
    if (games.isNotEmpty()) div("gf-tiles") { games.forEach { gameTileChoice(it, "game", checked = false, multiple = true) } }
    else if (nothingFor != null) p("gf-small gf-muted gf-search-empty") { +"Nothing found for \u201c$nothingFor\u201d. Try another spelling." }
}

/** One more date input, for the "add another date" button. */
fun TagConsumer<*>.dateField(value: String = "") = input(InputType.date, name = "dates", classes = "gf-input") {
    attributes["aria-label"] = "Date"
    this.value = value
}

/** Everything the poll page shows. [gameDay] (or [gameNight], for a poll with games) is what the pick made, once there is one. */
data class DatePollView(
    val poll: DatePollSummary,
    val names: Map<PlayerId, String>,
    val viewer: CurrentPlayer,
    val today: LocalDate,
    val inviteLink: String,
    val gameDay: GameDaySummary?,
    val gameNight: GameNightSummary? = null,
    val games: PollGamesView? = null,
    /** A refused change shown at the top, for a poll with no games section to show it in. */
    val error: String? = null,
) {
    val me: PlayerId get() = viewer.id
    val gameNames: Map<BggId, String> get() = games?.rows.orEmpty().associate { it.game.id to it.game.name }
}

/** One of the poll's games as the viewer sees it. [myAnswer] and [myBring] are null while unanswered. */
data class PollGameRow(
    val game: Game,
    val yes: List<PlayerId>,
    val no: List<PlayerId>,
    val bringers: List<PlayerId>,
    val myAnswer: Boolean?,
    val myBring: Boolean?,
    val inMyCollection: Boolean,
)

/** The games part of a poll (U11). [pool] is what the host can still add from their own collection (empty for anyone else). */
data class PollGamesView(val rows: List<PollGameRow>, val pool: List<Game>, val yesLeft: Int, val error: String? = null)

fun HTML.datePollPage(view: DatePollView) {
    val poll = view.poll
    val host = view.names[poll.host]
    // gf-landing: the games band runs edge to edge, like the hub's shelf.
    page(poll.title, site = SiteBar(view.viewer, Nav.GAME_DAYS), mainClasses = "gf-landing") {
        wrap {
            split("gf-split--poll") {
                div("gf-split__copy gf-poll-copy") {
                    crumb("/", "All game days")
                    p("gf-kicker") {
                        +when {
                            poll.hostGone -> "Poll over"
                            poll.picked != null -> "Date picked · ${poll.picked.date.short()}"
                            else -> "${if (poll.asksAboutGames) "Finding a date and games" else "Finding a date"} · ${poll.answers.size} of ${poll.participants.size} answered"
                        }
                    }
                    h1("gf-h1 gf-h1--landing") { +poll.title }
                    if (poll.place.isNotBlank()) ul("gf-facts") { li { icon("pin"); +poll.place } }
                    p("gf-lede gf-muted") {
                        +when {
                            poll.asksAboutGames && view.me == poll.host -> "Add a few games, then pick the date that suits most people and their games. Everyone who can make it gets the game night."
                            poll.asksAboutGames -> "Tick every date you can make and say which games you'd play. ${host ?: "The host"} picks the date and the game night appears."
                            view.me == poll.host -> "Pick the date that suits most people. Everyone who can make it gets the game day."
                            else -> "Tick every date you can make. ${host ?: "The host"} picks the winner and the game day appears."
                        }
                    }
                    if (view.error != null) errorNotice(view.error)
                    if (poll.hostGone) notice("alert") { +"This poll is over: the host is no longer on Boardfest, so nobody can pick a date." }
                    poll.picked?.let { picked ->
                        notice("check", NoticeKind.GOOD) { +"The host picked "; strong { +picked.date.full() }; +"." }
                        if (poll.asksAboutGames) {
                            if (view.gameNight != null && view.me in view.gameNight.players) linkButton("/gamenights/${view.gameNight.id}", "Open ${view.gameNight.title}", ButtonStyle.PRIMARY)
                            else p("gf-muted") { +"You weren't down as available that day, so you're not on the game night. Ask ${host ?: "the host"} if you can make it after all." }
                        } else if (view.gameDay?.cancelled == true) p("gf-muted") { +"${view.gameDay.title} was cancelled by the host." }
                        else if (view.gameDay != null && view.me in view.gameDay.players) linkButton("/gamedays/${view.gameDay.id}", "Open ${view.gameDay.title}", ButtonStyle.PRIMARY)
                        else p("gf-muted") { +"You weren't down as available that day, so you're not on the list. Ask ${host ?: "the host"} for the game day link if you can make it after all." }
                    }
                    if (poll.open) form(classes = "gf-section") {
                        id = "answer-form"
                        hx("post", "/polls/${poll.id}/answer")
                        hx("trigger", "change")
                        hx("target", "#poll-results")
                        hx("swap", "outerHTML")
                        val mine = poll.answers[view.me].orEmpty()
                        h2("gf-label") { +"Which dates can you make?" }
                        dateChips(poll.dates, checked = { it in mine }, disabled = { it < view.today }, legend = "Dates you can make")
                        p("gf-small gf-muted") { +"Tick every date that suits you. It saves as you go." }
                    }
                    view.games?.let { games -> gamesSignpost(view, games) }
                    if (poll.open) {
                        p("gf-label") { +"Share the poll" }
                        copyBox(view.inviteLink, small = true)
                        p("gf-small gf-muted") { +"Post it in your group chat so they can answer too." }
                    }
                }
                consumer.datePollResults(view)
            }
        }
        view.games?.let { games -> band(sunk = true) { pollGames(view, games) } }
    }
}

/** In the copy column, right under the dates: the poll's second question, so nobody misses the games further down. */
private fun FlowContent.gamesSignpost(view: DatePollView, games: PollGamesView) = div("gf-card gf-poll-signpost") {
    val poll = view.poll
    val isHost = view.me == poll.host
    val answered = games.rows.count { it.myAnswer != null }
    h2("gf-label") { +"Which games would you play?" }
    p("gf-small gf-muted") {
        +when {
            games.rows.isEmpty() && isHost -> "No games yet. Add a few from your shelves or BGG."
            games.rows.isEmpty() -> "${view.names[poll.host] ?: "The host"} hasn't added any games yet."
            !poll.open -> "${games.rows.size.of("game")} in the poll."
            else -> "${games.rows.size.of("game")} to choose from · you answered $answered · ${games.yesLeft.of("yes", "yeses")} left"
        }
    }
    if (poll.open && games.rows.isEmpty() && isHost) addGameButton("Add games")
    else if (poll.open && games.rows.isNotEmpty()) a("#poll-games", classes = buttonClasses(ButtonStyle.SECONDARY, ButtonSize.SMALL)) {
        +if (answered < games.rows.size) "Answer the games" else "See the games"
    }
}

/** The poll's games: yes or no for each, who brings it, and for the host a way to add more (U11). */
private fun FlowContent.pollGames(view: DatePollView, games: PollGamesView) = section("gf-stack gf-pollgames") {
    id = "poll-games"
    val poll = view.poll
    val isHost = view.me == poll.host
    div("gf-sechead") {
        h2("gf-h2 gf-h2--section") { +"Which games would you play?"; if (games.rows.isNotEmpty()) +" · ${games.rows.size}" }
        // With no games yet the search is right below the empty state: a second way there would only repeat it.
        if (isHost && poll.open && games.rows.isNotEmpty()) addGameButton("Add a game")
    }
    if (poll.open) p("gf-muted") {
        +"Up to $MAX_WISHES games you'd play: each one counts as a heart on the night. Have one? Say you'll bring it."
    }
    if (games.error != null) errorNotice(games.error)
    if (games.rows.isEmpty()) emptyState("No games yet", if (isHost) "Pick a few of yours below, or search BGG for anything." else "${view.names[poll.host] ?: "The host"} hasn't added any games yet.")
    div("gf-pollgames__list") { games.rows.forEach { pollGameCard(view, it) } }
    if (isHost && poll.open) pollGameAdder(view, games)
}

private fun FlowContent.pollGameCard(view: DatePollView, row: PollGameRow) = article("gf-card gf-pollgame") {
    id = "poll-game-${row.game.id}"
    val poll = view.poll
    val base = "/polls/${poll.id}/games/${row.game.id}"
    fun who(ids: List<PlayerId>) = ids.map { if (it == view.me) "you" else view.names[it] ?: SOMEONE }
    gameRow(row.game) {
        if (view.me == poll.host && poll.open) form(action = "$base/remove", method = FormMethod.post) {
            confirmOnSubmit("Take ${row.game.name} off the poll? Its answers go with it.")
            button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.GHOST, ButtonSize.SMALL)) { attributes["aria-label"] = "Take ${row.game.name} off the poll"; +"Take off" }
        }
    }
    ul("gf-pollgame__facts") {
        li {
            icon("heart")
            +if (row.yes.isEmpty()) "Nobody said yes yet" else "${who(row.yes).joinAnd().replaceFirstChar(Char::uppercase)} ${if (row.yes.size == 1 && row.yes.single() != view.me) "wants" else "want"} to play it"
            if (view.me == poll.host && row.no.isNotEmpty()) span("gf-muted") { +" · ${who(row.no).joinAnd()} said no" }
        }
        li("gf-pollgame__bring") {
            if (row.bringers.isEmpty()) span("gf-tag gf-tag--accent") { +"Nobody brings it yet" }
            else {
                icon("check")
                +"${who(row.bringers).first().replaceFirstChar(Char::uppercase)} ${if (row.bringers.first() == view.me) "bring" else "brings"} it"
                if (row.bringers.size > 1) span("gf-muted") { +" · ${(row.bringers.size - 1).of("other")} can too" }
            }
        }
    }
    if (!poll.open) return@article
    if (row.inMyCollection && row.myBring == null) notice("spark", id = "bring-prompt-${row.game.id}") {
        +"You have ${row.game.name}. Bring it if we play?"
    }
    div("gf-pollgame__actions") {
        form(action = "$base/answer", method = FormMethod.post, classes = "gf-chips") {
            chipButton("yes", "true", pressed = row.myAnswer == true, icon = "heart", label = "I'd play this", name = "I'd play ${row.game.name}")
            chipButton("yes", "false", pressed = row.myAnswer == false, label = "Not for me", name = "${row.game.name}: not for me")
        }
        form(action = "$base/bring", method = FormMethod.post, classes = "gf-chips") {
            // Pressed means "I bring it": pressing again takes it back.
            chipButton("bringing", (row.myBring != true).toString(), pressed = row.myBring == true, icon = "check", label = "I'll bring it", name = "I'll bring ${row.game.name}")
            if (row.inMyCollection && row.myBring == null) button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.GHOST, ButtonSize.SMALL)) {
                name = "bringing"; value = "false"; attributes["aria-label"] = "Not bringing ${row.game.name} this time"; +"Not this time"
            }
        }
    }
}

private const val ADD_GAME_SEARCH = "add-game-q"

/** Takes the host to the game search and puts the cursor in it: a jump alone does nothing when the search is already on screen. */
private fun FlowContent.addGameButton(label: String) = a("#$ADD_GAME_SEARCH", classes = buttonClasses(ButtonStyle.SECONDARY, ButtonSize.SMALL)) {
    hyperscript("on click halt the event then call #$ADD_GAME_SEARCH.scrollIntoView({block: 'center'}) then call #$ADD_GAME_SEARCH.focus()")
    icon("plus")
    +label
}

/**
 * A toggle chip that submits [field]=[value]; [pressed] shows the answer you gave, like the bring page's heart. [name] is what a screen
 * reader says: the game is in it, since every card has the same chips (BUG-035).
 */
private fun FlowContent.chipButton(field: String, value: String, pressed: Boolean, label: String, icon: String? = null, name: String = label) =
    button(type = ButtonType.submit, classes = "gf-chip gf-chip--text" + if (icon != null && icon != "heart") " gf-chip--outline-icon" else "") {
        this.name = field
        this.value = value
        attributes["aria-label"] = name
        attributes["aria-pressed"] = pressed.toString()
        if (icon != null) icon(icon)
        span { +label }
    }

/** The host's way to add games: their own collection, and BGG search for anything else. */
private fun FlowContent.pollGameAdder(view: DatePollView, games: PollGamesView) = div("gf-card gf-pollgames__add") {
    id = "add-game"
    h3("gf-h3") { +"Add a game" }
    searchField("Search your games and BGG", "Search for a game to add", "/polls/${view.poll.id}/games/search", "#poll-game-search") { id = ADD_GAME_SEARCH }
    div { id = "poll-game-search" }
    if (games.pool.isNotEmpty()) details("gf-pollgames__pool") {
        summary { +"Your games (${games.pool.size})" }
        ul("gf-list") { games.pool.forEach { game -> li { addRow(view.poll.id.toString(), game, "In your collection") } } }
    }
}

private fun FlowContent.addRow(poll: String, game: Game, meta: String) =
    gameRow(game, meta = meta) {
        form(action = "/polls/$poll/games", method = FormMethod.post) {
            hiddenInput("game", game.id.toString())
            button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.SECONDARY, ButtonSize.SMALL)) { icon("plus"); +"Add" }
        }
    }

/** What the host's search found: their own games first, then BGG. */
fun TagConsumer<*>.pollGameSearchResults(poll: DatePollSummary, mine: List<Game>, bgg: List<Game>, bggUnavailable: Boolean, nothingFor: String?) = div {
    id = "poll-game-search"
    if (bggUnavailable) p("gf-small gf-muted") { +"BGG isn't answering right now, so these are only games we already know." }
    if (mine.isEmpty() && bgg.isEmpty()) {
        if (nothingFor != null) p("gf-small gf-muted gf-search-empty") { +"Nothing found for \u201c$nothingFor\u201d. Try another spelling." }
        return@div
    }
    ul("gf-list") {
        mine.forEach { li { addRow(poll.id.toString(), it, "In your collection") } }
        bgg.forEach { li { addRow(poll.id.toString(), it, "From BGG: whoever has it can say they'll bring it") } }
    }
}


/** One card per date: how many can, who can't, and (for the host) the button to pick it. This is what answering swaps. */
fun TagConsumer<*>.datePollResults(view: DatePollView, error: String? = null) = div("gf-opts") {
    id = "poll-results"
    val poll = view.poll
    val isHost = view.me == poll.host
    val best = poll.best().takeIf { poll.open }
    h2("gf-vh") { +"How it looks" }
    if (error != null) errorNotice(error)
    poll.tallies().forEach { tally ->
        val declined = poll.answers.filter { (_, dates) -> tally.date !in dates }.keys.map { view.names[it] ?: SOMEONE }
        val unanswered = poll.waitingOn.size
        article("gf-card gf-opt" + if (tally.date == best) " gf-opt--best" else "") {
            div("gf-opt__top") {
                span("gf-date") {
                    span("gf-date__month") { +tally.date.format(MONTH) }
                    +" "
                    span("gf-date__day") { +tally.date.dayOfMonth.toString() }
                }
                div {
                    h3("gf-opt__title") { +tally.date.full() }
                    if (tally.date == best) span("gf-tag gf-tag--brand") { +"Most can come" }
                    if (poll.picked?.date == tally.date) span("gf-tag gf-tag--olive") { +"Picked" }
                }
            }
            div("gf-meter") {
                attributes["aria-hidden"] = "true"
                poll.participants.indices.forEach { idx -> i(if (idx < tally.available.size) "is-yes" else null) {} }
            }
            div("gf-who") {
                if (tally.available.isNotEmpty()) avatarStack(tally.available, view.names)
                span {
                    +when {
                        tally.available.isNotEmpty() -> "${tally.available.size} can"
                        declined.isNotEmpty() -> "Nobody can"
                        else -> "Nobody yet"
                    }
                    if (declined.isNotEmpty()) +" · ${declined.joinAnd()} can't"
                    if (poll.open && unanswered > 0 && tally.available.isNotEmpty()) +" · $unanswered haven't answered"
                }
            }
            if (poll.asksAboutGames && poll.proposals.isNotEmpty()) {
                // Only what this date changes: a game nobody offered to bring is missing on every date, and its own card says so.
                val missing = poll.planFor(tally.date).missing
                val lost = missing.filter { poll.bringers(it).isNotEmpty() }
                if (missing.isEmpty()) p("gf-small gf-opt__games") { icon("check"); +"Someone brings every game" }
                lost.forEach { game ->
                    p("gf-small gf-opt__games gf-opt__games--missing") {
                        +"No ${view.gameNames[game] ?: "game"}: ${poll.bringers(game).map { view.names[it] ?: SOMEONE }.joinAnd()} can't come"
                    }
                }
            }
            if (isHost && poll.open && tally.date >= view.today) button(
                type = ButtonType.button,
                classes = buttonClasses(if (tally.date == best) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, block = true),
            ) {
                hx("post", "/polls/${poll.id}/pick")
                hx("vals", """{"date": "${tally.date}"}""")
                hx("target", "#poll-results")
                hx("swap", "outerHTML")
                hx("confirm", "Pick ${tally.date.short()}? The poll closes and the ${if (poll.asksAboutGames) "game night" else "game day"} is created.")
                +if (tally.date == best) "Pick this date" else "Pick this one"
            }
        }
    }
    val waiting = poll.waitingOn.map { view.names[it] ?: SOMEONE }
    if (poll.open && waiting.isNotEmpty()) p("gf-small gf-muted") { +"Still to answer: ${waiting.joinToString(", ")}." }
}

private val MONTH = java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.ENGLISH)

/** The invite landing: what the poll is about, and a way in. */
data class PollInviteView(val poll: DatePollSummary, val names: Map<PlayerId, String>, val loggedIn: Boolean, val path: String)

fun HTML.pollInvitePage(view: PollInviteView) {
    val poll = view.poll
    val host = view.names[poll.host] ?: SOMEONE
    page(
        "Find a date: ${poll.title}",
        appBar = { wordmark() },
        mainClasses = "gf-landing",
        wide = true,
        head = {
            openGraph("og:title", "$host is finding a date: ${poll.title}")
            openGraph("og:description", poll.dates.joinToString(" · ") { it.short() })
        },
    ) {
        wrap {
            split {
                div("gf-split__copy") {
                    inviterLine(poll.host, view.names[poll.host], if (poll.asksAboutGames) "wants to find a date and games for a game night" else "wants to find a date for a game day")
                    h1("gf-h1 gf-h1--landing") { +poll.title }
                    ul("gf-facts") {
                        li { icon("calendar"); +"${poll.dates.size.of("date")} to choose from" }
                        li { icon("users"); avatarStack(poll.participants, view.names); +"${poll.participants.size.of("person", "people")} in" }
                        if (poll.asksAboutGames) li { icon("dice"); +"and which games to play" }
                    }
                    p("gf-small gf-muted") {
                        +if (poll.asksAboutGames) "Tick the dates you can make and say which games you'd play. $host picks the date and the game night appears."
                        else "Tick the dates you can make. ${host} picks the winner and the game day appears."
                    }
                    div("gf-narrow") { joinForm(view.path, "Join and answer", view.loggedIn) }
                }
                ul("gf-list") {
                    poll.dates.forEach { date -> li("gf-row") { span("gf-row__main") { span("gf-row__title") { +date.short() } } } }
                }
            }
        }
    }
}

/** A poll on the home page. */
fun FlowContent.pollCard(poll: DatePollSummary) = dayCard(
    href = "/polls/${poll.id}", month = "Poll", day = "${poll.dates.size}", title = poll.title,
    meta = "${poll.answers.size} of ${poll.participants.size} answered",
)
