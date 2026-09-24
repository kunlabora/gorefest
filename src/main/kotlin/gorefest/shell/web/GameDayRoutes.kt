package gorefest.shell.web

import gorefest.core.GameDayId
import gorefest.core.eventsourcing.Decision
import gorefest.core.gameday.CreateGameDay
import gorefest.core.gameday.CreateGameDayDecider
import gorefest.core.gameday.EditGameDay
import gorefest.core.gameday.EditGameDayDecider
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.GameDays
import gorefest.core.gameday.RotateInviteLink
import gorefest.core.gameday.RotateInviteLinkDecider
import gorefest.core.PlayerId
import gorefest.shell.App
import gorefest.shell.AppConfig
import gorefest.shell.InviteLinks
import gorefest.shell.auth.Login
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import kotlinx.html.InputType
import kotlinx.html.DIV
import kotlinx.html.role
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.style
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.html.TagConsumer
import gorefest.shell.IdGenerator
import java.time.Clock

private const val DEFAULT_TIME_ZONE = "Europe/Brussels"

context(ids: IdGenerator, clock: Clock, login: Login, config: AppConfig)
fun Route.gameDayRoutes(app: App) {
    get("/") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDays = with(GameDays) { app.gameDays.state.forPlayer(me.id) { zone -> todayIn(zone) } }
            val today = todayIn(DEFAULT_TIME_ZONE)
            call.respondHtml {
                page(
                    "Game days",
                    appBar = { appBarTitle("Gorefest"); logoutButton() },
                    dock = {
                        a("/gamedays/new", classes = "gf-btn gf-btn--primary gf-btn--lg gf-btn--block") { icon("plus"); +"Host a game day" }
                    },
                ) {
                    h1("gf-h1") { +"Your game days" }
                    if (me.id !in app.bggAccounts.state) section("gf-next") {
                        p("gf-kicker") { +"Optional" }
                        h2("gf-next__title") { +"Link your BGG account" }
                        p("gf-next__text") { +"Bring games straight from your BoardGameGeek collection instead of searching for each one." }
                        a("/bgg?returnTo=%2F", classes = "gf-btn gf-btn--secondary gf-btn--block") { +"Link BGG" }
                    } else p("gf-small") { a("/collection", classes = "gf-link") { +"Your collection" } }
                    if (gameDays.isEmpty()) {
                        emptyState("No game days yet", "Got an invite? Tap the link in your group chat. Or host one yourself.")
                    } else {
                        div("gf-stack") { gameDays.forEach { dayCard(it, today) } }
                    }
                }
            }
        }
    }

    get("/gamedays/new") {
        authenticated {
            call.respondHtml {
                page("Host a game day", appBar = { backLink("/", "All game days"); appBarTitle("New game day") }) {
                    h1("gf-h1") { +"Host a game day" }
                    consumer.gameDayForm(action = "/gamedays", submitLabel = "Create game day")
                }
            }
        }
    }

    post("/gamedays") {
        authenticated {
            val form = call.receiveParameters()
            val command = CreateGameDay(
                gameDay = GameDayId(ids.next()),
                host = contextOf<CurrentPlayer>().id,
                title = form["title"].orEmpty(),
                date = form["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.MIN,
                place = form["place"].orEmpty(),
                timeZone = form["timeZone"] ?: DEFAULT_TIME_ZONE,
                today = LocalDate.now(clock.withZone(zoneOrDefault(form["timeZone"]))),
            )
            when (val decision = app.commands.handle(CreateGameDayDecider, command)) {
                is Decision.Accepted -> redirect("/gamedays/${command.gameDay}")
                is Decision.Rejected -> respondFragment { gameDayForm("/gamedays", "Create game day", decision.reason, form) }
            }
        }
    }

    get("/gamedays/{id}/edit") {
        authenticated {
            val gameDay = gameDayFromPath(app)?.takeIf { it.host == contextOf<CurrentPlayer>().id }
                ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val values = Parameters.build {
                append("title", gameDay.title)
                append("date", gameDay.date.toString())
                append("place", gameDay.place)
            }
            call.respondHtml {
                page("Edit ${gameDay.title}", appBar = { backLink("/gamedays/${gameDay.id}", gameDay.title); appBarTitle("Edit game day") }) {
                    h1("gf-h1") { +"Edit game day" }
                    consumer.gameDayForm("/gamedays/${gameDay.id}/edit", "Save changes", values = values, editing = true)
                }
            }
        }
    }

    post("/gamedays/{id}/edit") {
        authenticated {
            val gameDay = gameDayFromPath(app) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val command = EditGameDay(
                gameDay = gameDay.id,
                editor = contextOf<CurrentPlayer>().id,
                title = form["title"].orEmpty(),
                date = form["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.MIN,
                place = form["place"].orEmpty(),
                today = todayIn(gameDay.timeZone),
            )
            when (val decision = app.commands.handle(EditGameDayDecider, command)) {
                is Decision.Accepted -> redirect("/gamedays/${gameDay.id}")
                is Decision.Rejected ->
                    respondFragment { gameDayForm("/gamedays/${gameDay.id}/edit", "Save changes", decision.reason, form, editing = true) }
            }
        }
    }

    get("/gamedays/{id}") {
        authenticated {
            val gameDay = gameDayFromPath(app)?.takeIf { contextOf<CurrentPlayer>().id in it.players }
                ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val names = login.names(gameDay.players)
            val shelf = app.shelves.state[gameDay.id].orEmpty().mapNotNull { entry -> app.catalog[entry.game]?.let { it to entry } }
            val answers = app.tastes.state[contextOf<CurrentPlayer>().id]?.comparisons?.size ?: 0
            val onTheDay = gameDay.date == todayIn(gameDay.timeZone)
            val played = app.plays.state[gameDay.id].orEmpty().mapNotNull { log -> app.catalog[log.game]?.let { it to log } }
            call.respondHtml {
                val isHost = gameDay.host == contextOf<CurrentPlayer>().id
                page(gameDay.title, appBar = {
                    backLink("/", "All game days")
                    appBarTitle("Game days")
                    if (isHost && !gameDay.ended) {
                        a("/gamedays/${gameDay.id}/edit", classes = "gf-btn gf-btn--ghost gf-btn--sm") { style = "margin-left: auto"; +"Edit" }
                    }
                }) {
                    div("gf-stack") {
                        p("gf-kicker") { +listOf(gameDay.date.long(), gameDay.place).filter { it.isNotBlank() }.joinToString(" · ") }
                        h1("gf-h1") { +gameDay.title }
                        if (gameDay.ended) span("gf-tag") { +"Ended" }
                    }
                    if (!gameDay.ended && onTheDay) section("gf-next") {
                        p("gf-kicker") { +checkpointLabel(gameDay, clock) }
                        h2("gf-next__title") { +"What did you play?" }
                        p("gf-next__text") { +"Log it in 20 seconds and get fresh suggestions for the next round." }
                        a("/gamedays/${gameDay.id}/log", classes = "gf-btn gf-btn--primary gf-btn--block") { +"Log a game" }
                    } else if (!gameDay.ended) section("gf-next") {
                        p("gf-kicker") { +"Your next step" }
                        if (answers < 10) {
                            h2("gf-next__title") { +"Tell us what you like" }
                            p("gf-next__text") { +"A dozen quick \"which of these two?\" questions, about 2 minutes. Then you'll see your likely favourites on this shelf." }
                            a("/gamedays/${gameDay.id}/taste", classes = "gf-btn gf-btn--primary gf-btn--block") { +"Start the taste round" }
                        } else {
                            h2("gf-next__title") { +"Your likely favourites" }
                            p("gf-next__text") { +"Based on your $answers answers. New games on the shelf? A few more pairs sharpen it." }
                            a("/gamedays/${gameDay.id}/favourites", classes = "gf-btn gf-btn--primary gf-btn--block") { +"See your favourites" }
                        }
                    }
                    if (!gameDay.ended && shelf.isNotEmpty()) div("gf-stack") {
                        a("/gamedays/${gameDay.id}/table", classes = "gf-btn gf-btn--secondary gf-btn--lg gf-btn--block") { icon("dice"); +"Pick a game for my table" }
                    }
                    if (played.isNotEmpty()) section("gf-section") {
                        h2("gf-h2") { +"Played today" }
                        ul("gf-list") {
                            played.forEach { (game, log) ->
                                li {
                                    gameRow(game, meta = "${log.players.map { names[it] ?: "?" }.joinToString(", ")} · ${log.minutes} min") {
                                        span("gf-small gf-muted") {
                                            +log.at.atZone(zoneOrDefault(gameDay.timeZone)).toLocalTime().withSecond(0).withNano(0).toString()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    section("gf-section") {
                        div("gf-sechead") {
                            h2("gf-h2") { +"The shelf · ${shelf.size}" }
                            if (!gameDay.ended) a("/gamedays/${gameDay.id}/bring", classes = "gf-link") { +"Bring games" }
                        }
                        if (shelf.isEmpty()) emptyState("Nothing on the shelf yet", "Tick the games you're bringing, and ask the others to do the same.")
                        else div("gf-tiles") {
                            shelf.forEach { (game, entry) ->
                                div("gf-tile") {
                                    art(game)
                                    span("gf-tile__owner") { entry.owners.firstOrNull()?.let { avatar(it, names[it]) } }
                                    span("gf-tile__name") { +game.name }
                                }
                            }
                        }
                    }
                    section("gf-section") {
                        div("gf-sechead") {
                            h2("gf-h2") { +"Players" }
                            if (isHost && !gameDay.ended) a("/gamedays/${gameDay.id}/ready", classes = "gf-link") { +"How ready are we?" }
                        }
                        consumer.players(gameDay, names)
                    }
                    if (!gameDay.ended) section("gf-section") {
                        h2("gf-h2") { +"Invite friends" }
                        consumer.invite(gameDay, app.invites)
                    }
                }
            }
        }
    }

    post("/gamedays/{id}/invite") {
        authenticated {
            val gameDay = gameDayFromPath(app) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            when (val decision = app.commands.handle(RotateInviteLinkDecider, RotateInviteLink(gameDay.id, contextOf<CurrentPlayer>().id))) {
                is Decision.Accepted -> respondFragment { invite(app.gameDays.state.getValue(gameDay.id), app.invites) }
                is Decision.Rejected -> respondFragment(HttpStatusCode.Forbidden) {
                    div("gf-notice gf-notice--error") {
                        id = "invite"
                        role = "alert"
                        icon("alert")
                        p { +decision.reason }
                    }
                }
            }
        }
    }
}

private fun RoutingContext.gameDayFromPath(app: App): GameDaySummary? =
    call.parameters["id"]?.let { runCatching { GameDayId.parse(it) }.getOrNull() }?.let(app.gameDays.state::get)

context(me: CurrentPlayer)
private fun TagConsumer<*>.players(gameDay: GameDaySummary, names: Map<PlayerId, String>) {
    div("gf-stack") {
        id = "players"
        if (gameDay.players.isNotEmpty()) ul("gf-list") {
            gameDay.players.forEach { player ->
                li("gf-row") {
                    // No name: the player never finished signing up, or was erased.
                    val name = names[player]
                    avatar(player, name)
                    span("gf-row__main") {
                        span("gf-row__title") { +(name ?: "Unknown player") }
                        if (player == me.id) span("gf-row__sub") { +"You" }
                    }
                    if (player == gameDay.host) span("gf-tag gf-tag--brand") { +"Host" }
                }
            }
        }
    }
}

/** The invite link to copy; the host can also replace it, which kills the old one. */
context(me: CurrentPlayer, config: AppConfig)
private fun TagConsumer<*>.invite(gameDay: GameDaySummary, invites: InviteLinks) {
    val link = "${config.baseUrl}/i/${invites.token(gameDay)}"
    div("gf-stack") {
        id = "invite"
        p("gf-small gf-muted") { +"Share this link in your group chat. Anyone with it can join." }
        div("gf-copy") {
            span("gf-copy__value gf-copy__value--small") { +link }
            button(classes = "gf-btn gf-btn--secondary gf-btn--sm") {
                attributes["data-link"] = link
                hyperscript("on click call navigator.clipboard.writeText(@data-link) then put 'Copied' into me")
                +"Copy"
            }
        }
        if (me.id == gameDay.host) button(classes = "gf-btn gf-btn--ghost gf-btn--sm") {
            hx("post", "/gamedays/${gameDay.id}/invite")
            hx("target", "#invite")
            hx("swap", "outerHTML")
            hx("confirm", "The current link will stop working. Make a new one?")
            icon("refresh")
            +"Make a new link"
        }
    }
}

private fun FlowContent.dayCard(gameDay: GameDaySummary, today: LocalDate) {
    a("/gamedays/${gameDay.id}", classes = "gf-daycard") {
        span("gf-date") {
            span("gf-date__month") { +if (gameDay.date == today) "Today" else gameDay.date.monthShort() }
            +" " // flex ignores it; screen readers and text extraction need it
            span("gf-date__day") { +gameDay.date.dayOfMonth.toString() }
        }
        span {
            style = "min-width: 0"
            span("gf-daycard__title") { +gameDay.title }
            span("gf-daycard__meta") {
                +listOf(gameDay.place, "${gameDay.players.size} going").filter { it.isNotBlank() }.joinToString(" · ")
            }
            if (gameDay.ended) span("gf-tag") { style = "margin-top: 6px"; +"Ended" }
        }
        span {
            style = "display: flex; transform: rotate(180deg); color: var(--ink-muted)"
            icon("back")
        }
    }
}

/** Create and edit share this form; editing leaves the time zone as it was created. */
private fun TagConsumer<*>.gameDayForm(
    action: String,
    submitLabel: String,
    error: String? = null,
    values: Parameters = Parameters.Empty,
    editing: Boolean = false,
) {
    form(action = action, method = kotlinx.html.FormMethod.post, classes = "gf-section") {
        hx("post", action)
        hx("swap", "outerHTML")
        if (error != null) div("gf-notice gf-notice--error") {
            role = "alert"
            icon("alert")
            p { +error }
        }
        field("title", "Title", hint = "Whatever your group calls it.") {
            input(InputType.text, name = "title", classes = "gf-input") { id = "title"; placeholder = "Gorefest #7"; required = true; value = values["title"].orEmpty() }
        }
        field("date", "Date") {
            input(InputType.date, name = "date", classes = "gf-input") { id = "date"; required = true; value = values["date"].orEmpty() }
        }
        field("place", "Where", hint = "Shown on the invite. Leave out the house number if you like.") {
            input(InputType.text, name = "place", classes = "gf-input") { id = "place"; value = values["place"].orEmpty() }
        }
        if (!editing) input(InputType.hidden, name = "timeZone") {
            value = DEFAULT_TIME_ZONE
            hyperscript("init set my value to Intl.DateTimeFormat().resolvedOptions().timeZone")
        }
        button(classes = "gf-btn gf-btn--primary gf-btn--lg gf-btn--block") {
            span("gf-spin") { attributes["aria-hidden"] = "true" }
            +submitLabel
        }
    }
}

private fun FlowContent.field(id: String, label: String, hint: String? = null, input: DIV.() -> Unit) = div("gf-field") {
    label("gf-label") { htmlFor = id; +label }
    input()
    if (hint != null) p("gf-hint") { +hint }
}

context(clock: Clock)
private fun todayIn(timeZone: String): LocalDate = LocalDate.now(clock.withZone(zoneOrDefault(timeZone)))

private fun zoneOrDefault(id: String?): ZoneId =
    id?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of(DEFAULT_TIME_ZONE)

