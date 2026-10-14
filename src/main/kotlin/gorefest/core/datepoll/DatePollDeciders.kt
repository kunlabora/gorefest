package gorefest.core.datepoll

import gorefest.core.BggId
import gorefest.core.DatePollAnswered
import gorefest.core.DatePollBringAnswered
import gorefest.core.DatePollClosed
import gorefest.core.DatePollCreated
import gorefest.core.DatePollGameAnswered
import gorefest.core.DatePollGameProposed
import gorefest.core.DatePollGameWithdrawn
import gorefest.core.DatePollId
import gorefest.core.DatePollJoined
import gorefest.core.DatePollPickedForGameNight
import gorefest.core.Event
import gorefest.core.GameDayCreated
import gorefest.core.GameDayId
import gorefest.core.GameNightCreated
import gorefest.core.GameNightId
import gorefest.core.PlayerErased
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
import gorefest.core.gameday.MAX_WISHES
import gorefest.core.typeName
import java.time.LocalDate
import java.time.ZoneId

/** A poll needs a choice to make, and a screen to show it on. */
const val MIN_POLL_DATES = 2
const val MAX_POLL_DATES = 14

/** How many games a poll can ask about: with three yeses each, more only spreads the wishes thin. */
const val MAX_POLL_GAMES = 12

private fun pollLifecycle(poll: DatePollId) =
    Query.Item(setOf(typeName<DatePollCreated>(), typeName<DatePollClosed>(), typeName<DatePollPickedForGameNight>()), setOf(Tags.datePoll(poll)))

/** Erasures are rare and carry no poll tag: a decision that reads the whole poll takes them all in (BUG-027). */
private val erasures = Query.Item(setOf(typeName<PlayerErased>()), emptySet())

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
    /** Ask which games people would play too (U11): the pick then makes a game night. */
    val asksAboutGames: Boolean = false,
    /** The games the host ticked on the form, put to the poll right away (ignored without [asksAboutGames]). */
    val games: List<BggId> = emptyList(),
)

object CreateDatePollDecider : Decider<CreateDatePoll, Boolean, Event> {
    /** State: does this poll already exist? */
    override val initialState = false

    override fun query(command: CreateDatePoll) = Query.of(Query.Item(setOf(typeName<DatePollCreated>()), setOf(Tags.datePoll(command.poll))))

    override fun evolve(state: Boolean, event: Event) = state || event is DatePollCreated

    override fun decide(command: CreateDatePoll, state: Boolean): Decision<Event> {
        val dates = command.dates.distinct().sorted()
        val games = if (command.asksAboutGames) command.games.distinct() else emptyList()
        return when {
            state -> Rejected(Rejection.DatePollAlreadyExists)
            command.title.isBlank() -> Rejected(Rejection.TitleMissing)
            command.title.trim().length > MAX_TITLE_LENGTH -> Rejected(Rejection.TitleTooLong)
            command.place.trim().length > MAX_PLACE_LENGTH -> Rejected(Rejection.PlaceTooLong)
            dates.size < MIN_POLL_DATES -> Rejected(Rejection.TooFewDates)
            dates.size > MAX_POLL_DATES -> Rejected(Rejection.TooManyDates)
            dates.any { it < command.today } -> Rejected(Rejection.DateInPast)
            runCatching { ZoneId.of(command.timeZone) }.isFailure -> Rejected(Rejection.UnknownTimeZone)
            games.size > MAX_POLL_GAMES -> Rejected(Rejection.TooManyPollGames)
            else -> with(command) {
                Accepted(
                    listOf(DatePollCreated(poll, host, title.trim(), place.trim(), dates, timeZone, asksAboutGames), DatePollJoined(poll, host)) +
                        games.map { DatePollGameProposed(poll, it) },
                )
            }
        }
    }
}

data class JoinDatePoll(val poll: DatePollId, val player: PlayerId)

/** The boundary is the poll's lifecycle plus *this player's* join, so players joining together never conflict. */
object JoinDatePollDecider : Decider<JoinDatePoll, JoinDatePollDecider.State, DatePollJoined> {
    sealed interface State {
        data object Unknown : State
        data class Open(val host: PlayerId, val joined: Boolean) : State
        data object Closed : State
        /** The host's account was erased: nobody can pick a date anymore. */
        data object HostGone : State
    }

    override val initialState: State = State.Unknown

