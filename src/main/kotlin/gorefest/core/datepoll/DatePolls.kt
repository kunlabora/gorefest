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
import gorefest.core.GameDayId
import gorefest.core.GameNightId
import gorefest.core.PlannedGame
import gorefest.core.PlayerId
import gorefest.core.eventsourcing.Projection
import java.time.LocalDate

/** The date the host picked, and the game day (or, for a poll with games, the game night) made from it. */
data class PickedDate(val date: LocalDate, val gameDay: GameDayId? = null, val gameNight: GameNightId? = null)

/** Who can make one of a poll's dates. */
data class DateTally(val date: LocalDate, val available: List<PlayerId>)

/** What a game night on one date would get: the games someone coming brings, and the ones nobody coming can bring. */
data class NightPlan(val plan: List<PlannedGame>, val missing: List<BggId>)

/** A date poll as the people in it see it. */
data class DatePollSummary(
    val id: DatePollId,
    val host: PlayerId,
    val title: String,
    val place: String,
    /** The options, earliest first. */
    val dates: List<LocalDate>,
    val timeZone: String,
    /** In the order they joined: the host first. */
    val participants: List<PlayerId>,
    /** Each answering player's available dates (anyone not in here hasn't answered). */
    val answers: Map<PlayerId, Set<LocalDate>> = emptyMap(),
    val picked: PickedDate? = null,
    /** The poll asks which games people would play too (U11). */
    val asksAboutGames: Boolean = false,
    /** The games the host put to the poll, in the order they were added. */
    val proposals: List<BggId> = emptyList(),
    /** Each player's yes (true) or no per game. */
    val gameAnswers: Map<PlayerId, Map<BggId, Boolean>> = emptyMap(),
    /** Each player's "I'll bring it" (true) or "not this time" per game: whether they've been asked shows here too. */
    val bringAnswers: Map<PlayerId, Map<BggId, Boolean>> = emptyMap(),
    /** Who offered to bring each game, in the order they offered. */
    val offers: Map<BggId, List<PlayerId>> = emptyMap(),
) {
    val open get() = picked == null

    /** Participants who haven't answered yet. */
    val waitingOn: List<PlayerId> get() = participants.filter { it !in answers }

    fun tallies(): List<DateTally> = dates.map { date -> DateTally(date, participants.filter { answers[it]?.contains(date) == true }) }

    /** The date the most people can make; ties go to the earliest. Null while nobody can make any. */
    fun best(): LocalDate? =
        tallies().filter { it.available.isNotEmpty() }
            .maxWithOrNull(compareBy<DateTally> { it.available.size }.thenByDescending { it.date })?.date

    /**
     * Who would be there on [date]: the host, whatever they ticked, and everyone who said they can make it, in the order they first answered.
     * It reads answers only, not joins, so the pick's boundary can leave joins out.
     */
    fun coming(date: LocalDate): List<PlayerId> = (listOf(host) + answers.filterValues { date in it }.keys).distinct()

    /** Who said yes to [game], in the order they first answered about games. */
    fun yesFor(game: BggId): List<PlayerId> = gameAnswers.filterValues { it[game] == true }.keys.toList()

    /** Who said no to [game]. */
    fun noFor(game: BggId): List<PlayerId> = gameAnswers.filterValues { it[game] == false }.keys.toList()

    /** Who offered to bring [game], first offer first. */
    fun bringers(game: BggId): List<PlayerId> = offers[game].orEmpty()

    /** How many of the poll's games [player] said yes to: a yes is a wish, so it counts against [gorefest.core.gameday.MAX_WISHES]. */
    fun yesCount(player: PlayerId): Int = proposals.count { gameAnswers[player]?.get(it) == true }

    /** Whether [player] still has to say if they'd bring [game]. */
    fun bringUnanswered(player: PlayerId, game: BggId) = bringAnswers[player]?.containsKey(game) != true

    /**
     * The game night [date] would make: each game goes to the first one who offered to bring it and can come, with
     * the yeses of those coming; a game nobody coming offered to bring is missing.
     */
    fun planFor(date: LocalDate): NightPlan {
        val coming = coming(date).toSet()
        val (planned, missing) = proposals.partition { game -> bringers(game).any { it in coming } }
        return NightPlan(
            plan = planned.map { game -> PlannedGame(game, bringers(game).first { it in coming }, yesFor(game).filter { it in coming }) },
            missing = missing,
        )
    }

    /** This poll after [event]; events about other polls or other things leave it as it is. */
    fun after(event: Event): DatePollSummary = when {
        event is DatePollJoined && event.poll == id -> if (event.player in participants) this else copy(participants = participants + event.player)
        event is DatePollAnswered && event.poll == id -> copy(answers = answers + (event.player to event.available.toSet()))
        event is DatePollClosed && event.poll == id -> copy(picked = PickedDate(event.date, gameDay = event.gameDay))
        event is DatePollPickedForGameNight && event.poll == id -> copy(picked = PickedDate(event.date, gameNight = event.gameNight))
        event is DatePollGameProposed && event.poll == id -> if (event.game in proposals) this else copy(proposals = proposals + event.game)
        // A game taken off takes its answers and offers with it: putting it back starts it fresh.
        event is DatePollGameWithdrawn && event.poll == id -> copy(
            proposals = proposals - event.game,
            gameAnswers = gameAnswers.mapValues { (_, a) -> a - event.game },
            bringAnswers = bringAnswers.mapValues { (_, a) -> a - event.game },
            offers = offers - event.game,
        )
        event is DatePollGameAnswered && event.poll == id ->
            copy(gameAnswers = gameAnswers + (event.player to (gameAnswers[event.player].orEmpty() + (event.game to event.yes))))
        event is DatePollBringAnswered && event.poll == id -> {
            val others = bringers(event.game) - event.player
            copy(
                bringAnswers = bringAnswers + (event.player to (bringAnswers[event.player].orEmpty() + (event.game to event.bringing))),
                // Offering again after taking it back puts you at the end of the queue.
                offers = offers + (event.game to if (event.bringing) bringers(event.game).takeIf { event.player in it } ?: (others + event.player) else others),
            )
        }
        else -> this
    }

    companion object {
        fun of(event: DatePollCreated) = DatePollSummary(
            event.poll, event.host, event.title, event.place, event.dates.sorted(), event.timeZone, participants = emptyList(), asksAboutGames = event.asksAboutGames,
        )
    }
}

