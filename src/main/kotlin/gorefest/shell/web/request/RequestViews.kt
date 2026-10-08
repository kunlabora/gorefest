package gorefest.shell.web.request

import gorefest.core.PlayerId
import gorefest.core.game.Game
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.MAX_BRING_REQUESTS
import gorefest.core.gameday.RequestStatus
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import kotlinx.html.*

/** What the ask button of one game says: ask, already asked, answered, or turned down. */
enum class AskState { CAN_ASK, ASKED, BRINGING, DECLINED }

/** One game in somebody's collection, as the ask screen shows it. */
data class AskRow(val game: Game, val owner: PlayerId, val ownerName: String, val state: AskState)

/** Somebody who can be asked: they joined the game day and allow requests. */
data class AskMember(val id: PlayerId, val name: String)

/** A request of mine, with how it went. */
data class MyRequest(val game: Game, val owner: PlayerId, val ownerName: String, val status: RequestStatus)

/** A game I'm asked to bring, and who asks. */
data class OwnerRequest(val game: Game, val askers: List<Pair<PlayerId, String>>)

data class AskView(val members: List<AskMember>, val mine: List<MyRequest>)

/** "Bram", "Bram and Caro", "Bram, Caro and Dirk". */
fun List<String>.joinNames(): String = when (size) {
    0 -> ""
    1 -> first()
    else -> dropLast(1).joinToString(", ") + " and " + last()
}

/** Pick a member and see their collection, or search for a game to see who has it. */
context(me: CurrentPlayer)
fun HTML.askPage(gameDay: GameDaySummary, view: AskView) = page("Ask someone to bring a game", site = SiteBar(me, Nav.GAME_DAYS), mainClasses = "gf-landing") {
    wrap {
        div("gf-split__copy gf-ask") {
            crumb("/gamedays/${gameDay.id}", gameDay.title)
            p("gf-kicker") { +"Ask someone" }
            h1("gf-h1 gf-h1--landing") { +"Who could bring what?" }
            p("gf-lede gf-muted") { +"Fancy a game you know somebody owns? Ask them to bring it. They answer with one tap, and you can ask up to $MAX_BRING_REQUESTS." }
            div("gf-stack") {
                searchField(
                    placeholder = "Search for a game someone has", label = "Search for a game someone has",
                    url = "/gamedays/${gameDay.id}/ask-search", target = "#ask-results",
                )
                consumer.askResults(gameDay, emptyList(), searched = false)
            }
            consumer.myRequests(gameDay, view.mine)
        }
    }
    band(sunk = true) {
        div("gf-stack") {
            h2("gf-h2") { +"Or look through someone's collection" }
            if (view.members.isEmpty()) emptyState("Nobody to ask yet", "Once friends join this game day, you can ask them to bring a game.")
            else ul("gf-list") {
                view.members.forEach { member ->
                    li("gf-row") {
                        avatar(member.id, member.name)
                        span("gf-row__main") { span("gf-row__title") { +member.name } }
                        linkButton("/gamedays/${gameDay.id}/ask/from/${member.id}", "See games", ButtonStyle.SECONDARY, ButtonSize.SMALL)
                    }
                }
            }
        }
    }
}

/** One member's collection, filtered by name, each game with its ask button. */
context(me: CurrentPlayer)
fun HTML.askMemberPage(gameDay: GameDaySummary, owner: AskMember, rows: List<AskRow>) =
    page("Ask ${owner.name}", site = SiteBar(me, Nav.GAME_DAYS), mainClasses = "gf-landing") {
        wrap {
            div("gf-split__copy gf-ask") {
                crumb("/gamedays/${gameDay.id}/ask", "Ask someone")
                p("gf-kicker") { +"Ask ${owner.name}" }
                h1("gf-h1 gf-h1--landing") { +"What could ${owner.name.substringBefore(' ')} bring?" }
                searchField(
                    placeholder = "Filter ${owner.name.substringBefore(' ')}'s games", label = "Filter games",
                    url = "/gamedays/${gameDay.id}/ask/from/${owner.id}/games", target = "#ask-list",
                )
                consumer.askList(gameDay, rows, named = false)
            }
        }
    }

/** The games of one member under the filter box. */
fun TagConsumer<*>.askList(gameDay: GameDaySummary, rows: List<AskRow>, named: Boolean) = ul("gf-list") {
    id = "ask-list"
    if (rows.isEmpty()) li { p("gf-small gf-muted") { +"No games to show." } }
    rows.forEach { askRow(gameDay, it, named) }
}

/** Games matching a search, each with who has it. Empty until something is searched. */
fun TagConsumer<*>.askResults(gameDay: GameDaySummary, rows: List<AskRow>, searched: Boolean) = div("gf-stack") {
    id = "ask-results"
    if (searched && rows.isEmpty()) p("gf-small gf-muted") { +"Nobody on this game day has that one." }
    if (rows.isNotEmpty()) ul("gf-list") { rows.forEach { askRow(gameDay, it, named = true) } }
}