    override fun query(command: JoinDatePoll) = Query.of(pollLifecycle(command.poll), playerIn(command.poll, command.player, typeName<DatePollJoined>()), erasures)

    override fun evolve(state: State, event: Event): State = when (event) {
        is DatePollCreated -> State.Open(event.host, joined = false)
        is DatePollJoined -> if (state is State.Open) state.copy(joined = true) else state
        is DatePollClosed, is DatePollPickedForGameNight -> State.Closed
        is PlayerErased -> if (state is State.Open && event.player == state.host) State.HostGone else state
        else -> state
    }

    override fun decide(command: JoinDatePoll, state: State): Decision<DatePollJoined> = when (state) {
        State.Unknown -> Rejected(Rejection.NoSuchDatePoll)
        State.Closed -> Rejected(Rejection.DatePollClosed)
        State.HostGone -> Rejected(Rejection.DatePollHostGone)
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
        is DatePollClosed, is DatePollPickedForGameNight -> State.Closed
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
    /** The game night to create instead, when the poll asks about games. */
    val gameNight: GameNightId = GameNightId(gameDay.value),
)

/** Everything a pick depends on. Joins aren't: who comes is told by the answers, so someone merely joining never makes the pick run again. */
private val pollTypes = setOf(
    typeName<DatePollCreated>(), typeName<DatePollAnswered>(), typeName<DatePollClosed>(), typeName<DatePollPickedForGameNight>(),
    typeName<DatePollGameProposed>(), typeName<DatePollGameWithdrawn>(), typeName<DatePollGameAnswered>(), typeName<DatePollBringAnswered>(),
)

/** A whole poll as a decision's state: null until it exists. */
private fun evolvePoll(state: DatePollSummary?, event: Event): DatePollSummary? =
    if (event is DatePollCreated) DatePollSummary.of(event) else state?.after(event)

/**
 * The host picks the date: the poll closes and the game day appears, with the host and everyone who
 * said they can make that date already in. A poll that asks about games makes a game night instead, with
 * the plan for that date (see [DatePollSummary.planFor]). The boundary is the whole poll, so a late answer
 * or offer makes the pick run again against it.
 */
object PickDateDecider : Decider<PickDate, DatePollSummary?, Event> {
    override val initialState: DatePollSummary? = null

    override fun query(command: PickDate) = Query.of(Query.Item(pollTypes, setOf(Tags.datePoll(command.poll))), erasures)

    override fun evolve(state: DatePollSummary?, event: Event) = evolvePoll(state, event)

    override fun decide(command: PickDate, state: DatePollSummary?): Decision<Event> = when {
        state == null -> Rejected(Rejection.NoSuchDatePoll)
        state.hostGone -> Rejected(Rejection.DatePollHostGone)
        !state.open -> Rejected(Rejection.DatePollClosed)
        command.host != state.host -> Rejected(Rejection.OnlyHostCanPickDate)
        command.date !in state.dates -> Rejected(Rejection.NotAnOption)
        command.date < command.today -> Rejected(Rejection.DateInPast)
        state.asksAboutGames -> {
            val night = state.planFor(command.date)
            Accepted(
                DatePollPickedForGameNight(command.poll, command.date, command.gameNight),
                GameNightCreated(
                    command.gameNight, command.poll, state.host, state.title, command.date, state.place, state.timeZone,
                    players = state.coming(command.date), plan = night.plan, missing = night.missing,
                ),
            )
        }
        else -> {
            val coming = state.coming(command.date).filter { it != state.host }
            Accepted(
                listOf(
                    DatePollClosed(command.poll, command.date, command.gameDay),
                    GameDayCreated(command.gameDay, state.host, state.title, command.date, state.place, state.timeZone),
                    PlayerJoined(command.gameDay, state.host),
                ) + coming.map { PlayerJoined(command.gameDay, it) },
            )
        }
    }
}

/** The host puts a game to the poll ([proposed]) or takes it off. */
data class ProposePollGame(val poll: DatePollId, val host: PlayerId, val game: BggId, val proposed: Boolean = true)

/** Only the host, only while the poll is open, and only in a poll that asks about games. The boundary is the poll's lifecycle and its list of games. */
object ProposePollGameDecider : Decider<ProposePollGame, DatePollSummary?, Event> {
    override val initialState: DatePollSummary? = null

