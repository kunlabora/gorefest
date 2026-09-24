package gorefest.shell.web

import gorefest.core.Basis
import gorefest.core.BggId
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.GameDayId
import gorefest.core.GamePlayStatusDeclared
import gorefest.core.bgg.Game
import gorefest.core.gameday.GameDaySummary
import gorefest.core.taste.DeclarePlayStatusDecider
import gorefest.core.taste.PlayerGames
import gorefest.core.taste.Predictor
import gorefest.core.taste.RecordComparisonDecider
import gorefest.core.taste.RoundState
import gorefest.core.taste.TasteHistory
import gorefest.core.taste.Wizard
import gorefest.core.taste.WizardPair
import gorefest.shell.App
import gorefest.shell.auth.Login
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.main
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.strong
import kotlinx.html.id

private val wizard = Wizard()

/** For players who already did a round: a few pairs on what's still uncertain. */
private val topUp = Wizard(minPairs = 3, maxPairs = 5)
private val predictor = Predictor()

/** Players with this many answers get a top-up round instead of the full one. */
const val TOP_UP_AFTER = 10

private fun wizardFor(topUpRound: Boolean) = if (topUpRound) topUp else wizard

/** The games a taste round draws from, and what we know about the player. */
private data class RoundInputs(val pool: List<Game>, val shelf: List<Game>, val anchors: List<Game>, val player: PlayerGames)

