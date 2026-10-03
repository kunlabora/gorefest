package gorefest.core.gameday

import gorefest.core.BggId
import gorefest.core.Event
import gorefest.core.GameDayCreated
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

/** Logging needs to know who joined, so the boundary is the game day's lifecycle and its joins. */
object LogPlayDecider : Decider<PlayLogged, LogPlayDecider.State, PlayLogged> {
    data class State(val open: Boolean = false, val ended: Boolean = false, val joined: Set<PlayerId> = emptySet())

    override val initialState = State()

    override fun query(command: PlayLogged) = Query.of(
        Query.Item(setOf(typeName<GameDayCreated>(), typeName<GameDayEnded>(), typeName<PlayerJoined>()), setOf(Tags.gameDay(command.gameDay))),
    )

    override fun evolve(state: State, event: Event) = when (event) {
        is GameDayCreated -> state.copy(open = true)
        is GameDayEnded -> state.copy(ended = true)
        is PlayerJoined -> state.copy(joined = state.joined + event.player)
        else -> state
    }

    override fun decide(command: PlayLogged, state: State): Decision<PlayLogged> = when {
        !state.open -> Rejected("No such game day")
        state.ended -> Rejected("This game day has ended")
        command.loggedBy !in state.joined -> Rejected("Join the game day first")
        command.players.isEmpty() -> Rejected("Tick who played")
        !state.joined.containsAll(command.players) -> Rejected("Only players at this game day can be in a log")
        command.minutes !in 5..720 -> Rejected("That doesn't look like a real duration")
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
 * where at least half of [table] played. 1.0 without such logs; clamped to 0.6–1.8.
 */
fun groupSpeed(logs: List<PlayLogged>, games: (BggId) -> Game?, table: Collection<PlayerId>): Double {
    val ratios = logs.filter { log -> log.players.count { it in table } * 2 >= table.size }.mapNotNull { log ->
        games(log.game)?.let { log.minutes.toDouble() / Matcher.estimatedMinutes(it, log.players.size) }
    }.sorted()
    if (ratios.isEmpty()) return 1.0
    val median = if (ratios.size % 2 == 1) ratios[ratios.size / 2] else (ratios[ratios.size / 2 - 1] + ratios[ratios.size / 2]) / 2
    return median.coerceIn(0.6, 1.8)
}
