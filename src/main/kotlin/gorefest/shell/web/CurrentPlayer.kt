package gorefest.shell.web

import gorefest.core.PlayerId
import gorefest.shell.auth.Login
import gorefest.shell.auth.Player
import io.ktor.server.routing.RoutingContext

/** The logged-in player, available as a context parameter to handlers that need one. */
data class CurrentPlayer(val id: PlayerId, val name: String)

const val SESSION_COOKIE = "gf_session"

/**
 * Runs [block] as the logged-in player. Without a session it sends the browser to log in;
 * a player without a name yet goes through the welcome step first, and one the admin hasn't approved yet waits on the pending page.
 * All come back here afterwards.
 */
context(login: Login)
suspend fun RoutingContext.authenticated(block: suspend context(CurrentPlayer) RoutingContext.() -> Unit) {
    val player = sessionPlayer() ?: return redirect("/login?returnTo=${currentPage().urlEncoded()}")
    val name = player.name ?: return redirect("/welcome?returnTo=${currentPage().urlEncoded()}")
    if (!player.approved) return redirect("/pending?returnTo=${currentPage().urlEncoded()}")
    block(CurrentPlayer(player.id, name), this)
}

context(login: Login)
suspend fun RoutingContext.sessionPlayer(): Player? =
    call.request.cookies[SESSION_COOKIE]?.let { login.currentPlayer(it) }
