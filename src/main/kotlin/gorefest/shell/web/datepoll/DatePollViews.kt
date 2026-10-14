package gorefest.shell.web.datepoll

import gorefest.core.BggId
import gorefest.core.PlayerId
import gorefest.core.datepoll.DatePollSummary
import gorefest.core.datepoll.MAX_POLL_DATES
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
fun HTML.datePollFormPage(site: SiteBar, values: Parameters = Parameters.Empty) =
    page("Find a date", site = site) {
        wrap {
            split("gf-split--pollform") {
                div("gf-split__copy") {
                    crumb("/", "All game days")
                    p("gf-kicker") { +"Find a date" }
                    h1("gf-h1 gf-h1--landing") { +"Find a date" }
                    p("gf-lede gf-muted") { +"Pick a few dates that could work, share the link, and see who can make which. You choose the winner; the game day is made for you." }
                }
                consumer.datePollForm(values = values)
            }
        }
    }

fun TagConsumer<*>.datePollForm(error: String? = null, values: Parameters = Parameters.Empty) =
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
        browserTimeZone()
        primaryButton("Start the poll")
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

/** The games part of a poll (U11). [pool] is what the host can add from people's collections (empty for anyone else). */
data class PollGamesView(val rows: List<PollGameRow>, val pool: List<Pair<Game, List<PlayerId>>>, val yesLeft: Int, val error: String? = null)

fun HTML.datePollPage(view: DatePollView) {
    val poll = view.poll
    val host = view.names[poll.host]
    page(poll.title, site = SiteBar(view.viewer, Nav.GAME_DAYS)) {
        wrap {
            split("gf-split--poll") {
                div("gf-split__copy gf-poll-copy") {
                    crumb("/", "All game days")
                    p("gf-kicker") { +"${if (poll.asksAboutGames) "Finding a date and games" else "Finding a date"} · ${poll.answers.size} of ${poll.participants.size} answered" }
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

/** The poll's games: yes or no for each, who brings it, and for the host a way to add more (U11). */
private fun FlowContent.pollGames(view: DatePollView, games: PollGamesView) = section("gf-stack gf-pollgames") {
    id = "poll-games"
    val poll = view.poll
    val isHost = view.me == poll.host
    h2("gf-h2 gf-h2--section") { +"Which games would you play?" }
    if (poll.open) p("gf-muted") {
        +"Say yes to up to $MAX_WISHES: each yes counts as a heart on the night. Have one of these? Say you'll bring it, so it's there."
        if (games.yesLeft < MAX_WISHES) +" You have ${games.yesLeft.of("yes", "yeses")} left."
    }
    if (games.error != null) errorNotice(games.error)
    if (games.rows.isEmpty()) emptyState("No games yet", if (isHost) "Add a few below: from your shelves, your friends' as they join, or any game on BGG." else "${view.names[poll.host] ?: "The host"} hasn't added any games yet.")
    div("gf-pollgames__list") { games.rows.forEach { pollGameCard(view, it) } }
    if (isHost && poll.open) pollGameAdder(view, games)
}

private fun FlowContent.pollGameCard(view: DatePollView, row: PollGameRow) = article("gf-card gf-pollgame") {
    id = "poll-game-${row.game.id}"
    val poll = view.poll
    val base = "/polls/${poll.id}/games/${row.game.id}"
    fun who(ids: List<PlayerId>) = ids.map { if (it == view.me) "you" else view.names[it] ?: "someone" }
    gameRow(row.game)
    p("gf-small") {
        +if (row.yes.isEmpty()) "Nobody said yes yet" else "${who(row.yes).joinAnd().replaceFirstChar(Char::uppercase)} ${if (row.yes.size == 1 && row.yes.single() != view.me) "wants" else "want"} to play it"
        if (view.me == poll.host && row.no.isNotEmpty()) span("gf-muted") { +" · ${who(row.no).joinAnd().replaceFirstChar(Char::uppercase)} said no" }
    }
    p("gf-small gf-pollgame__bring") {
        if (row.bringers.isEmpty()) span("gf-tag gf-tag--accent") { +"Nobody can bring it yet" }
        else { icon("check"); +"${who(row.bringers).first().replaceFirstChar(Char::uppercase)} ${if (row.bringers.first() == view.me) "bring" else "brings"} it" ; if (row.bringers.size > 1) span("gf-muted") { +" · ${(row.bringers.size - 1).of("other")} can too" } }
    }
    if (!poll.open) return@article
    if (row.inMyCollection && row.myBring == null) notice("spark", id = "bring-prompt-${row.game.id}") {
        +"You have ${row.game.name}. Bring it if we play?"
    }
    div("gf-pollgame__actions") {
        form(action = "$base/answer", method = FormMethod.post, classes = "gf-pollgame__answer") {
            button(type = ButtonType.submit, classes = buttonClasses(if (row.myAnswer == true) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, ButtonSize.SMALL)) {
                name = "yes"; value = "true"; attributes["aria-pressed"] = (row.myAnswer == true).toString()
                icon("heart"); +"Yes"
            }
            button(type = ButtonType.submit, classes = buttonClasses(if (row.myAnswer == false) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, ButtonSize.SMALL)) {
                name = "yes"; value = "false"; attributes["aria-pressed"] = (row.myAnswer == false).toString()
                +"No"
            }
        }
        form(action = "$base/bring", method = FormMethod.post, classes = "gf-pollgame__answer") {
            when {
                row.myBring == true -> button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.GHOST, ButtonSize.SMALL)) { name = "bringing"; value = "false"; +"Take back: I won't bring it" }
                row.inMyCollection && row.myBring == null -> {
                    button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.SMALL)) { name = "bringing"; value = "true"; +"I'll bring it" }
                    button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.GHOST, ButtonSize.SMALL)) { name = "bringing"; value = "false"; +"Not this time" }
                }
                else -> button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.GHOST, ButtonSize.SMALL)) { name = "bringing"; value = "true"; +"I'll bring it" }
            }
        }
        if (view.me == poll.host) form(action = "$base/remove", method = FormMethod.post, classes = "gf-pollgame__answer") {
            hyperscript("on submit if not confirm('Take ${row.game.name.replace("'", "")} off the poll? Its answers go with it.') halt the event")
            button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.GHOST, ButtonSize.SMALL)) { +"Take off" }
        }
    }
}

