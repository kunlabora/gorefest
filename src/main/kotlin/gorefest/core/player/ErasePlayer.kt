package gorefest.core.player

import gorefest.core.Event
import gorefest.core.PlayerErased
import gorefest.core.PlayerId
import gorefest.core.PlayerRegistered
import gorefest.core.Rejection
import gorefest.core.Tags
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.Decision.Accepted
import gorefest.core.eventsourcing.Decision.Rejected
import gorefest.core.eventsourcing.Query
import gorefest.core.typeName

/** The shell has already deleted (or is about to delete) the player's email, name, sessions and collection. */
data class ErasePlayer(val player: PlayerId)

/** Records the erasure once; asking again changes nothing. */
object ErasePlayerDecider : Decider<ErasePlayer, ErasureState, Event> {
    override val initialState = ErasureState(registered = false, erased = false)

    override fun query(command: ErasePlayer) = Query.of(
        Query.Item(setOf(typeName<PlayerRegistered>(), typeName<PlayerErased>()), setOf(Tags.player(command.player))),
    )

    override fun evolve(state: ErasureState, event: Event) = when (event) {
        is PlayerRegistered -> state.copy(registered = true)
        is PlayerErased -> state.copy(erased = true)
        else -> state
    }

    override fun decide(command: ErasePlayer, state: ErasureState): Decision<Event> = when {
        state.erased -> Accepted()
        !state.registered -> Rejected(Rejection.NoSuchPlayer)
        else -> Accepted(PlayerErased(command.player))
    }
}

data class ErasureState(val registered: Boolean, val erased: Boolean)
