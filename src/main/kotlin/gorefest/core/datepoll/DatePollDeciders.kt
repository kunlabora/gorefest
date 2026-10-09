package gorefest.core.datepoll

import gorefest.core.DatePollAnswered
import gorefest.core.DatePollClosed
import gorefest.core.DatePollCreated
import gorefest.core.DatePollId
import gorefest.core.DatePollJoined
import gorefest.core.Event
import gorefest.core.GameDayCreated
import gorefest.core.GameDayId
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.Rejection
import gorefest.core.Tags
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.Decision.Accepted
import gorefest.core.eventsourcing.Decision.Rejected
import gorefest.core.eventsourcing.Query
import gorefest.core.gameday.MAX_PLACE_LENGTH
import gorefest.core.gameday.MAX_TITLE_LENGTH
import gorefest.core.typeName
import java.time.LocalDate
import java.time.ZoneId

/** A poll needs a choice to make, and a screen to show it on. */
const val MIN_POLL_DATES = 2
const val MAX_POLL_DATES = 14

private fun pollLifecycle(poll: DatePollId) =
    Query.Item(setOf(typeName<DatePollCreated>(), typeName<DatePollClosed>()), setOf(Tags.datePoll(poll)))

private fun playerIn(poll: DatePollId, player: PlayerId, type: String) =
    Query.Item(setOf(type), setOf(Tags.datePoll(poll), Tags.player(player)))

data class CreateDatePoll(
    val poll: DatePollId,
    val host: PlayerId,
    val title: String,
    val place: String,
    val dates: List<LocalDate>,
    val timeZone: String,
    /** Today in [timeZone], supplied by the shell. */
    val today: LocalDate,
)

object CreateDatePollDecider : Decider<CreateDatePoll, Boolean, Event> {
    /** State: does this poll already exist? */
    override val initialState = false

    override fun query(command: CreateDatePoll) = Query.of(Query.Item(setOf(typeName<DatePollCreated>()), setOf(Tags.datePoll(command.poll))))

    override fun evolve(state: Boolean, event: Event) = state || event is DatePollCreated

    override fun decide(command: CreateDatePoll, state: Boolean): Decision<Event> {
        val dates = command.dates.distinct().sorted()
        return when {
            state -> Rejected(Rejection.DatePollAlreadyExists)
            command.title.isBlank() -> Rejected(Rejection.TitleMissing)
            command.title.trim().length > MAX_TITLE_LENGTH -> Rejected(Rejection.TitleTooLong)
            command.place.trim().length > MAX_PLACE_LENGTH -> Rejected(Rejection.PlaceTooLong)
            dates.size < MIN_POLL_DATES -> Rejected(Rejection.TooFewDates)
            dates.size > MAX_POLL_DATES -> Rejected(Rejection.TooManyDates)
            dates.any { it < command.today } -> Rejected(Rejection.DateInPast)
            runCatching { ZoneId.of(command.timeZone) }.isFailure -> Rejected(Rejection.UnknownTimeZone)
            else -> with(command) {
                Accepted(DatePollCreated(poll, host, title.trim(), place.trim(), dates, timeZone), DatePollJoined(poll, host))
            }
        }
    }
}

data class JoinDatePoll(val poll: DatePollId, val player: PlayerId)

/** The boundary is the poll's lifecycle plus *this player's* join, so players joining together never conflict. */
object JoinDatePollDecider : Decider<JoinDatePoll, JoinDatePollDecider.State, DatePollJoined> {
    sealed interface State {
        data object Unknown : State
        data class Open(val joined: Boolean) : State
        data object Closed : State
    }

    override val initialState: State = State.Unknown

    override fun query(command: JoinDatePoll) = Query.of(pollLifecycle(command.poll), playerIn(command.poll, command.player, typeName<DatePollJoined>()))

    override fun evolve(state: State, event: Event): State = when (event) {
        is DatePollCreated -> State.Open(joined = false)
        is DatePollJoined -> if (state is State.Open) State.Open(joined = true) else state
        is DatePollClosed -> State.Closed
        else -> state
    }

    override fun decide(command: JoinDatePoll, state: State): Decision<DatePollJoined> = when (state) {
        State.Unknown -> Rejected(Rejection.NoSuchDatePoll)
        State.Closed -> Rejected(Rejection.DatePollClosed)
        is State.Open -> if (state.joined) Accepted() else Accepted(DatePollJoined(command.poll, command.player))
    }
}

