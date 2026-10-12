package gorefest.shell.web.account

import gorefest.core.PlayerId
import gorefest.shell.App
import gorefest.shell.AppConfig
import gorefest.shell.account.RequestKind
import gorefest.shell.auth.EmailChange
import gorefest.shell.auth.Login
import gorefest.shell.web.admin
import gorefest.shell.web.authenticated
import gorefest.shell.web.redirect
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
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
            val view = AccountView(me.name, player?.email.orEmpty(), app.accountRequests.pending(me.id), hostedUpcoming(app, me.id), emailChanged = call.parameters["emailChanged"] == "1")
            call.respondHtml { accountPage(view) }
        }
    }

    get("/account/email") {
        authenticated {
            val me = contextOf<gorefest.shell.web.CurrentPlayer>()
            val pending = app.emailChange.pending(me.id) ?: return@authenticated redirect("/account")
            call.respondHtml { emailCodePage(pending) }
        }
    }

    post("/account/email") {
        authenticated {
            val me = contextOf<gorefest.shell.web.CurrentPlayer>()
            val player = app.db.transaction { login.players.find(me.id) } ?: return@authenticated redirect("/account")
            val error = when (app.emailChange.request(player, call.receiveParameters()["email"].orEmpty())) {
                is EmailChange.Requested.Sent -> return@authenticated redirect("/account/email")
                EmailChange.Requested.Invalid -> "That doesn't look like an email address."
                EmailChange.Requested.SameAsNow -> "That's the address you have already."
                EmailChange.Requested.TooMany -> "That's a lot of codes. Wait a bit, then try again."
                EmailChange.Requested.IsAdminAddress -> "This is the admin address. Change ADMIN_EMAIL on the server first, then change it here."
            }
            val asked = app.accountRequests.pending(me.id)
            call.respondHtml { accountPage(AccountView(me.name, player.email, asked, hostedUpcoming(app, me.id), emailError = error)) }
        }
    }

    post("/account/email/confirm") {
        authenticated {
            val me = contextOf<gorefest.shell.web.CurrentPlayer>()
            when (val confirmed = app.emailChange.confirm(me.id, call.receiveParameters()["code"].orEmpty())) {
                EmailChange.Confirmed.Changed -> redirect("/account?emailChanged=1")
                is EmailChange.Confirmed.WrongCode -> {
                    val pending = app.emailChange.pending(me.id).orEmpty()
                    call.respondHtml { emailCodePage(pending, "That code isn't right. ${confirmed.triesLeft} ${if (confirmed.triesLeft == 1) "try" else "tries"} left.") }
                }
                EmailChange.Confirmed.Failed -> {
                    val pending = app.emailChange.pending(me.id).orEmpty()
                    call.respondHtml { emailCodePage(pending, "That code didn't work. Go back and ask for a new one.") }
                }
            }
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

    get("/admin/requests/{id}/export") {
        admin {
            val id = call.parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }?.let(::PlayerId)
            val file = id?.let { app.personalData.of(it) } ?: return@admin call.respond(HttpStatusCode.NotFound)
            call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "boardfest-my-data.json").toString())
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respondText(file, ContentType.Application.Json)
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

/** Titles of the game days this player hosts that are still to come. */
private fun hostedUpcoming(app: App, me: PlayerId): List<String> {
    val now = app.clock.instant()
    return app.gameDaysById.values
        .filter { it.host == me && !it.closed && !it.date.isBefore(java.time.LocalDate.ofInstant(now, java.time.ZoneId.of(it.timeZone))) }
        .sortedBy { it.date }.map { it.title }
}
