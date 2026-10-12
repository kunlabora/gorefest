package gorefest.core.player

import gorefest.core.BggAccountLinked
import gorefest.core.BggAccountLinkedHashed
import gorefest.core.Event
import gorefest.core.PlayerErased
import gorefest.core.PlayerId
import gorefest.core.Rejection
import gorefest.core.Tags
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.Decision.Accepted
import gorefest.core.eventsourcing.Decision.Rejected
import gorefest.core.eventsourcing.Query
import gorefest.core.typeName

/**
 * The shell has already seen [username]'s BGG profile carry this player's verification code.
 * [usernameKey] is the keyed hash of the lowercased username (shell: `BggVerification.usernameKey`): it is what
 * the log stores, so the name itself never is. [username] is only here to meet links written before that.
 */
data class LinkBggAccount(val player: PlayerId, val username: String, val usernameKey: String)

/**
 * The boundary spans two tags: links of this player, and links of this username (under its old plain tag and its
 * hashed one). So two players racing for the same username conflict, and nobody else does.
 */
object LinkBggAccountDecider : Decider<LinkBggAccount, List<Event>, BggAccountLinkedHashed> {
    /** State: every link inside the boundary, and every erasure (an erased player's link no longer holds the name). */
    override val initialState = emptyList<Event>()

    override fun query(command: LinkBggAccount) = Query.of(
        Query.Item(setOf(typeName<BggAccountLinked>(), typeName<BggAccountLinkedHashed>()), setOf(Tags.player(command.player))),
        Query.Item(setOf(typeName<BggAccountLinked>()), setOf(Tags.bggUser(command.username))),
        Query.Item(setOf(typeName<BggAccountLinkedHashed>()), setOf(Tags.bggUserKey(command.usernameKey))),
        Query.Item(setOf(typeName<PlayerErased>()), emptySet()),
    )

    override fun evolve(state: List<Event>, event: Event) =
        if (event is BggAccountLinked || event is BggAccountLinkedHashed || event is PlayerErased) state + event else state

    override fun decide(command: LinkBggAccount, state: List<Event>): Decision<BggAccountLinkedHashed> {
        val erased = state.filterIsInstance<PlayerErased>().map { it.player }.toSet()
        // Each link as (who, is it this account?), whichever way it was written.
        val links = state.mapNotNull { event ->
            when (event) {
                is BggAccountLinked -> event.player to event.username.equals(command.username, ignoreCase = true)
                is BggAccountLinkedHashed -> event.player to (event.usernameKey == command.usernameKey)
                else -> null
            }
        }.filter { (player, _) -> player !in erased }
        val mine = links.firstOrNull { (player, _) -> player == command.player }
        val taken = links.any { (player, same) -> player != command.player && same }
        return when {
            mine != null && mine.second -> Accepted()
            mine != null -> Rejected(Rejection.AlreadyLinked)
            taken -> Rejected(Rejection.BggAccountTaken)
            else -> Accepted(BggAccountLinkedHashed(command.player, command.usernameKey))
        }
    }
}
