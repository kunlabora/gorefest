package gorefest.shell.web

import gorefest.core.GameDayId
import gorefest.core.gameday.GameDaySummary
import gorefest.shell.App
import io.ktor.server.routing.RoutingContext

/**
 * The game day in the `{id}` path parameter, if [me] joined it. Everyone else gets nothing (404).
 * A cancelled game day is read-only, so it is nothing either, except where [allowCancelled] (the hub, which says so).
 */
fun RoutingContext.memberGameDay(app: App, me: CurrentPlayer, allowCancelled: Boolean = false): GameDaySummary? =
    call.parameters["id"]?.let { runCatching { GameDayId.parse(it) }.getOrNull() }
        ?.let(app.gameDaysById::get)?.takeIf { me.id in it.players && (allowCancelled || !it.cancelled) }