/** The host's way to add games: search everyone's collections and BGG, or browse the collections. */
private fun FlowContent.pollGameAdder(view: DatePollView, games: PollGamesView) = div("gf-card gf-pollgames__add") {
    h3("gf-h3") { +"Add a game" }
    searchField("Search your friends' games and BGG", "Search for a game to add", "/polls/${view.poll.id}/games/search", "#poll-game-search")
    div { id = "poll-game-search" }
    if (games.pool.isNotEmpty()) details("gf-pollgames__pool") {
        summary { +"Browse everyone's games (${games.pool.size})" }
        ul("gf-list") { games.pool.forEach { (game, owners) -> li { poolRow(view.poll.id.toString(), game, owners, view.names) } } }
    }
    p("gf-small gf-muted") { +"Only the collections of people who joined, and only of those who let others ask them to bring games." }
}

private fun FlowContent.poolRow(poll: String, game: Game, owners: List<PlayerId>, names: Map<PlayerId, String>) =
    gameRow(game, meta = "${owners.map { names[it] ?: "someone" }.joinAnd()} ${if (owners.size == 1) "has" else "have"} it") {
        form(action = "/polls/$poll/games", method = FormMethod.post) {
            hiddenInput("game", game.id.toString())
            button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.SECONDARY, ButtonSize.SMALL)) { icon("plus"); +"Add" }
        }
    }

/** What the host's search found: games in people's collections first, then BGG. */
fun TagConsumer<*>.pollGameSearchResults(
    poll: DatePollSummary,
    pool: List<Pair<Game, List<PlayerId>>>,
    names: Map<PlayerId, String>,
    bgg: List<Game>,
    bggUnavailable: Boolean,
    nothingFor: String?,
) = div {
    id = "poll-game-search"
    if (bggUnavailable) p("gf-small gf-muted") { +"BGG isn't answering right now, so these are only games we already know." }
    if (pool.isEmpty() && bgg.isEmpty()) {
        if (nothingFor != null) p("gf-small gf-muted gf-search-empty") { +"Nothing found for \u201c$nothingFor\u201d. Try another spelling." }
        return@div
    }
    ul("gf-list") {
        pool.forEach { (game, owners) -> li { poolRow(poll.id.toString(), game, owners, names) } }
        bgg.forEach { game ->
            li {
                gameRow(game, meta = "Not in anyone's collection here: someone can still bring it") {
                    form(action = "/polls/${poll.id}/games", method = FormMethod.post) {
                        hiddenInput("game", game.id.toString())
                        button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.SECONDARY, ButtonSize.SMALL)) { icon("plus"); +"Add" }
                    }
                }
            }
        }
    }
}

private fun List<String>.joinAnd(): String = if (size < 2) joinToString() else dropLast(1).joinToString(", ") + " and " + last()

/** One card per date: how many can, who can't, and (for the host) the button to pick it. This is what answering swaps. */
fun TagConsumer<*>.datePollResults(view: DatePollView, error: String? = null) = div("gf-opts") {
    id = "poll-results"
    val poll = view.poll
    val isHost = view.me == poll.host
    val best = poll.best().takeIf { poll.open }
    h2("gf-vh") { +"How it looks" }
    if (error != null) errorNotice(error)
    poll.tallies().forEach { tally ->
        val declined = poll.answers.filter { (_, dates) -> tally.date !in dates }.keys.map { view.names[it] ?: "someone" }
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
                    if (declined.isNotEmpty()) +" · ${declined.joinToString(" and ")} can't"
                    if (poll.open && unanswered > 0 && tally.available.isNotEmpty()) +" · $unanswered haven't answered"
                }
            }
            if (poll.asksAboutGames && poll.proposals.isNotEmpty()) {
                val missing = poll.planFor(tally.date).missing
                if (missing.isEmpty()) p("gf-small gf-opt__games") { icon("check"); +"Every game has someone to bring it" }
                else p("gf-small gf-opt__games gf-opt__games--missing") {
                    +"Missing that day: "
                    +missing.joinToString("; ") { game ->
                        val name = view.gameNames[game] ?: "a game"
                        val offered = poll.bringers(game)
                        if (offered.isEmpty()) "$name (nobody offered to bring it)"
                        else "$name (${offered.map { view.names[it] ?: "someone" }.joinAnd()} can't come)"
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
    val waiting = poll.waitingOn.map { view.names[it] ?: "someone" }
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
                    inviterLine(poll.host, view.names[poll.host], "wants to find a date for a game day")
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
