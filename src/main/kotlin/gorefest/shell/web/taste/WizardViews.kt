package gorefest.shell.web.taste

import gorefest.core.Basis
import gorefest.core.gameday.GameDaySummary
import gorefest.core.taste.Favourite
import gorefest.core.taste.RoundState
import gorefest.core.taste.WizardPair
import gorefest.shell.web.bgg.collection
import gorefest.shell.web.components.*
import kotlinx.html.*

/** One step of a taste round: the pair, where the round stands, and how long it is. */
data class WizardStep(val gameDay: GameDaySummary, val pair: WizardPair, val round: RoundState, val total: Int, val topUp: Boolean)

fun HTML.wizardPage(step: WizardStep) = page(
    "${step.gameDay.title} · Taste round",
    appBar = {
        appBarTitle("${step.gameDay.title} · Taste round")
        appBarAction("/gamedays/${step.gameDay.id}/favourites", "Done for now")
    },
    mainClasses = "gf-wizard",
    mainId = "wizard",
) { wizardContent(step) }

/** The next step as an htmx fragment: the whole `main#wizard`. */
fun TagConsumer<*>.wizardFragment(step: WizardStep) = main("gf-body gf-wizard") {
    id = "wizard"
    wizardContent(step)
}

private fun FlowContent.wizardContent(step: WizardStep) {
    val (gameDay, pair, round, total) = step
    progress(round.step, total)
    div("gf-stack") {
        if (pair.basis == Basis.PLAYED) {
            span("gf-tag gf-tag--olive") { style = "align-self: flex-start"; icon("check"); +"You've played both" }
            h1("gf-h1") { +"Which did you enjoy more?" }
        } else {
            span("gf-tag gf-tag--accent") { style = "align-self: flex-start"; icon("unplayed"); +"Unplayed pair" }
            h1("gf-h1") { +"Which would you rather try?" }
        }
    }
    form(classes = "gf-wizard__form") {
        hx("post", "/gamedays/${gameDay.id}/taste")
        hx("target", "#wizard")
        hx("swap", "outerHTML")
        // A and B on the keyboard, as the key hints promise.
        hyperscript("on keydown from window if event.key is 'a' or event.key is 'A' then call #pick-a.click() else if event.key is 'b' or event.key is 'B' then call #pick-b.click() end")
        // The round travels in hidden fields: see Parameters.round() in WizardRoutes.
        hiddenInput("a", pair.a.id.toString())
        hiddenInput("b", pair.b.id.toString())
        hiddenInput("basis", pair.basis.name)
        hiddenInput("step", round.step.toString())
        hiddenInput("stable", round.stableSteps.toString())
        hiddenInput("asked", round.asked.joinToString(",") { it.joinToString("-") })
        hiddenInput("top", round.top.joinToString(","))
        hiddenInput("mode", if (step.topUp) "topup" else "full")
        duel(pair.a, pair.b, keyIds = true)
        if (pair.basis == Basis.PLAYED) duelAnswers() else duelAnswers("SKIP", "Don't know")
    }
}

private fun FlowContent.progress(step: Int, total: Int) = div("gf-progress") {
    role = "progressbar"
    attributes["aria-label"] = "Taste round"
    attributes["aria-valuemin"] = "0"
    attributes["aria-valuemax"] = "$total"
    attributes["aria-valuenow"] = "$step"
    div("gf-progress__track") { repeat(total) { span(if (it < step) "gf-progress__pip gf-progress__pip--done" else "gf-progress__pip") {} } }
    span("gf-progress__count") { +"${step + 1} / $total" }
}

/** The payoff: favourites on this shelf, or what to do when there's no shelf or too few answers yet. */
data class Payoff(
    val gameDay: GameDaySummary,
    val answers: Int?,
    val shelfEmpty: Boolean,
    val favourites: List<Favourite>,
    val hostName: String,
    val hostFavourites: List<Favourite>,
)

fun HTML.favouritesPage(view: Payoff) {
    val gameDay = view.gameDay
    page(
        "Your likely favourites",
        appBar = { backLink("/gamedays/${gameDay.id}", gameDay.title); appBarTitle(gameDay.title) },
        dock = {
            primaryLink("/gamedays/${gameDay.id}", "Open ${gameDay.title}")
            linkButton("/gamedays/${gameDay.id}/taste", "Sharpen it: a few more pairs", ButtonStyle.GHOST)
        },
    ) {
        div("gf-stack") {
            if (view.answers != null) p("gf-kicker") { +"Round done · ${view.answers} answers" }
            h1("gf-hero") { +if (view.shelfEmpty) "Nice taste. Now we need games." else "Your likely favourites today" }
        }
        when {
            view.shelfEmpty -> {
                emptyState("The shelf is empty so far", "When people tick what they're bringing, your favourites show up here. Bringing something yourself?") {
                    linkButton("/gamedays/${gameDay.id}/bring", "Bring games", ButtonStyle.SECONDARY, icon = "plus")
                }
                if (view.hostFavourites.isNotEmpty()) notice("spark") {
                    +"From ${view.hostName}'s collection, you'd likely enjoy "
                    view.hostFavourites.forEachIndexed { i, f -> if (i > 0) +" and "; strong { +f.game.name } }
                    +". Ask them to bring one."
                }
            }
            view.favourites.isEmpty() -> emptyState("Not enough answers yet", "A few quick pairs and we'll know what you'll enjoy on this shelf.") {
                linkButton("/gamedays/${gameDay.id}/taste", "Start the taste round", ButtonStyle.SECONDARY)
            }
            else -> ol("gf-podium") { view.favourites.forEachIndexed { i, f -> li { rankedGame(i + 1, f.game, f.reason) } } }
        }
    }
}

