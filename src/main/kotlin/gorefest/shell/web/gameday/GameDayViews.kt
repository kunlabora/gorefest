package gorefest.shell.web.gameday

import gorefest.core.PlayLogged
import gorefest.core.PlayerId
import gorefest.core.game.Game
import gorefest.core.datepoll.DatePollSummary
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.ShelfGame
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.DEFAULT_TIME_ZONE
import gorefest.shell.web.zone
import gorefest.shell.web.bgg.collection
import gorefest.shell.web.components.ButtonSize
import gorefest.shell.web.components.ButtonStyle
import gorefest.shell.web.components.appBarAction
import gorefest.shell.web.components.appBarTitle
import gorefest.shell.web.components.avatar
import gorefest.shell.web.components.backLink
import gorefest.shell.web.components.browserTimeZone
import gorefest.shell.web.components.buttonClasses
import gorefest.shell.web.components.copyBox
import gorefest.shell.web.components.dayCard
import gorefest.shell.web.components.emptyState
import gorefest.shell.web.components.errorNotice
import gorefest.shell.web.components.field
import gorefest.shell.web.components.gameRow
import gorefest.shell.web.components.gameTile
import gorefest.shell.web.components.hx
import gorefest.shell.web.components.hyperscript
import gorefest.shell.web.components.icon
import gorefest.shell.web.components.inWords
import gorefest.shell.web.components.linkButton
import gorefest.shell.web.components.logoutButton
import gorefest.shell.web.components.long
import gorefest.shell.web.components.monthShort
import gorefest.shell.web.components.nextStep
import gorefest.shell.web.components.of
import gorefest.shell.web.components.page
import gorefest.shell.web.components.primaryButton
import gorefest.shell.web.components.primaryLink
import gorefest.shell.web.components.sectionHead
import gorefest.shell.web.datepoll.pollCard
import gorefest.shell.web.suggestions.suggestions
import io.ktor.http.Parameters
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.ul
import java.time.LocalDate
import java.time.ZoneId


fun HTML.homePage(gameDays: List<GameDaySummary>, polls: List<DatePollSummary>, today: LocalDate, bggLinked: Boolean) = page(
    "Game days",
    appBar = { appBarTitle("Gorefest"); logoutButton() },
    dock = {
        primaryLink("/gamedays/new", "Host a game day", icon = "plus")
        linkButton("/polls/new", "Not sure when? Find a date", ButtonStyle.GHOST)
    },
) {
    h1("gf-h1") { +"Your game days" }
    if (!bggLinked) {
        nextStep(
            "Optional", "Link your BGG account",
            "Bring games straight from your BoardGameGeek collection instead of searching for each one.",
            "/bgg?returnTo=%2F", "Link BGG", ButtonStyle.SECONDARY,
        )
    } else p("gf-small") { a("/collection", classes = "gf-link") { +"Your collection" } }
    if (polls.isNotEmpty()) div("gf-stack") {
        h2("gf-h2") { +"Finding a date" }
        polls.forEach { pollCard(it) }
    }
    if (gameDays.isEmpty()) {
        if (polls.isEmpty()) emptyState("No game days yet", "Got an invite? Tap the link in your group chat. Or host one yourself, or ask your group which date works.")
    } else {
        div("gf-stack") {
            if (polls.isNotEmpty()) h2("gf-h2") { +"Game days" }
            gameDays.forEach { dayCard(it, today) }
        }
    }
}

/** Creating and editing a game day: same form, different target. */
data class GameDayFormView(val heading: String, val back: Pair<String, String>, val appBarTitle: String, val action: String, val submitLabel: String, val editing: Boolean)

fun HTML.gameDayFormPage(view: GameDayFormView, values: Parameters = Parameters.Empty) =
    page(view.heading, appBar = { backLink(view.back.first, view.back.second); appBarTitle(view.appBarTitle) }) {
        h1("gf-h1") { +view.heading }
        consumer.gameDayForm(view, values = values)
    }

