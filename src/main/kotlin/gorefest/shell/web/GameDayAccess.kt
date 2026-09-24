package gorefest.shell.web

import gorefest.core.GameDayId
import gorefest.core.gameday.GameDaySummary
import gorefest.shell.App
import io.ktor.server.routing.RoutingContext

/** The game day in the `{id}` path parameter, if [me] joined it. Everyone else gets nothing (404). */
fun RoutingContext.memberGameDay(app: App, me: CurrentPlayer): GameDaySummary? =
    call.parameters["id"]?.let { runCatching { GameDayId.parse(it) }.getOrNull() }
        ?.let(app.gameDays.state::get)?.takeIf { me.id in it.players }
