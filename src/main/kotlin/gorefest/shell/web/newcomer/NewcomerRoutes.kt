package gorefest.shell.web.newcomer

import gorefest.core.PlayerId
import gorefest.shell.AppConfig
import gorefest.shell.account.Newcomers
import gorefest.shell.auth.Login
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.admin
import gorefest.shell.web.redirect
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.uuid.Uuid

/** The admin (`ADMIN_EMAIL`) sees who signed up lately and rejects the ones they don't know. */
context(login: Login, config: AppConfig)
fun Route.newcomerRoutes(newcomers: Newcomers) {
    get("/admin/newcomers") {
        admin {
            val latest = newcomers.latest().filterNot { config.isAdmin(it.email) }
            call.respondHtml { newcomersPage(latest) }
        }
    }

    post("/admin/newcomers/{id}/reject") {
        admin {
            val id = call.parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }?.let(::PlayerId)
            // Never the admin's own account: that would lock the admin out.
            if (id != null && id != contextOf<CurrentPlayer>().id) newcomers.reject(id)
            redirect("/admin/newcomers")
        }
    }

    // Links in notices sent before approval was dropped.
    get("/admin/approvals") { admin { redirect("/admin/newcomers") } }
}
