package gorefest.core.player

import gorefest.core.Event
import gorefest.core.LoggedIn
import gorefest.core.PlayerId
import gorefest.core.PlayerRegistered
import gorefest.core.Tags
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.Decision.Accepted
import gorefest.core.eventsourcing.Query
import gorefest.core.typeName

/** A player proved they own their email. The shell has already checked the link or code. */
data class LogIn(val player: PlayerId)

object LogInDecider : Decider<LogIn, Boolean, Event> {
    /** State: is this player registered? */
    override val initialState = false

    override fun query(command: LogIn) = Query.of(
        Query.Item(setOf(typeName<PlayerRegistered>()), setOf(Tags.player(command.player))),
    )

    override fun evolve(state: Boolean, event: Event) = state || event is PlayerRegistered

    override fun decide(command: LogIn, state: Boolean): Decision<Event> =
        if (state) Accepted(LoggedIn(command.player))
        else Accepted(PlayerRegistered(command.player), LoggedIn(command.player))
}
