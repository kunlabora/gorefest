package gorefest.core.gameday

import gorefest.core.Event
import gorefest.core.GameDayCreated
import gorefest.core.GameDayEnded
import gorefest.core.GameDayId
import gorefest.core.Tags
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.Decision.Accepted
import gorefest.core.eventsourcing.Decision.Rejected
import gorefest.core.eventsourcing.Query
import gorefest.core.typeName
import java.time.Instant
import java.time.ZoneId

data class EndGameDay(val gameDay: GameDayId)

/** Ending twice is a no-op, so the auto-close job can run as often as it likes. */
object EndGameDayDecider : Decider<EndGameDay, EndGameDayDecider.State, GameDayEnded> {
    enum class State { UNKNOWN, OPEN, ENDED }

    override val initialState = State.UNKNOWN

    override fun query(command: EndGameDay) = Query.of(
        Query.Item(setOf(typeName<GameDayCreated>(), typeName<GameDayEnded>()), setOf(Tags.gameDay(command.gameDay))),
    )

    override fun evolve(state: State, event: Event) = when (event) {
        is GameDayCreated -> State.OPEN
        is GameDayEnded -> State.ENDED
        else -> state
    }

    override fun decide(command: EndGameDay, state: State): Decision<GameDayEnded> = when (state) {
        State.UNKNOWN -> Rejected("No such game day")
        State.OPEN -> Accepted(GameDayEnded(command.gameDay))
        State.ENDED -> Accepted()
    }
}

/** Game days still open after midnight at the end of their date, in their own time zone. */
fun Map<GameDayId, GameDaySummary>.dueToEnd(now: Instant): List<GameDayId> = values.filter { gameDay ->
    val zone = runCatching { ZoneId.of(gameDay.timeZone) }.getOrDefault(ZoneId.of("UTC"))
    !gameDay.ended && !now.isBefore(gameDay.date.plusDays(1).atStartOfDay(zone).toInstant())
}.map { it.id }
