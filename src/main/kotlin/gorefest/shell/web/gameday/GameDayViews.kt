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
import gorefest.shell.web.components.Nav
import gorefest.shell.web.components.SiteBar
import gorefest.shell.web.components.art
import gorefest.shell.web.components.avatarStack
import gorefest.shell.web.components.band
import gorefest.shell.web.components.crumb
import gorefest.shell.web.components.split
import gorefest.shell.web.components.wrap
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
import kotlinx.html.article
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


/** The next open game day on the home hero: its people, a few covers from its shelf, and the step you still owe it. */
data class HomeNext(val gameDay: GameDaySummary, val names: Map<PlayerId, String>, val shelf: List<Game>, val step: NextStep?)

/** Everything the home page shows: the next game day is the hero, the rest (and open polls) sit in the "Coming up" band. */
data class HomeView(
    val next: HomeNext?,
    val later: List<GameDaySummary>,
    /** Ended or cancelled: kept for reference, below "Coming up". */
    val past: List<GameDaySummary>,
    val polls: List<DatePollSummary>,
    val today: LocalDate,
    val bggLinked: Boolean,
)

context(me: CurrentPlayer)
fun HTML.homePage(view: HomeView) = page(
    "Game days",
    site = SiteBar(me, Nav.GAME_DAYS),
    appBar = { logoutButton() },
    mainClasses = "gf-landing gf-home",
) {
    val nothingYet = view.next == null && view.later.isEmpty() && view.past.isEmpty() && view.polls.isEmpty()
    wrap {
        h1("gf-vh") { +"Your game days" }
        div("gf-home__hero") {
            div("gf-home__main") {
                p("gf-kicker") { +(if (view.next != null) "Hi ${me.name.substringBefore(' ')} · next up" else "Hi ${me.name.substringBefore(' ')}") }
                if (view.next != null) nextGameDay(view.next, view.today)
                else if (nothingYet) emptyState("No game days yet", "Got an invite? Tap the link in your group chat. Or host one yourself, or ask your group which date works.")
            }
            div("gf-home__aside") {
                // The one brand fill is the next step; without a game day, hosting takes it.
                val hostStyle = if (view.next == null) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY
                article("gf-card gf-homecard") {
                    h2("gf-homecard__title") { +"Host a game day" }
                    p("gf-small gf-muted") { +"Pick a date, share a link, everyone brings games." }
                    linkButton("/gamedays/new", "Host a game day", hostStyle, icon = "plus")
                }
                article("gf-card gf-homecard") {
                    h2("gf-homecard__title") { +"Not sure when?" }
                    p("gf-small gf-muted") { +"Ask your group which date suits them first." }
                    linkButton("/polls/new", "Find a date", ButtonStyle.SECONDARY, icon = "calendar")
                }
            }
        }
        // No nav under 720px, so the collection is one tap away here instead.
        if (view.bggLinked) p("gf-small gf-phone-only") { a("/collection", classes = "gf-link") { +"Your collection" } }
    }
    if (view.later.isNotEmpty() || view.polls.isNotEmpty() || !view.bggLinked) band(sunk = true) {
        div("gf-stack gf-home__coming") {
            h2("gf-h2 gf-h2--section") { +"Coming up" }
            div("gf-home__list") {
                view.later.forEach { dayCard(it, view.today) }
                view.polls.forEach { pollCard(it) }
                if (!view.bggLinked) div("gf-dashrow") {
                    id = "bgg-nudge"
                    div {
                        span("gf-daycard__title") { +"Link your BGG account" }
                        span("gf-daycard__meta") { +"Bring games from your collection." }
                    }
                    linkButton("/bgg?returnTo=%2F", "Link BGG", ButtonStyle.SECONDARY, ButtonSize.SMALL)
                }
            }
        }
    }
    if (view.past.isNotEmpty()) wrap {
        div("gf-stack gf-home__past") {
            h2("gf-h2 gf-h2--section") { +"Past" }
            div("gf-home__list") { view.past.forEach { dayCard(it, view.today) } }
        }
    }
}

/** The hero card: the date, who is going, a fan of covers from the shelf, and the one step you still owe. */
private fun FlowContent.nextGameDay(next: HomeNext, today: LocalDate) {
    val gameDay = next.gameDay
    val base = "/gamedays/${gameDay.id}"
    article("gf-card gf-homeday") {
        div("gf-homeday__top") {
            span("gf-date") {
                span("gf-date__month") { +dateMonth(gameDay, today) }
                +" "
                span("gf-date__day") { +gameDay.date.dayOfMonth.toString() }
            }
            div {
                style = "min-width: 0"
                h2("gf-homeday__title") { a(base) { +gameDay.title } }
                p("gf-muted gf-homeday__meta") { +listOf(gameDay.place, "${gameDay.players.size} going").filter { it.isNotBlank() }.joinToString(" · ") }
            }
        }
        div("gf-homeday__mid") {
            div("gf-homeday__who") {
                avatarStack(gameDay.players, next.names)
                if (next.step is NextStep.TasteRound) span("gf-tag gf-tag--accent") { +"Taste round not done" }
            }
            if (next.shelf.isNotEmpty()) div("gf-fan") {
                // Each box tilts a little differently, like a hand of cards.
                next.shelf.take(4).zip(listOf(-6, 3, -2, 5)).forEach { (game, tilt) -> art(game, extraStyle = "--r:${tilt}deg") }
            }
        }
        next.step?.let { step ->
            val (label, href) = step.action(base)
            primaryLink(href, label)
        } ?: linkButton(base, "Open ${gameDay.title}", ButtonStyle.PRIMARY, ButtonSize.LARGE, block = true)
    }
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
    /** The game day has started: playing is under way, so logging a play is offered whatever the next step is. */
    val logOffered: Boolean = false,
)