/** Create and edit share this form; editing leaves the time zone as it was created. */
fun TagConsumer<*>.gameDayForm(view: GameDayFormView, error: String? = null, values: Parameters = Parameters.Empty) =
    form(action = view.action, method = FormMethod.post, classes = "gf-section") {
        hx("post", view.action)
        hx("swap", "outerHTML")
        if (error != null) errorNotice(error)
        field("title", "Title", hint = "Whatever your group calls it.") {
            input(InputType.text, name = "title", classes = "gf-input") { id = "title"; placeholder = "Gorefest #7"; required = true; value = values["title"].orEmpty() }
        }
        field("date", "Date") {
            input(InputType.date, name = "date", classes = "gf-input") { id = "date"; required = true; value = values["date"].orEmpty() }
        }
        field("place", "Where", hint = "Shown on the invite. Leave out the house number if you like.") {
            input(InputType.text, name = "place", classes = "gf-input") { id = "place"; value = values["place"].orEmpty() }
        }
        if (!view.editing) browserTimeZone()
        primaryButton(view.submitLabel)
    }

/** What the next-step card on the hub should push. */
sealed interface NextStep {
    data class Checkpoint(val label: String) : NextStep
    data object TasteRound : NextStep
    data class TopUp(val uncertain: Int) : NextStep
    data class Favourites(val answers: Int) : NextStep
}

/** Everything the game day hub shows. */
data class GameDayHub(
    val gameDay: GameDaySummary,
    val names: Map<PlayerId, String>,
    val shelf: List<Pair<Game, ShelfGame>>,
    val played: List<Pair<Game, PlayLogged>>,
    val next: NextStep?,
    val inviteLink: String,
)

context(me: CurrentPlayer)
fun HTML.gameDayPage(hub: GameDayHub) {
    val gameDay = hub.gameDay
    val isHost = gameDay.host == me.id
    val open = !gameDay.closed
    page(gameDay.title, appBar = {
        backLink("/", "All game days")
        appBarTitle("Game days")
        if (isHost && open) appBarAction("/gamedays/${gameDay.id}/edit", "Edit")
    }) {
        div("gf-stack") {
            p("gf-kicker") { +listOf(gameDay.date.long(), gameDay.place).filter { it.isNotBlank() }.joinToString(" · ") }
            h1("gf-h1") { +gameDay.title }
            if (gameDay.cancelled) span("gf-tag") { +"Cancelled" } else if (gameDay.ended) span("gf-tag") { +"Ended" }
        }
        hub.next?.let { nextStepCard(gameDay, it) }
        if (open && hub.shelf.isNotEmpty()) div("gf-stack") {
            linkButton("/gamedays/${gameDay.id}/table", "Pick a game for my table", ButtonStyle.SECONDARY, ButtonSize.LARGE, block = true, icon = "dice")
        }
        if (hub.played.isNotEmpty()) section("gf-section") {
            // The log stays one tap away all day, not only through the checkpoint card.
            sectionHead("Played today", "/gamedays/${gameDay.id}/log".takeIf { hub.next is NextStep.Checkpoint }, "Log a game")
            ul("gf-list") {
                hub.played.forEach { (game, log) ->
                    li {
                        gameRow(game, meta = "${log.players.map { hub.names[it] ?: "?" }.joinToString(", ")} · ${log.minutes} min") {
                            span("gf-small gf-muted") { +log.at.atZone(zone(gameDay)).toLocalTime().withSecond(0).withNano(0).toString() }
                        }
                    }
                }
            }
        }
        section("gf-section") {
            sectionHead("The shelf · ${hub.shelf.size}", "/gamedays/${gameDay.id}/bring".takeIf { open }, "Bring games")
            if (hub.shelf.isEmpty()) emptyState("Nothing on the shelf yet", "Tick the games you're bringing, and ask the others to do the same.")
            else div("gf-tiles") {
                hub.shelf.forEach { (game, entry) -> gameTile(game, entry.owners.firstOrNull(), entry.owners.firstOrNull()?.let(hub.names::get)) }
            }
        }
        section("gf-section") {
            sectionHead("Players", "/gamedays/${gameDay.id}/ready".takeIf { isHost && open }, "How ready are we?")
            consumer.players(gameDay, hub.names, broughtBy = hub.shelf.flatMap { (_, entry) -> entry.owners }.groupingBy { it }.eachCount())
        }
        if (open) section("gf-section") {
            h2("gf-h2") { +"Invite friends" }
            consumer.invite(gameDay, hub.inviteLink)
        }
        if (isHost && open) p("gf-small") { a("/gamedays/${gameDay.id}/cancel", classes = "gf-link") { +"Cancel this game day" } }
    }
}

