package gorefest.shell.web

import gorefest.shell.AppConfig
import gorefest.shell.auth.Login
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/** The admin (`ADMIN_EMAIL`), or a 404: the page's existence isn't for anyone else to learn. */
context(login: Login, config: AppConfig)
suspend fun RoutingContext.admin(block: suspend context(CurrentPlayer) RoutingContext.() -> Unit) {
    val player = sessionPlayer()
    val name = player?.name
    if (player == null || name == null || !player.approved || !config.isAdmin(player.email)) return call.respond(HttpStatusCode.NotFound)
    block(CurrentPlayer(player.id, name), this)
}
