package gorefest.core.gameday

import gorefest.core.Rejection
import gorefest.core.Event
import gorefest.core.GameDayCreated
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
import java.time.LocalDate
import java.time.ZoneId

/** What fits on a card and in the invite preview. */
const val MAX_TITLE_LENGTH = 80
const val MAX_PLACE_LENGTH = 80

data class CreateGameDay(
    val gameDay: GameDayId,
    val host: PlayerId,
    val title: String,
    val date: LocalDate,
    val place: String,
    val timeZone: String,
    /** Today in [timeZone], supplied by the shell. */
    val today: LocalDate,
)

object CreateGameDayDecider : Decider<CreateGameDay, Boolean, Event> {
    /** State: does this game day already exist? */
    override val initialState = false

    override fun query(command: CreateGameDay) = Query.of(
        Query.Item(setOf(typeName<GameDayCreated>()), setOf(Tags.gameDay(command.gameDay))),
    )

    override fun evolve(state: Boolean, event: Event) = state || event is GameDayCreated

    override fun decide(command: CreateGameDay, state: Boolean): Decision<Event> = when {
        state -> Rejected(Rejection.GameDayAlreadyExists)
        command.title.isBlank() -> Rejected(Rejection.TitleMissing)
        command.title.trim().length > MAX_TITLE_LENGTH -> Rejected(Rejection.TitleTooLong)
        command.place.trim().length > MAX_PLACE_LENGTH -> Rejected(Rejection.PlaceTooLong)
        command.date < command.today -> Rejected(Rejection.DateInPast)
        runCatching { ZoneId.of(command.timeZone) }.isFailure -> Rejected(Rejection.UnknownTimeZone)
        else -> with(command) {
            Accepted(
                GameDayCreated(gameDay, host, title.trim(), date, place.trim(), timeZone),
                PlayerJoined(gameDay, host),
            )
        }
    }
}