/** The host's confirm step: cancelling is final, and nobody is mailed. */
fun HTML.cancelGameDayPage(gameDay: GameDaySummary, error: String? = null) =
    page("Cancel ${gameDay.title}", appBar = { backLink("/gamedays/${gameDay.id}", gameDay.title); appBarTitle("Cancel game day") }) {
        div("gf-stack") {
            h1("gf-h1") { +"Cancel ${gameDay.title}?" }
            if (error != null) errorNotice(error)
            p("gf-muted") { +"The game day stays visible to everyone who joined, marked as cancelled, and can't be changed anymore. The invite link stops working. Nobody gets an email, so tell your group yourself." }
            form(action = "/gamedays/${gameDay.id}/cancel", method = FormMethod.post, classes = "gf-stack") {
                button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.LARGE, block = true)) { +"Yes, cancel the game day" }
                linkButton("/gamedays/${gameDay.id}", "Keep it", ButtonStyle.GHOST, block = true)
            }
        }
    }

private fun FlowContent.nextStepCard(gameDay: GameDaySummary, next: NextStep) {
    val base = "/gamedays/${gameDay.id}"
    when (next) {
        is NextStep.Checkpoint -> nextStep(next.label, "What did you play?", "Log it in 20 seconds and get fresh suggestions for the next round.", "$base/log", "Log a game")
        NextStep.TasteRound -> nextStep(
            "Your next step", "Tell us what you like",
            "A dozen quick \"which of these two?\" questions, about 2 minutes. Then you'll see your likely favourites on this shelf.",
            "$base/taste", "Start the taste round",
        )
        is NextStep.TopUp -> nextStep(
            "Your next step", "Sharpen your picks",
            "${next.uncertain.inWords()} ${if (next.uncertain == 1) "game" else "games"} on the shelf we're not sure about for you. A few quick pairs will sharpen your picks.",
            "$base/taste", "A few quick pairs",
        )
        is NextStep.Favourites -> nextStep(
            "Your next step", "Your likely favourites",
            "Based on your ${next.answers} answers. New games on the shelf? A few more pairs sharpen it.",
            "$base/favourites", "See your favourites",
        )
    }
}

context(me: CurrentPlayer)
private fun TagConsumer<*>.players(gameDay: GameDaySummary, names: Map<PlayerId, String>, broughtBy: Map<PlayerId, Int>) = div("gf-stack") {
    id = "players"
    if (gameDay.players.isNotEmpty()) ul("gf-list") {
        gameDay.players.forEach { player ->
            li(if (player == me.id) "gf-row gf-row--me" else "gf-row") {
                // No name: the player never finished signing up, or was erased.
                val name = names[player]
                avatar(player, name)
                span("gf-row__main") {
                    span("gf-row__title") { +(name ?: "Unknown player") }
                    span("gf-row__sub") {
                        val count = broughtBy[player] ?: 0
                        +when (count) { 0 -> "Bringing nothing yet"; 1 -> "Bringing 1 game"; else -> "Bringing $count games" }
                    }
                }
                if (player == me.id) span("gf-tag gf-tag--solid") { +"You" }
                if (player == gameDay.host) span("gf-tag gf-tag--brand") { +"Host" }
            }
        }
    }
}

/** The invite link to copy; the host can also replace it, which kills the old one. */
context(me: CurrentPlayer)
fun TagConsumer<*>.invite(gameDay: GameDaySummary, link: String) = div("gf-stack") {
    id = "invite"
    p("gf-small gf-muted") { +"Share this link in your group chat. Anyone with it can join." }
    copyBox(link, small = true)
    if (me.id == gameDay.host) button(classes = buttonClasses(ButtonStyle.GHOST, ButtonSize.SMALL)) {
        hx("post", "/gamedays/${gameDay.id}/invite")
        hx("target", "#invite")
        hx("swap", "outerHTML")
        hx("confirm", "The current link will stop working. Make a new one?")
        icon("refresh")
        +"Make a new link"
    }
}

private fun FlowContent.dayCard(gameDay: GameDaySummary, today: LocalDate) = dayCard(
    href = "/gamedays/${gameDay.id}",
    month = if (gameDay.date == today) "Today" else gameDay.date.monthShort(),
    day = gameDay.date.dayOfMonth.toString(),
    title = gameDay.title,
    meta = listOf(gameDay.place, "${gameDay.players.size} going").filter { it.isNotBlank() }.joinToString(" · "),
    tag = if (gameDay.cancelled) "Cancelled" else if (gameDay.ended) "Ended" else null,
)