    override fun query(command: ProposePollGame) = Query.of(
        pollLifecycle(command.poll),
        Query.Item(setOf(typeName<DatePollGameProposed>(), typeName<DatePollGameWithdrawn>()), setOf(Tags.datePoll(command.poll))),
        erasures,
    )

    override fun evolve(state: DatePollSummary?, event: Event) = evolvePoll(state, event)

    override fun decide(command: ProposePollGame, state: DatePollSummary?): Decision<Event> = when {
        state == null -> Rejected(Rejection.NoSuchDatePoll)
        state.hostGone -> Rejected(Rejection.DatePollHostGone)
        !state.open -> Rejected(Rejection.DatePollClosed)
        !state.asksAboutGames -> Rejected(Rejection.PollAsksNoGames)
        command.host != state.host -> Rejected(Rejection.OnlyHostProposesGames)
        command.proposed == (command.game in state.proposals) -> Accepted()
        !command.proposed -> Accepted(DatePollGameWithdrawn(command.poll, command.game))
        state.proposals.size >= MAX_POLL_GAMES -> Rejected(Rejection.TooManyPollGames)
        else -> Accepted(DatePollGameProposed(command.poll, command.game))
    }
}

/** [player] would ([yes]) or wouldn't play [game]. */
data class AnswerPollGame(val poll: DatePollId, val player: PlayerId, val game: BggId, val yes: Boolean)

/** [player] will bring [game] if it's played ([bringing]), or not. */
data class AnswerBring(val poll: DatePollId, val player: PlayerId, val game: BggId, val bringing: Boolean)

/** The boundary for one player's answer about games: the lifecycle, the poll's games, and this player's own join and answers. */
private fun playerGameQuery(poll: DatePollId, player: PlayerId, answerType: String) = Query.of(
    pollLifecycle(poll),
    Query.Item(setOf(typeName<DatePollGameProposed>(), typeName<DatePollGameWithdrawn>()), setOf(Tags.datePoll(poll))),
    playerIn(poll, player, typeName<DatePollJoined>()),
    playerIn(poll, player, answerType),
    erasures,
)

private fun DatePollSummary?.cantAnswer(player: PlayerId, game: BggId): Rejection? = when {
    this == null -> Rejection.NoSuchDatePoll
    hostGone -> Rejection.DatePollHostGone
    !open -> Rejection.DatePollClosed
    player !in participants -> Rejection.NotInDatePoll
    game !in proposals -> Rejection.NotInPoll
    else -> null
}

/** Everyone answers at once without conflicts: only their own answers are in the boundary. A yes is a wish, so at most [MAX_WISHES]. */
object AnswerPollGameDecider : Decider<AnswerPollGame, DatePollSummary?, DatePollGameAnswered> {
    override val initialState: DatePollSummary? = null
    override fun query(command: AnswerPollGame) = playerGameQuery(command.poll, command.player, typeName<DatePollGameAnswered>())
    override fun evolve(state: DatePollSummary?, event: Event) = evolvePoll(state, event)

    override fun decide(command: AnswerPollGame, state: DatePollSummary?): Decision<DatePollGameAnswered> {
        state.cantAnswer(command.player, command.game)?.let { return Rejected(it) }
        val previous = state!!.gameAnswers[command.player]?.get(command.game)
        return when {
            previous == command.yes -> Accepted()
            command.yes && state.yesCount(command.player) >= MAX_WISHES -> Rejected(Rejection.TooManyWishes)
            else -> with(command) { Accepted(DatePollGameAnswered(poll, player, game, yes)) }
        }
    }
}

/** Anyone in the poll can offer to bring a game, collection or not (U11); only their own offers are in the boundary. */
object AnswerBringDecider : Decider<AnswerBring, DatePollSummary?, DatePollBringAnswered> {
    override val initialState: DatePollSummary? = null
    override fun query(command: AnswerBring) = playerGameQuery(command.poll, command.player, typeName<DatePollBringAnswered>())
    override fun evolve(state: DatePollSummary?, event: Event) = evolvePoll(state, event)

    override fun decide(command: AnswerBring, state: DatePollSummary?): Decision<DatePollBringAnswered> {
        state.cantAnswer(command.player, command.game)?.let { return Rejected(it) }
        return if (state!!.bringAnswers[command.player]?.get(command.game) == command.bringing) Accepted()
        else with(command) { Accepted(DatePollBringAnswered(poll, player, game, bringing)) }
    }
}
