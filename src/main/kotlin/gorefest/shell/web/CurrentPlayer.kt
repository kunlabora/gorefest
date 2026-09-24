package gorefest.shell.web

import gorefest.core.PlayerId
import io.ktor.server.routing.RoutingContext
import kotlin.uuid.Uuid

/** The authenticated player, available as a context parameter to handlers that need one. */
data class CurrentPlayer(val id: PlayerId)

/**
 * Runs [block] as the logged-in player.
 * STUB until the auth story lands: everybody is the same dev player.
 */
suspend fun RoutingContext.authenticated(block: suspend context(CurrentPlayer) RoutingContext.() -> Unit) {
    block(CurrentPlayer(DEV_PLAYER), this)
}

val DEV_PLAYER = PlayerId(Uuid.parse("00000000-0000-0000-0000-00000000de00"))
