package gorefest.core.gameday

import gorefest.core.Rejection
import gorefest.core.Event
import gorefest.core.GameDayCreated
import gorefest.core.GameDayEnded
import gorefest.core.GameDayId
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.Tags
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.Decision.Accepted
import gorefest.core.eventsourcing.Decision.Rejected
import gorefest.core.eventsourcing.Query
import gorefest.core.typeName

data class JoinGameDay(val gameDay: GameDayId, val player: PlayerId)

/**
 * The boundary is the game day's lifecycle plus *this player's* join only,
 * so different players joining at the same time never conflict.
 */
object JoinGameDayDecider : Decider<JoinGameDay, JoinGameDayDecider.State, PlayerJoined> {
    sealed interface State {
        data object Unknown : State
        data class Open(val joined: Boolean) : State
        data object Ended : State
    }

    override val initialState: State = State.Unknown

    override fun query(command: JoinGameDay) = Query.of(
        Query.Item(
            types = setOf(typeName<GameDayCreated>(), typeName<GameDayEnded>()),
            tags = setOf(Tags.gameDay(command.gameDay)),
        ),
        Query.Item(
            types = setOf(typeName<PlayerJoined>()),
            tags = setOf(Tags.gameDay(command.gameDay), Tags.player(command.player)),
        ),
    )

    override fun evolve(state: State, event: Event): State = when (event) {
        is GameDayCreated -> State.Open(joined = false)
        is PlayerJoined -> if (state is State.Open) State.Open(joined = true) else state
        is GameDayEnded -> State.Ended
        else -> state
    }

    override fun decide(command: JoinGameDay, state: State): Decision<PlayerJoined> = when (state) {
        State.Unknown -> Rejected(Rejection.NoSuchGameDay)
        State.Ended -> Rejected(Rejection.GameDayOver)
        is State.Open ->
            if (state.joined) Accepted()
            else Accepted(PlayerJoined(command.gameDay, command.player))
    }
}
