package gorefest.core.datepoll

import gorefest.core.DatePollAnswered
import gorefest.core.DatePollClosed
import gorefest.core.DatePollCreated
import gorefest.core.DatePollId
import gorefest.core.DatePollJoined
import gorefest.core.Event
import gorefest.core.GameDayId
import gorefest.core.PlayerId
import gorefest.core.eventsourcing.Projection
import java.time.LocalDate

/** The date the host picked, and the game day made from it. */
data class PickedDate(val date: LocalDate, val gameDay: GameDayId)

/** Who can make one of a poll's dates. */
data class DateTally(val date: LocalDate, val available: List<PlayerId>)

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
) {
    val open get() = picked == null

    /** Participants who haven't answered yet. */
    val waitingOn: List<PlayerId> get() = participants.filter { it !in answers }

    fun tallies(): List<DateTally> = dates.map { date -> DateTally(date, participants.filter { answers[it]?.contains(date) == true }) }

    /** The date the most people can make; ties go to the earliest. Null while nobody can make any. */
    fun best(): LocalDate? =
        tallies().filter { it.available.isNotEmpty() }
            .maxWithOrNull(compareBy<DateTally> { it.available.size }.thenByDescending { it.date })?.date
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

object DatePolls : Projection<DatePollBoard> {
    override val initialState = DatePollBoard.none

    override fun evolve(state: DatePollBoard, event: Event): DatePollBoard = when (event) {
        is DatePollCreated -> state.added(
            DatePollSummary(event.poll, event.host, event.title, event.place, event.dates.sorted(), event.timeZone, participants = emptyList()),
        )
        is DatePollJoined -> state.changed(event.poll) { if (event.player in it.participants) it else it.copy(participants = it.participants + event.player) }
        is DatePollAnswered -> state.changed(event.poll) { it.copy(answers = it.answers + (event.player to event.available.toSet())) }
        is DatePollClosed -> state.changed(event.poll) { it.copy(picked = PickedDate(event.date, event.gameDay)) }
        else -> state
    }
}
