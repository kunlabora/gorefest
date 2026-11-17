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
 * The shell has checked that BGG knows [username]; nobody proves the account is theirs (U21).
 * [usernameKey] is the keyed hash of the lowercased username (shell: `BggUsernameKey`): it is what the log stores, so
 * the name itself never is. [username] is only here to meet links written before that.
 */
data class LinkBggAccount(val player: PlayerId, val username: String, val usernameKey: String)

/**
 * Only the player's own links count: several players may link the same BGG account (a shared collection), so nobody
 * else's link can get in the way.
 */
object LinkBggAccountDecider : Decider<LinkBggAccount, List<Event>, BggAccountLinkedHashed> {
    /** State: the player's links, unlinks and erasure, in order. */
    override val initialState = emptyList<Event>()

    override fun query(command: LinkBggAccount) = Query.of(
        Query.Item(
            setOf(typeName<BggAccountLinked>(), typeName<BggAccountLinkedHashed>(), typeName<BggAccountUnlinked>(), typeName<PlayerErased>()),
            setOf(Tags.player(command.player)),
        ),
    )

    override fun evolve(state: List<Event>, event: Event) =
        if (event is BggAccountLinked || event is BggAccountLinkedHashed || event is BggAccountUnlinked || event is PlayerErased) state + event else state

    override fun decide(command: LinkBggAccount, state: List<Event>): Decision<BggAccountLinkedHashed> {
        // The link that still holds, as "is it this account?": null when there is none.
        val mine = state.fold(null as Boolean?) { linked, event ->
            when (event) {
                is BggAccountLinked -> event.username.equals(command.username, ignoreCase = true)
                is BggAccountLinkedHashed -> event.usernameKey == command.usernameKey
                is BggAccountUnlinked, is PlayerErased -> null
                else -> linked
            }
        }
        return when (mine) {
            true -> Accepted()
            false -> Rejected(Rejection.AlreadyLinked)
            null -> Accepted(BggAccountLinkedHashed(command.player, command.usernameKey))
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
