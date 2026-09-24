package gorefest.core.player

import gorefest.core.BggAccountLinked
import gorefest.core.Event
import gorefest.core.PlayerId
import gorefest.core.eventsourcing.Projection

/** Which BGG username each player linked. */
object BggAccounts : Projection<Map<PlayerId, String>> {
    override val initialState = emptyMap<PlayerId, String>()

    override fun evolve(state: Map<PlayerId, String>, event: Event) =
        if (event is BggAccountLinked) state + (event.player to event.username) else state
}
