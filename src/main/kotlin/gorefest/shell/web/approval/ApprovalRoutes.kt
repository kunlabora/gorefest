package gorefest.shell.web.approval

import gorefest.core.PlayerId
import gorefest.shell.AppConfig
import gorefest.shell.auth.Login
import gorefest.shell.web.admin
import gorefest.shell.web.auth.pendingPage
import gorefest.shell.web.redirect
import gorefest.shell.web.safeReturnTo
import gorefest.shell.web.sessionPlayer
import gorefest.shell.web.urlEncoded
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.uuid.Uuid

/** Accounts wait here until the admin (`ADMIN_EMAIL`) lets them in. */
context(login: Login, config: AppConfig)
fun Route.approvalRoutes() {
    get("/pending") {
        val player = sessionPlayer() ?: return@get redirect("/login?returnTo=${"/pending".urlEncoded()}")
        val returnTo = safeReturnTo(call.parameters["returnTo"])
        val name = player.name ?: return@get redirect("/welcome?returnTo=${"/pending".urlEncoded()}")
        if (player.approved) return@get redirect(returnTo)
        call.respondHtml { pendingPage(name, returnTo) }
    }

    get("/admin/approvals") {
        admin {
            val waiting = login.waiting()
            call.respondHtml { approvalsPage(waiting) }
        }
    }

    for ((action, decide) in listOf<Pair<String, suspend (PlayerId) -> Boolean>>("approve" to login::approve, "reject" to login::reject)) {
        post("/admin/approvals/{id}/$action") {
            admin {
                val id = call.parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }?.let(::PlayerId)
                if (id != null) decide(id)
                redirect("/admin/approvals")
            }
        }
    }
}
