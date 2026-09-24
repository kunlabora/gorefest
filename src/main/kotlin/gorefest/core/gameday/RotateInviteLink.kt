package gorefest.core.gameday

import gorefest.core.Event
import gorefest.core.GameDayCreated
import gorefest.core.GameDayEnded
import gorefest.core.GameDayId
import gorefest.core.InviteLinkRotated
import gorefest.core.PlayerId
import gorefest.core.Tags
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.Decision.Accepted
import gorefest.core.eventsourcing.Decision.Rejected
import gorefest.core.eventsourcing.Query
import gorefest.core.typeName

data class RotateInviteLink(val gameDay: GameDayId, val by: PlayerId)

object RotateInviteLinkDecider : Decider<RotateInviteLink, RotateInviteLinkDecider.State, InviteLinkRotated> {
    sealed interface State {
        data object Unknown : State
        data class Open(val host: PlayerId, val version: Int) : State
        data object Ended : State
    }

    override val initialState: State = State.Unknown

    override fun query(command: RotateInviteLink) = Query.of(
        Query.Item(
            setOf(typeName<GameDayCreated>(), typeName<InviteLinkRotated>(), typeName<GameDayEnded>()),
            setOf(Tags.gameDay(command.gameDay)),
        ),
    )

    override fun evolve(state: State, event: Event): State = when (event) {
        is GameDayCreated -> State.Open(event.host, version = 0)
        is InviteLinkRotated -> if (state is State.Open) state.copy(version = event.version) else state
        is GameDayEnded -> State.Ended
        else -> state
    }

    override fun decide(command: RotateInviteLink, state: State): Decision<InviteLinkRotated> = when (state) {
        State.Unknown -> Rejected("No such game day")
        State.Ended -> Rejected("This game day has ended")
        is State.Open ->
            if (command.by != state.host) Rejected("Only the host can make a new invite link")
            else Accepted(InviteLinkRotated(command.gameDay, state.version + 1))
    }
}
