package gorefest.core.player

import gorefest.core.BggAccountLinked
import gorefest.core.BggAccountLinkedHashed
import gorefest.core.BggAccountUnlinked
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
    /** State: every link, unlink and erasure inside the boundary, in order. */
    override val initialState = emptyList<Event>()

    override fun query(command: LinkBggAccount) = Query.of(
        Query.Item(setOf(typeName<BggAccountLinked>(), typeName<BggAccountLinkedHashed>(), typeName<BggAccountUnlinked>()), setOf(Tags.player(command.player))),
        Query.Item(setOf(typeName<BggAccountLinked>()), setOf(Tags.bggUser(command.username))),
        Query.Item(setOf(typeName<BggAccountLinkedHashed>()), setOf(Tags.bggUserKey(command.usernameKey))),
        // Anyone's unlink or erasure frees their username.
        Query.Item(setOf(typeName<PlayerErased>(), typeName<BggAccountUnlinked>()), emptySet()),
    )

    override fun evolve(state: List<Event>, event: Event) =
        if (event is BggAccountLinked || event is BggAccountLinkedHashed || event is BggAccountUnlinked || event is PlayerErased) state + event else state

    override fun decide(command: LinkBggAccount, state: List<Event>): Decision<BggAccountLinkedHashed> {
        // Whose link still holds: the latest one per player, until they unlink or are erased. Each as "is it this account?".
        val active = mutableMapOf<PlayerId, Boolean>()
        state.forEach { event ->
            when (event) {
                is BggAccountLinked -> active[event.player] = event.username.equals(command.username, ignoreCase = true)
                is BggAccountLinkedHashed -> active[event.player] = event.usernameKey == command.usernameKey
                is BggAccountUnlinked -> active.remove(event.player)
                is PlayerErased -> active.remove(event.player)
                else -> Unit
            }
        }
        val mine = active[command.player]
        val taken = active.any { (player, same) -> player != command.player && same }
        return when {
            mine == true -> Accepted()
            mine == false -> Rejected(Rejection.AlreadyLinked)
            taken -> Rejected(Rejection.BggAccountTaken)
            else -> Accepted(BggAccountLinkedHashed(command.player, command.usernameKey))
        }
    }
}

/** The shell deletes the collection and sync rows once this is recorded. */
data class UnlinkBggAccount(val player: PlayerId)

object UnlinkBggAccountDecider : Decider<UnlinkBggAccount, Boolean, BggAccountUnlinked> {
    /** State: does the player have a link? */
    override val initialState = false

    override fun query(command: UnlinkBggAccount) = Query.of(
        Query.Item(setOf(typeName<BggAccountLinked>(), typeName<BggAccountLinkedHashed>(), typeName<BggAccountUnlinked>()), setOf(Tags.player(command.player))),
    )

    override fun evolve(state: Boolean, event: Event) = when (event) {
        is BggAccountLinked, is BggAccountLinkedHashed -> true
        is BggAccountUnlinked -> false
        else -> state
    }

    override fun decide(command: UnlinkBggAccount, state: Boolean): Decision<BggAccountUnlinked> =
        if (state) Accepted(BggAccountUnlinked(command.player)) else Accepted()
}