context(me: CurrentPlayer)
fun HTML.gameDayPage(hub: GameDayHub) {
    val gameDay = hub.gameDay
    val isHost = gameDay.host == me.id
    val open = !gameDay.closed
    val base = "/gamedays/${gameDay.id}"
    page(
        gameDay.title,
        site = SiteBar(me, Nav.GAME_DAYS),
        appBar = { if (isHost && open) appBarAction("$base/edit", "Edit") },
        mainClasses = "gf-landing gf-hub",
    ) {
        wrap {
            split("gf-hub__hero") {
                div("gf-split__copy") {
                    crumb("/", "All game days")
                    h1("gf-h1 gf-h1--hub") { +gameDay.title }
                    if (gameDay.cancelled) span("gf-tag") { +"Cancelled" } else if (gameDay.ended) span("gf-tag") { +"Ended" }
                    ul("gf-hubfacts") {
                        li { icon("calendar"); +gameDay.date.long() }
                        if (gameDay.place.isNotBlank()) li { icon("pin"); +gameDay.place }
                        li { icon("users"); +"${gameDay.players.size} going" }
                    }
                    if (open && hub.shelf.isNotEmpty()) linkButton("$base/table", "Pick a game for my table", ButtonStyle.SECONDARY, ButtonSize.LARGE, icon = "dice")
                    // On the day, someone still doing their taste round can log a play from here too.
                    if (open && hub.logOffered && hub.next !is NextStep.Checkpoint) linkButton("$base/log", "Log a game", ButtonStyle.GHOST, ButtonSize.SMALL)
                }
                hub.next?.let { nextStepCard(gameDay, it) }
            }
        }
        band(sunk = true) {
            div("gf-stack gf-hub__shelf") {
                div("gf-sechead") {
                    h2("gf-h2 gf-h2--section") { +"The shelf · ${hub.shelf.size}" }
                    if (open) linkButton("$base/bring", "Bring games", ButtonStyle.SECONDARY, ButtonSize.SMALL, icon = "plus")
                }
                if (hub.shelf.isEmpty()) emptyState("Nothing on the shelf yet", "Tick the games you're bringing, and ask the others to do the same.")
                else div("gf-tiles") {
                    hub.shelf.forEach { (game, entry) -> gameTile(game, entry.owners.firstOrNull(), entry.owners.firstOrNull()?.let(hub.names::get)) }
                }
            }
        }
        wrap("gf-hub__rest") {
            if (hub.played.isNotEmpty()) section("gf-section") {
                // The log stays one tap away all day, not only through the checkpoint card.
                sectionHead("Played today", "$base/log".takeIf { hub.next is NextStep.Checkpoint }, "Log a game")
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
            div("gf-hub__cols") {
                section("gf-section") {
                    sectionHead("Players", "$base/ready".takeIf { isHost && open }, "How ready are we?")
                    consumer.players(gameDay, hub.names, broughtBy = hub.shelf.flatMap { (_, entry) -> entry.owners }.groupingBy { it }.eachCount())
                }
                if (open) section("gf-section") {
                    h2("gf-h2") { +"Invite friends" }
                    consumer.invite(gameDay, hub.inviteLink)
                }
            }
            if (isHost && open) p("gf-small") { a("$base/cancel", classes = "gf-link") { +"Cancel this game day" } }
        }
    }
}

/** The host's confirm step: cancelling is final, and nobody is mailed. */
context(me: CurrentPlayer)
fun HTML.cancelGameDayPage(gameDay: GameDaySummary, error: String? = null) =
    page("Cancel ${gameDay.title}", site = SiteBar(me, Nav.GAME_DAYS), mainClasses = "gf-landing") {
        wrap {
            div("gf-split__copy gf-narrow") {
                crumb("/gamedays/${gameDay.id}", gameDay.title)
                p("gf-kicker") { +"Cancel game day" }
                h1("gf-h1 gf-h1--landing") { +"Cancel ${gameDay.title}?" }
                if (error != null) errorNotice(error)
                p("gf-lede gf-muted") { +"The game day stays visible to everyone who joined, marked as cancelled, and can't be changed anymore. The invite link stops working. Nobody gets an email, so tell your group yourself." }
                form(action = "/gamedays/${gameDay.id}/cancel", method = FormMethod.post, classes = "gf-cancel-actions") {
                    button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.LARGE)) { +"Yes, cancel the game day" }
                    linkButton("/gamedays/${gameDay.id}", "Keep it", ButtonStyle.GHOST)
                }
            }
        }
    }

/** The button for a step: its label and where it goes. */
private fun NextStep.action(base: String): Pair<String, String> = when (this) {
    is NextStep.Checkpoint -> "Log a game" to "$base/log"
    NextStep.TasteRound -> "Tell us what you like" to "$base/taste"
    is NextStep.TopUp -> "A few quick pairs" to "$base/taste"
    is NextStep.Favourites -> "See your favourites" to "$base/favourites"
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

private fun dateMonth(gameDay: GameDaySummary, today: LocalDate) = if (gameDay.date == today) "Today" else gameDay.date.monthShort()

private fun FlowContent.dayCard(gameDay: GameDaySummary, today: LocalDate) = dayCard(
    href = "/gamedays/${gameDay.id}",
    month = dateMonth(gameDay, today),
    day = gameDay.date.dayOfMonth.toString(),
    title = gameDay.title,
    meta = listOf(gameDay.place, "${gameDay.players.size} going").filter { it.isNotBlank() }.joinToString(" · "),
    tag = if (gameDay.cancelled) "Cancelled" else if (gameDay.ended) "Ended" else null,
)