context(login: Login)
fun Route.wizardRoutes(app: App) {
    suspend fun inputs(gameDay: GameDaySummary, me: CurrentPlayer): RoundInputs {
        val shelf = app.shelves.state[gameDay.id].orEmpty().map { it.game }
        val hosts = app.collectionSync.collection(gameDay.host)
        val mine = app.collectionSync.collection(me.id)
        val pool = (shelf + hosts + mine).distinct().mapNotNull(app.catalog::get)
        val anchors = app.catalog.all().filter { it.rank != null }.sortedBy { it.rank }.take(30)
        val player = PlayerGames(app.tastes.state[me.id] ?: TasteHistory(), mine)
        return RoundInputs(pool, shelf.mapNotNull(app.catalog::get), anchors, player)
    }

    fun RoundInputs.top(now: java.time.Instant) = predictor.favourites(player, shelf.ifEmpty { pool }, now).map { it.game.id }

    fun RoundInputs.focus(topUpRound: Boolean, now: java.time.Instant) =
        if (topUpRound) topUp.uncertain(player, shelf, now).ifEmpty { null } else null

    get("/gamedays/{id}/taste") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val inputs = inputs(gameDay, me)
            val now = app.clock.instant()
            val topUpRound = inputs.player.history.comparisons.size >= TOP_UP_AFTER
            val round = RoundState(top = inputs.top(now))
            val pair = (if (topUpRound) topUp.stalePair(inputs.player, app.catalog::get, now) else null)
                ?: wizardFor(topUpRound).next(inputs.pool, inputs.anchors, inputs.player, round, now, inputs.focus(topUpRound, now))
                ?: return@authenticated redirect("/gamedays/${gameDay.id}/favourites")
            call.respondHtml {
                page(
                    "${gameDay.title} · Taste round",
                    appBar = {
                        appBarTitle("${gameDay.title} · Taste round")
                        a("/gamedays/${gameDay.id}/favourites", classes = "gf-btn gf-btn--ghost gf-btn--sm") { attributes["style"] = "margin-left: auto"; +"Done for now" }
                    },
                    mainClasses = "gf-wizard",
                    mainId = "wizard",
                ) { wizardContent(gameDay, pair, round, topUpRound) }
            }
        }
    }

    post("/gamedays/{id}/taste") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val a = form["a"]?.toIntOrNull()?.let(::BggId)?.let(app.catalog::get)
            val b = form["b"]?.toIntOrNull()?.let(::BggId)?.let(app.catalog::get)
            if (a == null || b == null) return@authenticated redirect("/gamedays/${gameDay.id}/taste")
            val basis = runCatching { Basis.valueOf(form["basis"].orEmpty()) }.getOrDefault(Basis.VIBE)
            val now = app.clock.instant()

            when (val answer = form["answer"]) {
                "A", "B", "BOTH_GREAT", "BOTH_MEH" ->
                    app.commands.handle(RecordComparisonDecider, ComparisonRecorded(me.id, a.id, b.id, ComparisonResult.valueOf(answer), basis, now))
                "UNPLAYED" -> listOf(a, b).forEach { app.commands.handle(DeclarePlayStatusDecider, GamePlayStatusDeclared(me.id, it.id, played = false)) }
                else -> Unit // "Skip": nothing learned, but don't ask this pair again
            }

            val inputs = inputs(gameDay, me)
            val topUpRound = form["mode"] == "topup"
            val w = wizardFor(topUpRound)
            val round = w.advance(form.round(), WizardPair(a, b, basis), inputs.top(now))
            val next = if (w.shouldStop(round)) null else w.next(inputs.pool, inputs.anchors, inputs.player, round, now, inputs.focus(topUpRound, now))
            if (next == null) return@authenticated redirect("/gamedays/${gameDay.id}/favourites?answers=${round.step}")
            val html = createHTML().main("gf-body gf-wizard") {
                id = "wizard"
                wizardContent(gameDay, next, round, topUpRound)
            }
            respondTextHtml(html)
        }
    }

    get("/gamedays/{id}/favourites") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val inputs = inputs(gameDay, me)
            val now = app.clock.instant()
            val answers = call.parameters["answers"]?.toIntOrNull()
            val favourites = predictor.favourites(inputs.player, inputs.shelf, now)
            val hostFavourites = if (inputs.shelf.isEmpty() && gameDay.host != me.id) {
                val hosts = app.collectionSync.collection(gameDay.host).mapNotNull(app.catalog::get)
                predictor.favourites(inputs.player, hosts, now, count = 2)
            } else emptyList()
            val hostName = login.names(listOf(gameDay.host))[gameDay.host] ?: "the host"
            call.respondHtml {
                page(
                    "Your likely favourites",
                    appBar = { backLink("/gamedays/${gameDay.id}", gameDay.title); appBarTitle(gameDay.title) },
                    dock = {
                        a("/gamedays/${gameDay.id}", classes = "gf-btn gf-btn--primary gf-btn--lg gf-btn--block") { +"Open ${gameDay.title}" }
                        a("/gamedays/${gameDay.id}/taste", classes = "gf-btn gf-btn--ghost") { +"Sharpen it: a few more pairs" }
                    },
                ) {
                    div("gf-stack") {
                        if (answers != null) p("gf-kicker") { +"Round done · $answers answers" }
                        h1("gf-hero") { +if (inputs.shelf.isEmpty()) "Nice taste. Now we need games." else "Your likely favourites today" }
                    }
                    if (inputs.shelf.isEmpty()) {
                        emptyState("The shelf is empty so far", "When people tick what they're bringing, your favourites show up here. Bringing something yourself?") {
                            a("/gamedays/${gameDay.id}/bring", classes = "gf-btn gf-btn--secondary") { icon("plus"); +"Bring games" }
                        }
                        if (hostFavourites.isNotEmpty()) div("gf-notice") {
                            icon("spark")
                            p {
                                +"From $hostName's collection, you'd likely enjoy "
                                hostFavourites.forEachIndexed { i, f -> if (i > 0) +" and "; strong { +f.game.name } }
                                +". Ask them to bring one."
                            }
                        }
                    } else if (favourites.isEmpty()) {
                        emptyState("Not enough answers yet", "A few quick pairs and we'll know what you'll enjoy on this shelf.") {
                            a("/gamedays/${gameDay.id}/taste", classes = "gf-btn gf-btn--secondary") { +"Start the taste round" }
                        }
                    } else ol("gf-podium") {
                        favourites.forEachIndexed { i, f ->
                            li {
                                article(if (i == 0) "gf-game gf-game--large" else "gf-game") {
                                    span("gf-game__rank") { +"${i + 1}" }
                                    art(f.game, if (i == 0) "gf-art--wide" else "")
                                    div("gf-game__body") {
                                        h3("gf-game__name") { +f.game.name }
                                        p("gf-game__meta") { +f.game.facts }
                                        p("gf-game__reason") { icon("spark"); +f.reason }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/** The round travels in hidden fields: step, stable count, asked pairs, current top 3. */
private fun Parameters.round() = RoundState(
    step = this["step"]?.toIntOrNull() ?: 0,
    stableSteps = this["stable"]?.toIntOrNull() ?: 0,
    asked = this["asked"].orEmpty().split(',').filter { it.contains('-') }
        .map { pair -> pair.split('-').mapNotNull { it.toIntOrNull()?.let(::BggId) }.toSet() }.toSet(),
    top = this["top"].orEmpty().split(',').mapNotNull { it.toIntOrNull()?.let(::BggId) },
)

private fun FlowContent.wizardContent(gameDay: GameDaySummary, pair: WizardPair, round: RoundState, topUpRound: Boolean) {
    val total = wizardFor(topUpRound).roundLength
    div("gf-progress") {
        role = "progressbar"
        attributes["aria-label"] = "Taste round"
        attributes["aria-valuemin"] = "0"
        attributes["aria-valuemax"] = "$total"
        attributes["aria-valuenow"] = "${round.step}"
        div("gf-progress__track") { repeat(total) { span(if (it < round.step) "gf-progress__pip gf-progress__pip--done" else "gf-progress__pip") {} } }
        span("gf-progress__count") { +"${round.step + 1} / $total" }
    }
    div("gf-stack") {
        if (pair.basis == Basis.PLAYED) {
            span("gf-tag gf-tag--olive") { attributes["style"] = "align-self: flex-start"; icon("check"); +"You've played both" }
            h1("gf-h1") { +"Which did you enjoy more?" }
        } else {
            span("gf-tag gf-tag--accent") { attributes["style"] = "align-self: flex-start"; icon("unplayed"); +"Unplayed pair" }
            h1("gf-h1") { +"Which would you rather try?" }
        }
    }
    form(classes = "gf-wizard__form") {
        hx("post", "/gamedays/${gameDay.id}/taste")
        hx("target", "#wizard")
        hx("swap", "outerHTML")
        // A and B on the keyboard, as the key hints promise.
        hyperscript("on keydown from window if event.key is 'a' or event.key is 'A' then call #pick-a.click() else if event.key is 'b' or event.key is 'B' then call #pick-b.click() end")
        hidden("a", pair.a.id.toString())
        hidden("b", pair.b.id.toString())
        hidden("basis", pair.basis.name)
        hidden("step", round.step.toString())
        hidden("stable", round.stableSteps.toString())
        hidden("asked", round.asked.joinToString(",") { it.joinToString("-") })
        hidden("top", round.top.joinToString(","))
        hidden("mode", if (topUpRound) "topup" else "full")
        div("gf-duel gf-deal") {
            pick(pair.a, "A")
            span("gf-duel__or") { attributes["aria-hidden"] = "true"; +"or" }
            pick(pair.b, "B")
        }
        div("gf-answers") {
            if (pair.basis == Basis.PLAYED) answer("UNPLAYED", "Haven't played", "unplayed") else answer("SKIP", "Don't know", "unplayed")
            answer("BOTH_MEH", "Both meh", "meh")
            answer("BOTH_GREAT", "Both great", "star", great = true)
        }
    }
}

private fun kotlinx.html.FORM.hidden(name: String, value: String) = input(InputType.hidden, name = name) { this.value = value }

private fun FlowContent.pick(game: Game, key: String) = button(type = ButtonType.submit, classes = "gf-pick") {
    id = "pick-${key.lowercase()}"
    name = "answer"
    value = key
    span("gf-pick__key") { attributes["aria-hidden"] = "true"; +key }
    art(game)
    span("gf-pick__name") { +game.name }
    span("gf-pick__facts") { +game.facts }
    span("gf-spin") { attributes["aria-hidden"] = "true" }
}

private fun FlowContent.answer(value: String, label: String, iconName: String, great: Boolean = false) =
    button(type = ButtonType.submit, classes = if (great) "gf-answer gf-answer--great" else "gf-answer") {
        name = "answer"
        this.value = value
        icon(iconName)
        +label
    }

private suspend fun RoutingContext.respondTextHtml(html: String) =
    call.respondText(html, io.ktor.http.ContentType.Text.Html)

