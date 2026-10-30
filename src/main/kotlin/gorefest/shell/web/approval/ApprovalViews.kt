package gorefest.shell.web.approval

import gorefest.shell.auth.Player
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import kotlinx.html.*

context(me: CurrentPlayer)
fun HTML.approvalsPage(waiting: List<Player>) = page("New accounts", site = SiteBar(me, Nav.GAME_DAYS), mainClasses = "gf-landing") {
    wrap {
        div("gf-split__copy") {
            p("gf-kicker") { +"Admin" }
            pageHeading("New accounts")
            p("gf-lede") { +"People who signed up and are waiting to be let in. Only approve people you know." }
        }
    }
    band(sunk = true) {
        div("gf-stack") {
            if (waiting.isEmpty()) emptyState("Nobody is waiting", "New sign-ups show up here, and you get an email for each.")
            else ul("gf-list") { waiting.forEach { row(it) } }
        }
    }
}

/** The email can be long: the buttons get their own line on a phone, and the address breaks where it must. */
private fun UL.row(player: Player) = li("gf-row gf-row--approval") {
    avatar(player.id, player.name)
    span("gf-row__main") {
        span("gf-row__title") { +player.name.orEmpty() }
        span("gf-row__sub") { +player.email }
    }
    div("gf-row__actions") {
        form(action = "/admin/approvals/${player.id.value}/approve", method = FormMethod.post) {
            actionButton("Approve", ButtonStyle.PRIMARY, submit = true)
        }
        form(action = "/admin/approvals/${player.id.value}/reject", method = FormMethod.post) {
            actionButton("Reject", ButtonStyle.GHOST, submit = true)
        }
    }
}
