package gorefest.shell.web.gameday

import gorefest.core.gameday.Readiness
import gorefest.shell.App
import gorefest.shell.AppConfig
import gorefest.shell.auth.Login
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.authenticated
import gorefest.shell.web.components.of
import gorefest.shell.web.memberGameDay
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

context(login: Login, config: AppConfig)
fun Route.readinessRoutes(app: App) {
    get("/gamedays/{id}/ready") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me)?.takeIf { it.host == me.id } ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val shelf = app.shelves.of(gameDay.id).mapNotNull { entry -> app.catalog[entry.game]?.let { it to entry } }
            val readiness = Readiness.of(gameDay, shelf, app.tastes)
            val names = login.names(gameDay.players)
            val today = LocalDate.now(app.clock.withZone(runCatching { ZoneId.of(gameDay.timeZone) }.getOrDefault(ZoneId.of("UTC"))))
            val days = ChronoUnit.DAYS.between(today, gameDay.date)
            val view = ReadinessView(gameDay, readiness, names, days, tasteLink = "${config.baseUrl}/gamedays/${gameDay.id}/taste")
            call.respondHtml { readinessPage(view) }
        }
    }
}
