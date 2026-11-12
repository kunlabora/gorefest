package gorefest.shell.web.newcomer

import gorefest.shell.auth.Player
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import kotlinx.html.*

context(me: CurrentPlayer)
fun HTML.newcomersPage(latest: List<Player>) = page("New accounts", site = SiteBar(me, Nav.GAME_DAYS), mainClasses = "gf-landing") {
    wrap {
        div("gf-split__copy") {
            p("gf-kicker") { +"Admin" }
            pageHeading("New accounts")
            p("gf-lede") { +"The latest sign-ups, newest first. Everyone is in at once: reject the people you don't know." }
        }
    }
    band(sunk = true) {
        div("gf-stack") {
            if (latest.isEmpty()) emptyState("Nobody yet", "New sign-ups show up here, and you hear about each one.")
            else ul("gf-list") { latest.forEach { row(it) } }
        }
    }
}

/** The email can be long: the button gets its own line on a phone, and the address breaks where it must. */
private fun UL.row(player: Player) = li("gf-row gf-row--approval") {
    avatar(player.id, player.name)
    span("gf-row__main") {
        span("gf-row__title") { +player.name.orEmpty() }
        span("gf-row__sub") { +player.email }
    }
    div("gf-row__actions") {
        form(action = "/admin/newcomers/${player.id.value}/reject", method = FormMethod.post) {
            confirmOnSubmit("Reject ${player.name ?: "this account"}? Their account is erased for good.")
            actionButton("Reject", ButtonStyle.GHOST, submit = true, says = "Reject ${player.name ?: player.email}")
        }
    }
}
