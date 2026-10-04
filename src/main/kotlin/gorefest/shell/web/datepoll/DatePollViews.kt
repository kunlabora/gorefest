package gorefest.shell.web.datepoll

import gorefest.core.PlayerId
import gorefest.core.datepoll.DatePollSummary
import gorefest.core.datepoll.MIN_POLL_DATES
import gorefest.core.gameday.GameDaySummary
import gorefest.shell.web.components.*
import io.ktor.http.Parameters
import kotlinx.html.*
import java.time.LocalDate

/** The form that starts a poll: a title, a place and a few dates (with "add another"). */
fun HTML.datePollFormPage(values: Parameters = Parameters.Empty) =
    page("Find a date", appBar = { backLink("/", "All game days"); appBarTitle("Find a date") }) {
        div("gf-stack") {
            h1("gf-h1") { +"Find a date" }
            p("gf-muted") { +"Pick a few dates that could work, share the link, and see who can make which. You choose the winner; the game day is made for you." }
        }
        consumer.datePollForm(values = values)
    }

fun TagConsumer<*>.datePollForm(error: String? = null, values: Parameters = Parameters.Empty) =
    form(action = "/polls", method = FormMethod.post, classes = "gf-section") {
        id = "poll-form"
        hx("post", "/polls")
        hx("swap", "outerHTML")
        if (error != null) errorNotice(error)
        field("title", "Title", hint = "Whatever your group calls it.") {
            input(InputType.text, name = "title", classes = "gf-input") { id = "title"; placeholder = "Gorefest #7"; required = true; value = values["title"].orEmpty() }
        }
        field("place", "Where", hint = "Shown on the invite. Leave out the house number if you like.") {
            input(InputType.text, name = "place", classes = "gf-input") { id = "place"; value = values["place"].orEmpty() }
        }
        div("gf-section") {
            h2("gf-h2") { +"Which dates could work?" }
            div("gf-stack") {
                id = "poll-dates"
                val given = values.getAll("dates").orEmpty()
                (given + List((MIN_POLL_DATES + 1 - given.size).coerceAtLeast(0)) { "" }).forEach { dateField(it) }
            }
            button(type = ButtonType.button, classes = buttonClasses(ButtonStyle.GHOST, ButtonSize.SMALL)) {
                hx("get", "/polls/new/date")
                hx("target", "#poll-dates")
                hx("swap", "beforeend")
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
    val me: PlayerId,
    val today: LocalDate,
    val inviteLink: String,
    val gameDay: GameDaySummary?,
)

fun HTML.datePollPage(view: DatePollView) {
    val poll = view.poll
    page(
        poll.title,
        appBar = { backLink("/", "All game days"); appBarTitle(poll.title) },
    ) {
        div("gf-stack") {
            p("gf-kicker") { +"Finding a date · hosted by ${view.names[poll.host] ?: "someone"}" }
            h1("gf-h1") { +poll.title }
            if (poll.place.isNotBlank()) ul("gf-facts") { li { icon("pin"); +poll.place } }
        }
        poll.picked?.let { picked ->
            notice("check", NoticeKind.GOOD) { +"The host picked "; strong { +picked.date.full() }; +"." }
            if (view.gameDay != null && view.me in view.gameDay.players) linkButton("/gamedays/${view.gameDay.id}", "Open ${view.gameDay.title}", ButtonStyle.PRIMARY)
            else p("gf-muted") { +"You weren't down as available that day, so you're not on the list. Ask ${view.names[poll.host] ?: "the host"} for the game day link if you can make it after all." }
        }
        if (poll.open) div("gf-section") {
            h2("gf-h2") { +"Which dates can you make?" }
            form(classes = "gf-section") {
                id = "answer-form"
                hx("post", "/polls/${poll.id}/answer")
                hx("trigger", "change")
                hx("target", "#poll-results")
                hx("swap", "outerHTML")
                val mine = poll.answers[view.me].orEmpty()
                dateChips(poll.dates, checked = { it in mine }, disabled = { it < view.today }, legend = "Dates you can make")
                p("gf-small gf-muted") { +"Tick every date that suits you. It saves as you go." }
            }
        }
        consumer.datePollResults(view)
        if (poll.open) div("gf-section") {
            h2("gf-h2") { +"Invite people" }
            p("gf-small gf-muted") { +"Share this link in your group chat so they can answer too." }
            copyBox(view.inviteLink, small = true)
        }
    }
}

/** Who can make which date; the host also gets the buttons to pick one. This is what answering swaps. */
fun TagConsumer<*>.datePollResults(view: DatePollView, error: String? = null) = section("gf-section") {
    id = "poll-results"
    val poll = view.poll
    val isHost = view.me == poll.host
    val best = poll.best().takeIf { poll.open }
    h2("gf-h2") { +"How it looks" }
    if (error != null) errorNotice(error)
    ul("gf-list") {
        poll.tallies().forEach { tally ->
            li("gf-row") {
                span("gf-row__main") {
                    span("gf-row__title") { +tally.date.short() }
                    span("gf-row__sub") {
                        +if (tally.available.isEmpty()) "Nobody yet" else "${tally.available.size} can"
                        if (tally.available.isNotEmpty()) avatarStack(tally.available, view.names)
                    }
                }
                if (tally.date == best) span("gf-tag gf-tag--brand") { +"Best so far" }
                if (poll.picked?.date == tally.date) span("gf-tag gf-tag--olive") { +"Picked" }
                if (isHost && poll.open && tally.date >= view.today) button(type = ButtonType.button, classes = buttonClasses(ButtonStyle.SECONDARY, ButtonSize.SMALL)) {
                    hx("post", "/polls/${poll.id}/pick")
                    hx("vals", """{"date": "${tally.date}"}""")
                    hx("target", "#poll-results")
                    hx("swap", "outerHTML")
                    hx("confirm", "Pick ${tally.date.short()}? The poll closes and the game day is created.")
                    +"Pick"
                }
            }
        }
    }
    val waiting = poll.waitingOn.map { view.names[it] ?: "someone" }
    if (poll.open && waiting.isNotEmpty()) p("gf-small gf-muted") { +"Still to answer: ${waiting.joinToString(", ")}." }
}

/** The invite landing: what the poll is about, and a way in. */
data class PollInviteView(val poll: DatePollSummary, val names: Map<PlayerId, String>, val loggedIn: Boolean, val path: String)

fun HTML.pollInvitePage(view: PollInviteView) {
    val poll = view.poll
    val host = view.names[poll.host] ?: "Someone"
    page(
        "Find a date: ${poll.title}",
        appBar = { appBarTitle("Gorefest") },
        head = {
            openGraph("og:title", "$host is finding a date: ${poll.title}")
            openGraph("og:description", poll.dates.joinToString(" · ") { it.short() })
        },
        dock = { joinForm(view.path, "Join and answer", view.loggedIn) },
    ) {
        inviterLine(poll.host, view.names[poll.host], "wants to find a date for a game day")
        h1("gf-hero") { +poll.title }
        ul("gf-facts") {
            if (poll.place.isNotBlank()) li { icon("pin"); +poll.place }
            li { icon("calendar"); +"${poll.dates.size.of("date")} to choose from" }
            li { icon("users"); avatarStack(poll.participants, view.names); +"${poll.participants.size.of("person", "people")} in" }
        }
        ul("gf-list") {
            poll.dates.forEach { date -> li("gf-row") { span("gf-row__main") { span("gf-row__title") { +date.short() } } } }
        }
        p("gf-small gf-muted") { +"Tick the dates you can make. ${host} picks the winner and the game day appears." }
    }
}

/** A poll on the home page. */
fun FlowContent.pollCard(poll: DatePollSummary) = dayCard(
    href = "/polls/${poll.id}", month = "Poll", day = "${poll.dates.size}", title = poll.title,
    meta = "${poll.answers.size} of ${poll.participants.size} answered",
)
