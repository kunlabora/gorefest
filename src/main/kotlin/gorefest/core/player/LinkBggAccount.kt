package gorefest.core.player

import gorefest.core.Rejection
import gorefest.core.BggAccountLinked
import gorefest.core.PlayerErased
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
object LinkBggAccountDecider : Decider<LinkBggAccount, List<Event>, BggAccountLinked> {
    /** State: every link inside the boundary, and every erasure (an erased player's link no longer holds the name). */
    override val initialState = emptyList<Event>()

    override fun query(command: LinkBggAccount) = Query.of(
        Query.Item(setOf(typeName<BggAccountLinked>()), setOf(Tags.player(command.player))),
        Query.Item(setOf(typeName<BggAccountLinked>()), setOf(Tags.bggUser(command.username))),
        Query.Item(setOf(typeName<PlayerErased>()), emptySet()),
    )

    override fun evolve(state: List<Event>, event: Event) = if (event is BggAccountLinked || event is PlayerErased) state + event else state

    override fun decide(command: LinkBggAccount, state: List<Event>): Decision<BggAccountLinked> {
        val erased = state.filterIsInstance<PlayerErased>().map { it.player }.toSet()
        val links = state.filterIsInstance<BggAccountLinked>().filter { it.player !in erased }
        val mine = links.firstOrNull { it.player == command.player }
        val taken = links.any { it.player != command.player && it.username.equals(command.username, ignoreCase = true) }
        return when {
            mine != null && mine.username.equals(command.username, ignoreCase = true) -> Accepted()
            mine != null -> Rejected(Rejection.AlreadyLinked(mine.username))
            taken -> Rejected(Rejection.BggAccountTaken)
            else -> Accepted(BggAccountLinked(command.player, command.username))
        }
    }
}
