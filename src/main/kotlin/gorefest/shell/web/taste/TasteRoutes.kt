package gorefest.shell.web.taste

import gorefest.core.Basis
import gorefest.core.BggId
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.GamePlayStatusDeclared
import gorefest.core.game.Game
import gorefest.core.gameday.GameDaySummary
import gorefest.core.taste.DeclarePlayStatusDecider
import gorefest.core.taste.PlayerGames
import gorefest.core.taste.RecordComparisonDecider
import gorefest.core.taste.RoundState
import gorefest.core.taste.WizardPair
import gorefest.shell.App
import gorefest.shell.auth.Login
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.authenticated
import gorefest.shell.web.bgg.collection
import gorefest.shell.web.components.full
import gorefest.shell.web.components.of
import gorefest.shell.web.memberGameDay
import gorefest.shell.web.redirect
import gorefest.shell.web.respondFragment
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** Players with this many answers get a top-up round (a few pairs on what's still uncertain) instead of the full one. */
const val TOP_UP_AFTER = 10

/** The answers that record a comparison; anything else ("haven't played ...") doesn't count as a step of the round. */
private val COMPARISON_ANSWERS = ComparisonResult.entries.map { it.name }.toSet()

private fun App.wizardFor(topUpRound: Boolean) = if (topUpRound) topUpWizard else wizard

/** The games a taste round draws from, and what we know about the player. */
private data class RoundInputs(val pool: List<Game>, val shelf: List<Game>, val anchors: List<Game>, val player: PlayerGames)

context(login: Login)
fun Route.tasteRoutes(app: App) {
    suspend fun inputs(gameDay: GameDaySummary, me: CurrentPlayer): RoundInputs {
        val shelf = app.shelves.of(gameDay.id).map { it.game }
        val hosts = app.collectionSync.collection(gameDay.host)
        val mine = app.collectionSync.collection(me.id)
        val pool = (shelf + hosts + mine).distinct().mapNotNull(app.catalog::get)
        val anchors = app.catalog.all().filter { it.popularityRank != null }.sortedBy { it.popularityRank }.take(30)
        val player = PlayerGames(app.tastes.of(me.id), mine)
        return RoundInputs(pool, shelf.mapNotNull(app.catalog::get), anchors, player)
    }

    fun RoundInputs.top(now: java.time.Instant) = app.predictor.favourites(player, shelf.ifEmpty { pool }, now).map { it.game.id }

    fun RoundInputs.focus(topUpRound: Boolean, now: java.time.Instant) =
        if (topUpRound) app.topUpWizard.uncertain(player, shelf, now).ifEmpty { null } else null

    get("/gamedays/{id}/taste") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val inputs = inputs(gameDay, me)
            val now = app.clock.instant()
            val topUpRound = inputs.player.history.comparisons.size >= TOP_UP_AFTER
            val round = RoundState(top = inputs.top(now))
            val pair = (if (topUpRound) app.topUpWizard.stalePair(inputs.player, app.catalog::get, now) else null)
                ?: app.wizardFor(topUpRound).next(inputs.pool, inputs.anchors, inputs.player, round, now, inputs.focus(topUpRound, now))
                ?: return@authenticated redirect("/gamedays/${gameDay.id}/favourites")
            call.respondHtml { wizardPage(WizardStep(gameDay, pair, round, app.wizardFor(topUpRound).roundLength, topUpRound)) }
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

            val answer = form["answer"]
            when (answer) {
                in COMPARISON_ANSWERS ->
                    app.commands.handle(RecordComparisonDecider, ComparisonRecorded(me.id, a.id, b.id, ComparisonResult.valueOf(answer!!), basis, now))
                // "Haven't played": remembered, so the pair isn't asked again and the matcher knows these are new to them.
                "UNPLAYED" -> listOf(a, b).forEach { app.commands.handle(DeclarePlayStatusDecider, GamePlayStatusDeclared(me.id, it.id, played = false)) }
                "UNPLAYED_A" -> app.commands.handle(DeclarePlayStatusDecider, GamePlayStatusDeclared(me.id, a.id, played = false))
                "UNPLAYED_B" -> app.commands.handle(DeclarePlayStatusDecider, GamePlayStatusDeclared(me.id, b.id, played = false))
                else -> Unit
            }

            val inputs = inputs(gameDay, me)
            val topUpRound = form["mode"] == "topup"
            val w = app.wizardFor(topUpRound)
            val round = w.advance(form.round(), WizardPair(a, b, basis), inputs.top(now), learned = answer in COMPARISON_ANSWERS)
            val next = if (w.shouldStop(round)) null else w.next(inputs.pool, inputs.anchors, inputs.player, round, now, inputs.focus(topUpRound, now))
            if (next == null) return@authenticated redirect("/gamedays/${gameDay.id}/favourites?answers=${round.step}")
            respondFragment { wizardFragment(WizardStep(gameDay, next, round, w.roundLength, topUpRound)) }
        }
    }

    get("/gamedays/{id}/favourites") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val inputs = inputs(gameDay, me)
            val now = app.clock.instant()
            val answers = call.parameters["answers"]?.toIntOrNull()
            val favourites = app.predictor.favourites(inputs.player, inputs.shelf, now)
            val hostFavourites = if (inputs.shelf.isEmpty() && gameDay.host != me.id) {
                val hosts = app.collectionSync.collection(gameDay.host).mapNotNull(app.catalog::get)
                app.predictor.favourites(inputs.player, hosts, now, count = 2)
            } else emptyList()
            val hostName = login.names(listOf(gameDay.host))[gameDay.host] ?: "the host"
            call.respondHtml { favouritesPage(Payoff(gameDay, answers, inputs.shelf.isEmpty(), favourites, hostName, hostFavourites)) }
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