/** One game and the ask button of its owner. With [named], the owner's name leads the facts (the search lists several owners). */
fun TagConsumer<*>.askRow(gameDay: GameDaySummary, row: AskRow, named: Boolean, error: String? = null) = li {
    val rowId = "ask-${row.owner}-${row.game.id}"
    id = rowId
    val meta = if (named) "${row.ownerName} · ${row.game.facts}" else row.game.facts
    gameRow(row.game, meta) {
        when (row.state) {
            AskState.CAN_ASK -> button(type = ButtonType.button, classes = buttonClasses(ButtonStyle.SECONDARY, ButtonSize.SMALL)) {
                attributes["aria-label"] = "Ask ${row.ownerName} to bring ${row.game.name}"
                hx("post", "/gamedays/${gameDay.id}/ask")
                hx("vals", """{"owner": "${row.owner}", "game": "${row.game.id}", "named": "$named"}""")
                hx("target", "#$rowId")
                hx("swap", "outerHTML")
                +"Ask"
            }
            AskState.ASKED -> span("gf-tag gf-tag--olive") { icon("check"); +"Asked" }
            AskState.BRINGING -> span("gf-tag gf-tag--olive") { icon("check"); +"Bringing it" }
            AskState.DECLINED -> span("gf-small gf-muted") { +"Not this time" }
        }
    }
    if (error != null) fieldError(error)
}

/** My requests with how they went, and a way to take an open one back. Fetched again when I ask. */
fun TagConsumer<*>.myRequests(gameDay: GameDaySummary, rows: List<MyRequest>) = section("gf-section") {
    id = "my-requests"
    hx("get", "/gamedays/${gameDay.id}/ask/mine")
    hx("trigger", "$REQUESTS_CHANGED from:body")
    hx("swap", "outerHTML")
    if (rows.isNotEmpty()) {
        h2("gf-h2") { +"Your requests" }
        ul("gf-list") {
            rows.forEach { row ->
                li("gf-row") {
                    id = "mine-${row.owner}-${row.game.id}"
                    span("gf-row__main") {
                        span("gf-row__title") { +row.game.name }
                        span("gf-row__sub") {
                            +when (row.status) {
                                RequestStatus.OPEN -> "Asked ${row.ownerName}"
                                RequestStatus.ANSWERED -> "${row.ownerName} is bringing it"
                                RequestStatus.DECLINED -> "Not this time"
                            }
                        }
                    }
                    if (row.status == RequestStatus.OPEN) button(type = ButtonType.button, classes = buttonClasses(ButtonStyle.GHOST, ButtonSize.SMALL)) {
                        attributes["aria-label"] = "Take back the request to ${row.ownerName} for ${row.game.name}"
                        hx("post", "/gamedays/${gameDay.id}/ask/withdraw")
                        hx("vals", """{"owner": "${row.owner}", "game": "${row.game.id}"}""")
                        hx("target", "#my-requests")
                        hx("swap", "outerHTML")
                        +"Take it back"
                    }
                    else if (row.status == RequestStatus.ANSWERED) span("gf-tag gf-tag--olive") { icon("check"); +"Bringing it" }
                }
            }
        }
    }
}

/** Sent by the server (HX-Trigger) when I asked someone, so my list of requests picks it up. */
const val REQUESTS_CHANGED = "requests-changed"

/**
 * On the bring page, above the collection: the games people ask me to bring, each with "Bring it" and "Not this time".
 * Always present (empty when nothing is asked) so it can be swapped; fetched again when the shelf changes.
 */
fun TagConsumer<*>.bringRequests(gameDay: GameDaySummary, rows: List<OwnerRequest>) = section("gf-section") {
    id = "bring-requests"
    hx("get", "/gamedays/${gameDay.id}/ask/requests")
    hx("trigger", "shelf-changed from:body")
    hx("swap", "outerHTML")
    if (rows.isNotEmpty()) {
        h2("gf-h2") { +"People are asking" }
        ul("gf-list") {
            rows.forEach { row ->
                val names = row.askers.map { it.second }
                li("gf-row") {
                    id = "request-${row.game.id}"
                    avatarStack(row.askers.map { it.first }, row.askers.toMap(), max = 3)
                    span("gf-row__main") {
                        span("gf-row__title") { +"${names.joinNames()} ${if (names.size == 1) "asks" else "ask"} if you can bring ${row.game.name}" }
                        span("gf-row__sub") { +row.game.facts }
                    }
                    button(type = ButtonType.button, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.SMALL)) {
                        attributes["aria-label"] = "Bring ${row.game.name}"
                        hx("post", "/gamedays/${gameDay.id}/shelf/${row.game.id}")
                        hx("vals", """{"bringing": "true", "row": "request"}""")
                        hx("target", "#bring-requests")
                        hx("swap", "outerHTML")
                        +"Bring it"
                    }
                    button(type = ButtonType.button, classes = buttonClasses(ButtonStyle.GHOST, ButtonSize.SMALL)) {
                        attributes["aria-label"] = "Not this time: ${row.game.name}"
                        hx("post", "/gamedays/${gameDay.id}/ask/decline")
                        hx("vals", """{"game": "${row.game.id}"}""")
                        hx("target", "#bring-requests")
                        hx("swap", "outerHTML")
                        +"Not this time"
                    }
                }
            }
        }
    }
}

/** The collection page's switch: may people on my game days ask me to bring games? */
fun TagConsumer<*>.bringRequestsSwitch(allowed: Boolean) = div("gf-stack") {
    id = "bring-requests-switch"
    label("gf-row") {
        span("gf-row__main") {
            span("gf-row__title") { +"Let people on my game days ask me to bring games" }
            span("gf-row__sub") {
                +"Only people on a game day you joined see your collection, and only to ask you for a game. You always get to say \"Not this time\"."
            }
        }
        input(InputType.checkBox, classes = "gf-switch", name = "allowed") {
            attributes["role"] = "switch"
            value = "true"
            checked = allowed
            attributes["aria-label"] = "Let people on my game days ask me to bring games"
            hx("post", "/collection/bring-requests")
            hx("trigger", "change")
            hx("vals", """{"allowed": "${!allowed}"}""")
            hx("target", "#bring-requests-switch")
            hx("swap", "outerHTML")
        }
    }
}
