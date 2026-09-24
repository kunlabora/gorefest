package gorefest.core.player

import gorefest.core.BggAccountLinked
import gorefest.core.Event
import gorefest.core.PlayerId
import gorefest.core.Tags
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.Decision.Accepted
import gorefest.core.eventsourcing.Decision.Rejected
import gorefest.core.eventsourcing.Query
import gorefest.core.typeName

/** The shell has already seen [username]'s BGG profile carry this player's verification code. */
data class LinkBggAccount(val player: PlayerId, val username: String)

/**
 * The boundary spans two tags: links of this player, and links of this username. So two players
 * racing for the same username conflict, and nobody else does.
 */
object LinkBggAccountDecider : Decider<LinkBggAccount, List<BggAccountLinked>, BggAccountLinked> {
    /** State: every link inside the boundary. */
    override val initialState = emptyList<BggAccountLinked>()

    override fun query(command: LinkBggAccount) = Query.of(
        Query.Item(setOf(typeName<BggAccountLinked>()), setOf(Tags.player(command.player))),
        Query.Item(setOf(typeName<BggAccountLinked>()), setOf(Tags.bggUser(command.username))),
    )

    override fun evolve(state: List<BggAccountLinked>, event: Event) = if (event is BggAccountLinked) state + event else state

    override fun decide(command: LinkBggAccount, state: List<BggAccountLinked>): Decision<BggAccountLinked> {
        val mine = state.firstOrNull { it.player == command.player }
        val taken = state.any { it.player != command.player && it.username.equals(command.username, ignoreCase = true) }
        return when {
            mine != null && mine.username.equals(command.username, ignoreCase = true) -> Accepted()
            mine != null -> Rejected("You've already linked ${mine.username}")
            taken -> Rejected("That BGG account is already linked to another player")
            else -> Accepted(BggAccountLinked(command.player, command.username))
        }
    }
}
