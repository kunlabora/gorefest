package gorefest.shell.web

import gorefest.core.gameday.Gap
import gorefest.core.gameday.Readiness
import gorefest.core.gameday.TasteStatus
import gorefest.shell.App
import gorefest.shell.AppConfig
import gorefest.shell.auth.Login
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.ButtonType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.ul
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

context(login: Login, config: AppConfig)
fun Route.readinessRoutes(app: App) {
    get("/gamedays/{id}/ready") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me)?.takeIf { it.host == me.id } ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val shelf = app.shelves.state[gameDay.id].orEmpty().mapNotNull { entry -> app.catalog[entry.game]?.let { it to entry } }
            val readiness = Readiness.of(gameDay, shelf, app.tastes.state)
            val names = login.names(gameDay.players)
            val today = LocalDate.now(app.clock.withZone(runCatching { ZoneId.of(gameDay.timeZone) }.getOrDefault(ZoneId.of("UTC"))))
            val days = ChronoUnit.DAYS.between(today, gameDay.date)
            val tasteLink = "${config.baseUrl}/gamedays/${gameDay.id}/taste"
            call.respondHtml {
                page("How ready are we?", appBar = { backLink("/gamedays/${gameDay.id}", gameDay.title); appBarTitle("${gameDay.title} · Host") }) {
                    div("gf-stack") {
                        p("gf-kicker") {
                            +when {
                                days > 1 -> "$days days to go"
                                days == 1L -> "Tomorrow"
                                days == 0L -> "Today"
                                else -> "Over"
                            }
                        }
                        h1("gf-h1") { +"How ready are we?" }
                    }
                    div("gf-stats") {
                        stat("${readiness.joined}", "joined")
                        stat("${readiness.shelf}", "on the shelf")
                        stat("${readiness.tasteReady}/${readiness.joined}", "taste ready")
                    }
                    readiness.gaps.lastOrNull()?.let { gap -> div("gf-notice") { icon("users"); p { gapText(gap) } } }
                    if (readiness.shelf == 0) div("gf-notice") { icon("dice"); p { strong { +"The shelf is empty." }; +" Tick what you're bringing and ask the others to do the same." } }
                    section("gf-section") {
                        h2("gf-h2") { +"Players" }
                        ul("gf-list") {
                            readiness.players.forEach { p ->
                                val name = names[p.player] ?: "Unknown player"
                                li("gf-row") {
                                    avatar(p.player, names[p.player])
                                    span("gf-row__main") {
                                        span("gf-row__title") { +name }
                                        span("gf-row__sub") {
                                            +when (p.status) {
                                                TasteStatus.NONE -> "Joined, no answers yet"
                                                TasteStatus.THIN -> "${p.answers} ${if (p.answers == 1) "answer" else "answers"}: picks will be guesses"
                                                TasteStatus.READY -> "${p.answers} answers · brought ${p.brought}"
                                            }
                                        }
                                    }
                                    if (p.status == TasteStatus.READY) span("gf-tag gf-tag--olive") { icon("check"); +"Ready" }
                                    else if (p.player != me.id) button(type = ButtonType.button, classes = "gf-btn gf-btn--secondary gf-btn--sm") {
                                        // No email invites: the host pastes this into the chat.
                                        attributes["data-message"] = "Hey ${name.substringBefore(' ')}, got 2 minutes before ${gameDay.title}? " +
                                            "Tell the app what you like, so it can pick games you'll enjoy: $tasteLink"
                                        attributes["aria-label"] = "Copy a nudge for $name"
                                        hyperscript("on click call navigator.clipboard.writeText(@data-message) then put 'Copied' into me")
                                        +"Nudge"
                                    }
                                }
                            }
                        }
                        p("gf-hint") { +"Nudge copies a friendly message with a link to the taste round. Paste it in the chat." }
                    }
                }
            }
        }
    }
}

private fun kotlinx.html.FlowContent.stat(number: String, label: String) = div("gf-stat") {
    span("gf-stat__num") { +number }
    +" " // the spans are blocks; screen readers and text extraction need the space
    span("gf-stat__label") { +label }
}

private fun kotlinx.html.P.gapText(gap: Gap) {
    strong { +"Nothing plays well at ${gap.players}." }
    when (gap.games.size) {
        0 -> +" No game on the shelf suits ${gap.players} players. Ask for one that does, or plan to split tables."
        1 -> +" Only ${gap.games[0].name} works for ${gap.players}. Ask for another ${gap.players}-player game."
        else -> +" Only ${gap.games.joinToString(" and ") { it.name }} work for ${gap.players}. Ask for another ${gap.players}-player game."
    }
}
