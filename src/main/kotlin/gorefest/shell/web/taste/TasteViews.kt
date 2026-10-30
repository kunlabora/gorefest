package gorefest.shell.web.taste

import gorefest.core.taste.FavouriteReason
import gorefest.core.Basis
import gorefest.core.gameday.GameDaySummary
import gorefest.core.taste.Favourite
import gorefest.core.taste.RoundState
import gorefest.core.taste.WizardPair
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import kotlinx.html.*

/** One step of a taste round: the pair, where the round stands, and how long it is. */
data class WizardStep(val gameDay: GameDaySummary, val pair: WizardPair, val round: RoundState, val total: Int, val topUp: Boolean)

fun HTML.wizardPage(me: CurrentPlayer, step: WizardStep) = page(
    "${step.gameDay.title} · Taste round",
    site = SiteBar(me, Nav.GAME_DAYS),
    appBar = { appBarAction("/gamedays/${step.gameDay.id}/favourites", "Done for now") },
    mainClasses = "gf-wizard gf-wrap",
    mainId = "wizard",
) { wizardContent(step) }

/** The next step as an htmx fragment: the whole `main#wizard`. */
fun TagConsumer<*>.wizardFragment(step: WizardStep) = main("gf-body gf-wizard gf-wrap") {
    id = "wizard"
    announce("Next: ${step.pair.a.name} or ${step.pair.b.name}?")
    wizardContent(step)
}

/** Copy on the left (kicker, progress, basis, the question, key hints), the pair and its answers on the right; stacked on a phone. */
private fun FlowContent.wizardContent(step: WizardStep) {
    val (gameDay, pair, round, total) = step
    split("gf-wiz") {
        div("gf-split__copy gf-wiz__copy") {
            p("gf-kicker") { +"${gameDay.title} · ${if (step.topUp) "Top-up round" else "Taste round"}" }
            progressSteps(round.progress.coerceAtMost(total - 1), total, "Taste round")
            if (pair.basis == Basis.PLAYED) {
                span("gf-tag gf-tag--olive gf-tag--start") { icon("check"); +"You've played both" }
                heading(Level.H1, "gf-h1 gf-wiz__question") { +"Which did you enjoy more?" }
            } else {
                span("gf-tag gf-tag--accent gf-tag--start") { icon("unplayed"); +"Unplayed pair" }
                heading(Level.H1, "gf-h1 gf-wiz__question") { +"Which would you rather try?" }
            }
            p("gf-wiz__keys") {
                kbd { +"A" }; kbd { +"B" }; +" to pick · "; kbd { +"1" }; +" both great · "; kbd { +"2" }; +" both meh"
            }
        }
        form(classes = "gf-wizard__form") {
            hx("post", "/gamedays/${gameDay.id}/taste")
            hx("target", "#wizard")
            hx("swap", "outerHTML")
            // A and B pick, 1 and 2 answer "both great" and "both meh", as the key hints promise.
            hyperscript("on keydown from window if event.repeat or event.metaKey or event.ctrlKey or event.altKey or event.target.matches('input, textarea, select, [contenteditable]') then exit end if event.key is 'a' or event.key is 'A' then call #pick-a.click() else if event.key is 'b' or event.key is 'B' then call #pick-b.click() else if event.key is '1' then call #answer-great.click() else if event.key is '2' then call #answer-meh.click() end")
            // The round travels in hidden fields: see Parameters.round() in TasteRoutes.
            hiddenInput("a", pair.a.id.toString())
            hiddenInput("b", pair.b.id.toString())
            hiddenInput("basis", pair.basis.name)
            hiddenInput("step", round.step.toString())
            hiddenInput("stable", round.stableSteps.toString())
            hiddenInput("asked", round.asked.joinToString(",") { it.joinToString("-") })
            hiddenInput("top", round.top.joinToString(","))
            hiddenInput("mode", if (step.topUp) "topup" else "full")
            duel(pair.a, pair.b, keyIds = true)
            duelUnplayed(pair.a, pair.b)
            duelAnswers("Haven't played either", keyIds = true)
        }
    }
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

fun HTML.favouritesPage(me: CurrentPlayer, view: Payoff) {
    val gameDay = view.gameDay
    page("Your likely favourites", site = SiteBar(me, Nav.GAME_DAYS), mainClasses = "gf-fav-page gf-wrap") {
        split("gf-pay") {
            div("gf-split__copy") {
                crumb("/gamedays/${gameDay.id}", gameDay.title)
                if (view.answers != null) p("gf-kicker") { +"Round done · ${view.answers} ${if (view.answers == 1) "answer" else "answers"}" }
                pageHeading(if (view.shelfEmpty) "Nice taste. Now we need games." else "Your likely favourites today")
                if (view.favourites.isNotEmpty() && !view.shelfEmpty) p("gf-lede") { +"Based on how you picked, these are the games from ${gameDay.title}'s shelf that should suit you best." }
                div("gf-pay__cta") {
                    linkButton("/gamedays/${gameDay.id}", "Open ${gameDay.title}", ButtonStyle.PRIMARY, ButtonSize.LARGE)
                    linkButton("/gamedays/${gameDay.id}/taste", "Sharpen it: a few more pairs", ButtonStyle.GHOST)
                }
            }
            div("gf-pay__side") {
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
                    else -> ol("gf-podium gf-tilted") { view.favourites.forEachIndexed { i, f -> li("gf-card gf-sample gf-fav") { favourite(i + 1, f) } } }
                }
            }
        }
    }
}

/** One favourite as the login's sample card, with real art, its rank and the reason. */
private fun LI.favourite(rank: Int, f: Favourite) {
    span("gf-game__rank") { +"$rank" }
    art(f.game)
    div {
        heading(Level.H2, "gf-sample__name") { bggNameLink(f.game) }
        p("gf-fav__reason") { icon("spark"); +f.reason.text() }
    }
}

/** The one-line reason under a favourite. */
private fun FavouriteReason.text(): String = when (this) {
    FavouriteReason.CalledItGreat -> "You called it great."
    is FavouriteReason.PickedOver -> "You picked it over ${games.take(2).joinToString(" and ")}."
    FavouriteReason.JustAGuess -> "Just a guess so far: it hasn't won a pair yet."
}
