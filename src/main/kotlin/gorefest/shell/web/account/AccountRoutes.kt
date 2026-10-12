package gorefest.shell.web.account

import gorefest.core.PlayerId
import gorefest.shell.App
import gorefest.shell.AppConfig
import gorefest.shell.account.RequestKind
import gorefest.shell.auth.Login
import gorefest.shell.web.admin
import gorefest.shell.web.authenticated
import gorefest.shell.web.redirect
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.uuid.Uuid

/** The person's side: see the account and ask for their data or for it to be deleted. */
context(login: Login, config: AppConfig)
fun Route.accountRoutes(app: App) {
    get("/account") {
        authenticated {
            val me = contextOf<gorefest.shell.web.CurrentPlayer>()
            val player = login.players.let { app.db.transaction { it.find(me.id) } }
            val today = app.clock.instant()
            val hosting = app.gameDaysById.values
                .filter { it.host == me.id && !it.closed && !it.date.isBefore(java.time.LocalDate.ofInstant(today, java.time.ZoneId.of(it.timeZone))) }
                .sortedBy { it.date }.map { it.title }
            val view = AccountView(me.name, player?.email.orEmpty(), app.accountRequests.pending(me.id), hosting)
            call.respondHtml { accountPage(view) }
        }
    }

    for (kind in RequestKind.entries) {
        post("/account/${kind.name.lowercase()}") {
            authenticated {
                app.accountRequests.ask(contextOf<gorefest.shell.web.CurrentPlayer>().id, kind)
                redirect("/account#request-${kind.name.lowercase()}")
            }
        }
    }

    // The admin's side.
    get("/admin/requests") {
        admin {
            val open = app.accountRequests.open()
            call.respondHtml { requestsPage(open) }
        }
    }

    post("/admin/requests/{id}/erase") {
        admin {
            val id = call.parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }?.let(::PlayerId)
            if (id != null) app.accountRequests.erase(id)
            redirect("/admin/requests")
        }
    }

    post("/admin/requests/{id}/export/done") {
        admin {
            val id = call.parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }?.let(::PlayerId)
            if (id != null) app.accountRequests.done(id, RequestKind.EXPORT)
            redirect("/admin/requests")
        }
    }
}
