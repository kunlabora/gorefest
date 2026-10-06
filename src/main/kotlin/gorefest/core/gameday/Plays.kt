package gorefest.core.gameday

import gorefest.core.Rejection
import gorefest.core.BggId
import gorefest.core.Event
import gorefest.core.GameDayCreated
import gorefest.core.GameDayDetailsChanged
import gorefest.core.GameDayCancelled
import gorefest.core.GameDayEnded
import gorefest.core.GameDayId
import gorefest.core.PlayLogged
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.Tags
import gorefest.core.game.Game
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.Decision.Accepted
import gorefest.core.eventsourcing.Decision.Rejected
import gorefest.core.eventsourcing.Projection
import gorefest.core.eventsourcing.Query
import gorefest.core.matching.Matcher
import gorefest.core.typeName
import java.time.LocalDate
import java.time.ZoneId

/**
 * Logging needs to know who joined and when the game day is, so the boundary is its lifecycle, its date and its joins.
 * Plays can be logged from the game day's date (in its own time zone) until it ends or is cancelled.
 */
object LogPlayDecider : Decider<PlayLogged, LogPlayDecider.State, PlayLogged> {
    data class State(val date: LocalDate? = null, val zone: ZoneId = ZoneId.of("UTC"), val open: Boolean = false, val ended: Boolean = false, val cancelled: Boolean = false, val joined: Set<PlayerId> = emptySet())

    override val initialState = State()

    override fun query(command: PlayLogged) = Query.of(
        Query.Item(setOf(typeName<GameDayCreated>(), typeName<GameDayDetailsChanged>(), typeName<GameDayEnded>(), typeName<GameDayCancelled>(), typeName<PlayerJoined>()), setOf(Tags.gameDay(command.gameDay))),
    )

    override fun evolve(state: State, event: Event) = when (event) {
        is GameDayCreated -> state.copy(open = true, date = event.date, zone = runCatching { ZoneId.of(event.timeZone) }.getOrDefault(state.zone))
        is GameDayDetailsChanged -> state.copy(date = event.date)
        is GameDayEnded -> state.copy(ended = true)
        is GameDayCancelled -> state.copy(cancelled = true)
        is PlayerJoined -> state.copy(joined = state.joined + event.player)
        else -> state
    }

    override fun decide(command: PlayLogged, state: State): Decision<PlayLogged> = when {
        !state.open -> Rejected(Rejection.NoSuchGameDay)
        state.ended -> Rejected(Rejection.GameDayOver)
        state.cancelled -> Rejected(Rejection.GameDayCancelled)
        state.date != null && command.at.atZone(state.zone).toLocalDate() < state.date -> Rejected(Rejection.GameDayNotStarted)
        command.loggedBy !in state.joined -> Rejected(Rejection.NotJoined)
        command.players.isEmpty() -> Rejected(Rejection.NobodyPlayed)
        !state.joined.containsAll(command.players) -> Rejected(Rejection.PlayersNotAtGameDay)
        command.minutes !in 5..720 -> Rejected(Rejection.ImplausibleDuration)
        else -> Accepted(command.copy(players = command.players.distinct()))
    }
}

/** Every play log, per game day. */
class GameDayPlays private constructor(private val byGameDay: Map<GameDayId, List<PlayLogged>>) {
    /** What was played at [gameDay], in the order logged; empty if nothing yet. */
    fun on(gameDay: GameDayId): List<PlayLogged> = byGameDay[gameDay].orEmpty()

    /** Every log from every game day. */
    val all: List<PlayLogged> get() = byGameDay.values.flatten()

    internal fun logged(play: PlayLogged) = GameDayPlays(byGameDay + (play.gameDay to on(play.gameDay) + play))

    companion object {
        val none = GameDayPlays(emptyMap())
    }
}

object Plays : Projection<GameDayPlays> {
    override val initialState = GameDayPlays.none

    override fun evolve(state: GameDayPlays, event: Event) = if (event is PlayLogged) state.logged(event) else state
}

/**
 * How fast a group plays compared to the box: the median of actual ÷ estimated minutes over logs
 * of games with a known playtime where at least half of [table] played. 1.0 without such logs; clamped to 0.6–1.8.
 */
fun groupSpeed(logs: List<PlayLogged>, games: (BggId) -> Game?, table: Collection<PlayerId>): Double {
    val ratios = logs.filter { log -> log.players.count { it in table } * 2 >= table.size }.mapNotNull { log ->
        // A game without a playtime has no box time to compare with.
        games(log.game)?.takeIf(Matcher::hasPlaytime)?.let { log.minutes.toDouble() / Matcher.estimatedMinutes(it, log.players.size) }
    }.sorted()
    if (ratios.isEmpty()) return 1.0
    val median = if (ratios.size % 2 == 1) ratios[ratios.size / 2] else (ratios[ratios.size / 2 - 1] + ratios[ratios.size / 2]) / 2
    return median.coerceIn(0.6, 1.8)
}
