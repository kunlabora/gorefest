package gorefest.core.gameday

import gorefest.core.Event
import gorefest.core.GameDayCancelled
import gorefest.core.GameDayCreated
import gorefest.core.GameDayEnded
import gorefest.core.GameDayId
import gorefest.core.PlayerId
import gorefest.core.Rejection
import gorefest.core.Tags
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.Decision.Accepted
import gorefest.core.eventsourcing.Decision.Rejected
import gorefest.core.eventsourcing.Query
import gorefest.core.typeName

data class CancelGameDay(val gameDay: GameDayId, val by: PlayerId)

/** The host calls the game day off, as long as it hasn't ended. Silent: nobody is mailed. */
object CancelGameDayDecider : Decider<CancelGameDay, CancelGameDayDecider.State, GameDayCancelled> {
    sealed interface State {
        data object Unknown : State
        data class Open(val host: PlayerId) : State
        data object Ended : State
        data object Cancelled : State
    }

    override val initialState: State = State.Unknown

    override fun query(command: CancelGameDay) = Query.of(
        Query.Item(
            setOf(typeName<GameDayCreated>(), typeName<GameDayEnded>(), typeName<GameDayCancelled>()),
            setOf(Tags.gameDay(command.gameDay)),
        ),
    )

    override fun evolve(state: State, event: Event): State = when (event) {
        is GameDayCreated -> State.Open(event.host)
        is GameDayEnded -> State.Ended
        is GameDayCancelled -> State.Cancelled
        else -> state
    }

    override fun decide(command: CancelGameDay, state: State): Decision<GameDayCancelled> = when (state) {
        State.Unknown -> Rejected(Rejection.NoSuchGameDay)
        State.Ended -> Rejected(Rejection.GameDayOver)
        State.Cancelled -> Rejected(Rejection.GameDayCancelled)
        is State.Open ->
            if (command.by != state.host) Rejected(Rejection.OnlyHostCanCancel)
            else Accepted(GameDayCancelled(command.gameDay))
    }
}
