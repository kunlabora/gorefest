package gorefest.shell.web.datepoll

import gorefest.core.PlayerId
import gorefest.core.datepoll.DatePollSummary
import gorefest.core.datepoll.MAX_POLL_DATES
import gorefest.core.datepoll.MIN_POLL_DATES
import gorefest.core.gameday.GameDaySummary
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
        browserTimeZone()
        primaryButton("Start the poll")
    }

/** One more date input, for the "add another date" button. */
fun TagConsumer<*>.dateField(value: String = "") = input(InputType.date, name = "dates", classes = "gf-input") {
    attributes["aria-label"] = "Date"
    this.value = value
}

/** Everything the poll page shows. [gameDay] is the game day made from the pick, once there is one. */
data class DatePollView(
    val poll: DatePollSummary,
    val names: Map<PlayerId, String>,
    val viewer: CurrentPlayer,
    val today: LocalDate,
    val inviteLink: String,
    val gameDay: GameDaySummary?,
) {
    val me: PlayerId get() = viewer.id
}

fun HTML.datePollPage(view: DatePollView) {
    val poll = view.poll
    val host = view.names[poll.host]
    page(poll.title, site = SiteBar(view.viewer, Nav.GAME_DAYS)) {
        wrap {
            split("gf-split--poll") {
                div("gf-split__copy gf-poll-copy") {
                    crumb("/", "All game days")
                    p("gf-kicker") { +"Finding a date · ${poll.answers.size} of ${poll.participants.size} answered" }
                    h1("gf-h1 gf-h1--landing") { +poll.title }
                    if (poll.place.isNotBlank()) ul("gf-facts") { li { icon("pin"); +poll.place } }
                    p("gf-lede gf-muted") {
                        +if (view.me == poll.host) "Pick the date that suits most people. Everyone who can make it gets the game day."
                        else "Tick every date you can make. ${host ?: "The host"} picks the winner and the game day appears."
                    }
                    poll.picked?.let { picked ->
                        notice("check", NoticeKind.GOOD) { +"The host picked "; strong { +picked.date.full() }; +"." }
                        if (view.gameDay?.cancelled == true) p("gf-muted") { +"${view.gameDay.title} was cancelled by the host." }
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
            if (isHost && poll.open && tally.date >= view.today) button(
                type = ButtonType.button,
                classes = buttonClasses(if (tally.date == best) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, block = true),
            ) {
                hx("post", "/polls/${poll.id}/pick")
                hx("vals", """{"date": "${tally.date}"}""")
                hx("target", "#poll-results")
                hx("swap", "outerHTML")
                hx("confirm", "Pick ${tally.date.short()}? The poll closes and the game day is created.")
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
    val host = view.names[poll.host] ?: "Someone"
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
                    }
                    p("gf-small gf-muted") { +"Tick the dates you can make. ${host} picks the winner and the game day appears." }
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