/** The date polls, by id; [forPlayer] and [of] are the questions callers ask. */
class DatePollBoard private constructor(private val byId: Map<DatePollId, DatePollSummary>) {
    fun of(poll: DatePollId): DatePollSummary? = byId[poll]

    /** The polls [player] is in: open ones first, newest first within each group. */
    fun forPlayer(player: PlayerId): List<DatePollSummary> =
        byId.values.filter { player in it.participants }.reversed().sortedBy { !it.open }

    internal fun changed(poll: DatePollId, f: (DatePollSummary) -> DatePollSummary) =
        byId[poll]?.let { DatePollBoard(byId + (poll to f(it))) } ?: this

    internal fun added(summary: DatePollSummary) = DatePollBoard(byId + (summary.id to summary))

    companion object {
        val none = DatePollBoard(emptyMap())
    }
}

/** The poll an event is about, if it's about one. */
private val Event.poll: DatePollId?
    get() = when (this) {
        is DatePollJoined -> poll
        is DatePollAnswered -> poll
        is DatePollClosed -> poll
        is DatePollPickedForGameNight -> poll
        is DatePollGameProposed -> poll
        is DatePollGameWithdrawn -> poll
        is DatePollGameAnswered -> poll
        is DatePollBringAnswered -> poll
        else -> null
    }

object DatePolls : Projection<DatePollBoard> {
    override val initialState = DatePollBoard.none

    override fun evolve(state: DatePollBoard, event: Event): DatePollBoard = when (event) {
        is DatePollCreated -> state.added(DatePollSummary.of(event))
        else -> event.poll?.let { poll -> state.changed(poll) { it.after(event) } } ?: state
    }
}
