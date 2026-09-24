package gorefest.core.gameday

import gorefest.core.Event
import gorefest.core.GameDayCreated
import gorefest.core.GameDayDetailsChanged
import gorefest.core.GameDayEnded
import gorefest.core.GameDayId
import gorefest.core.PlayerId
import gorefest.core.Tags
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.Decision.Accepted
import gorefest.core.eventsourcing.Decision.Rejected
import gorefest.core.eventsourcing.Query
import gorefest.core.typeName
import java.time.LocalDate

data class EditGameDay(
    val gameDay: GameDayId,
    val editor: PlayerId,
    val title: String,
    val date: LocalDate,
    val place: String,
    /** Today in the game day's time zone, supplied by the shell. */
    val today: LocalDate,
)

object EditGameDayDecider : Decider<EditGameDay, EditGameDayDecider.State, GameDayDetailsChanged> {
    sealed interface State {
        data object Unknown : State
        data class Open(val host: PlayerId, val title: String, val date: LocalDate, val place: String) : State
        data object Ended : State
    }

    override val initialState: State = State.Unknown

    override fun query(command: EditGameDay) = Query.of(
        Query.Item(
            setOf(typeName<GameDayCreated>(), typeName<GameDayDetailsChanged>(), typeName<GameDayEnded>()),
            setOf(Tags.gameDay(command.gameDay)),
        ),
    )

    override fun evolve(state: State, event: Event): State = when (event) {
        is GameDayCreated -> State.Open(event.host, event.title, event.date, event.place)
        is GameDayDetailsChanged -> if (state is State.Open) state.copy(title = event.title, date = event.date, place = event.place) else state
        is GameDayEnded -> State.Ended
        else -> state
    }

    override fun decide(command: EditGameDay, state: State): Decision<GameDayDetailsChanged> = when (state) {
        State.Unknown -> Rejected("No such game day")
        State.Ended -> Rejected("This game day has ended")
        is State.Open -> when {
            command.editor != state.host -> Rejected("Only the host can change this game day")
            command.title.isBlank() -> Rejected("Give the game day a title")
            command.date < command.today -> Rejected("That date is in the past")
            else -> {
                val changed = GameDayDetailsChanged(command.gameDay, command.title.trim(), command.date, command.place.trim())
                if (changed.title == state.title && changed.date == state.date && changed.place == state.place) Accepted()
                else Accepted(changed)
            }
        }
    }
}