data class AnswerDatePoll(val poll: DatePollId, val player: PlayerId, val available: Set<LocalDate>)

/** Only this player's own join and answer are inside the boundary besides the lifecycle, so everyone can answer at once. */
object AnswerDatePollDecider : Decider<AnswerDatePoll, AnswerDatePollDecider.State, DatePollAnswered> {
    sealed interface State {
        data object Unknown : State
        data class Open(val dates: Set<LocalDate>, val joined: Boolean, val previous: Set<LocalDate>?) : State
        data object Closed : State
    }

    override val initialState: State = State.Unknown

    override fun query(command: AnswerDatePoll) = Query.of(
        pollLifecycle(command.poll),
        playerIn(command.poll, command.player, typeName<DatePollJoined>()),
        playerIn(command.poll, command.player, typeName<DatePollAnswered>()),
    )

    override fun evolve(state: State, event: Event): State = when (event) {
        is DatePollCreated -> State.Open(event.dates.toSet(), joined = false, previous = null)
        is DatePollJoined -> if (state is State.Open) state.copy(joined = true) else state
        is DatePollAnswered -> if (state is State.Open) state.copy(previous = event.available.toSet()) else state
        is DatePollClosed -> State.Closed
        else -> state
    }

    override fun decide(command: AnswerDatePoll, state: State): Decision<DatePollAnswered> = when (state) {
        State.Unknown -> Rejected(Rejection.NoSuchDatePoll)
        State.Closed -> Rejected(Rejection.DatePollClosed)
        is State.Open -> when {
            !state.joined -> Rejected(Rejection.NotInDatePoll)
            !state.dates.containsAll(command.available) -> Rejected(Rejection.NotAnOption)
            state.previous == command.available -> Accepted()
            else -> Accepted(DatePollAnswered(command.poll, command.player, command.available.sorted()))
        }
    }
}

data class PickDate(
    val poll: DatePollId,
    val host: PlayerId,
    val date: LocalDate,
    /** The game day to create for it. */
    val gameDay: GameDayId,
    /** Today in the poll's time zone, supplied by the shell. */
    val today: LocalDate,
)

/**
 * The host picks the date: the poll closes and the game day appears, with the host and everyone who
 * said they can make that date already in. The boundary is the whole poll, so a late answer makes the
 * pick run again against the new answers.
 */
object PickDateDecider : Decider<PickDate, PickDateDecider.State, Event> {
    sealed interface State {
        data object Unknown : State
        data class Open(val created: DatePollCreated, val answers: Map<PlayerId, Set<LocalDate>>) : State
        data object Closed : State
    }

    override val initialState: State = State.Unknown

    override fun query(command: PickDate) = Query.of(
        Query.Item(setOf(typeName<DatePollCreated>(), typeName<DatePollAnswered>(), typeName<DatePollClosed>()), setOf(Tags.datePoll(command.poll))),
    )

    override fun evolve(state: State, event: Event): State = when (event) {
        is DatePollCreated -> State.Open(event, emptyMap())
        is DatePollAnswered -> if (state is State.Open) state.copy(answers = state.answers + (event.player to event.available.toSet())) else state
        is DatePollClosed -> State.Closed
        else -> state
    }

    override fun decide(command: PickDate, state: State): Decision<Event> = when (state) {
        State.Unknown -> Rejected(Rejection.NoSuchDatePoll)
        State.Closed -> Rejected(Rejection.DatePollClosed)
        is State.Open -> {
            val poll = state.created
            when {
                command.host != poll.host -> Rejected(Rejection.OnlyHostCanPickDate)
                command.date !in poll.dates -> Rejected(Rejection.NotAnOption)
                command.date < command.today -> Rejected(Rejection.DateInPast)
                else -> {
                    val coming = state.answers.filterValues { command.date in it }.keys.filter { it != poll.host }
                    Accepted(
                        listOf(
                            DatePollClosed(command.poll, command.date, command.gameDay),
                            GameDayCreated(command.gameDay, poll.host, poll.title, command.date, poll.place, poll.timeZone),
                            PlayerJoined(command.gameDay, poll.host),
                        ) + coming.map { PlayerJoined(command.gameDay, it) },
                    )
                }
            }
        }
    }
}
