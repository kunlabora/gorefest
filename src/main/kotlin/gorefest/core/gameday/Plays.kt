package gorefest.core.gameday

import gorefest.core.Rejection
import gorefest.core.BggId
import gorefest.core.Event
import gorefest.core.GameDayCreated
import gorefest.core.GameDayDetailsChanged
import gorefest.core.GameDayCancelled
import gorefest.core.GameDayId
import gorefest.core.PlayLogged
import gorefest.core.PlayRemoved
import gorefest.core.PlayerId
import gorefest.core.PlayerErased
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Logging needs to know who joined and when the game day is, so the boundary is its lifecycle, its date and its joins.
 * Plays can be logged from the game day's date (in its own time zone), also after it ended (nobody logs everything on
 * the night), but not on a cancelled one. Erased players stay on a closed game day's history, so every erasure is in
 * the boundary too: a new play must never name someone who was erased.
 */
/** Names one play by what [PlayLogged] holds: it has no id of its own. */
data class PlayKey(val gameDay: GameDayId, val game: BggId, val loggedBy: PlayerId, val at: Instant)

val PlayLogged.key get() = PlayKey(gameDay, game, loggedBy, at)
val PlayRemoved.key get() = PlayKey(gameDay, game, loggedBy, loggedAt)

object LogPlayDecider : Decider<PlayLogged, LogPlayDecider.State, PlayLogged> {
    data class State(val date: LocalDate? = null, val zone: ZoneId = ZoneId.of("UTC"), val open: Boolean = false, val cancelled: Boolean = false, val joined: Set<PlayerId> = emptySet())

    override val initialState = State()

    override fun query(command: PlayLogged) = Query.of(
        Query.Item(setOf(typeName<GameDayCreated>(), typeName<GameDayDetailsChanged>(), typeName<GameDayCancelled>(), typeName<PlayerJoined>()), setOf(Tags.gameDay(command.gameDay))),
        Query.Item(setOf(typeName<PlayerErased>()), emptySet()),
    )

    override fun evolve(state: State, event: Event) = when (event) {
        is GameDayCreated -> state.copy(open = true, date = event.date, zone = runCatching { ZoneId.of(event.timeZone) }.getOrDefault(state.zone))
        is GameDayDetailsChanged -> state.copy(date = event.date)
        is GameDayCancelled -> state.copy(cancelled = true)
        is PlayerJoined -> state.copy(joined = state.joined + event.player)
        is PlayerErased -> state.copy(joined = state.joined - event.player)
        else -> state
    }

    override fun decide(command: PlayLogged, state: State): Decision<PlayLogged> = when {
        !state.open -> Rejected(Rejection.NoSuchGameDay)
        state.cancelled -> Rejected(Rejection.GameDayCancelled)
        state.date != null && command.at.atZone(state.zone).toLocalDate() < state.date -> Rejected(Rejection.GameDayNotStarted)
        command.loggedBy !in state.joined -> Rejected(Rejection.NotJoined)
        command.players.isEmpty() -> Rejected(Rejection.NobodyPlayed)
        !state.joined.containsAll(command.players) -> Rejected(Rejection.PlayersNotAtGameDay)
        command.minutes !in 5..720 -> Rejected(Rejection.ImplausibleDuration)
        else -> Accepted(command.copy(players = command.players.distinct()))
    }
}

/** Take back the play [play], by [by] (U23). */
data class RemovePlay(val play: PlayKey, val by: PlayerId, val at: Instant)

/**
 * Only whoever logged a play, or played in it, can take it back, on a game day that isn't cancelled. The boundary is the
 * game day's lifecycle and its plays of that game; removing a play that's already gone records nothing.
 */
object RemovePlayDecider : Decider<RemovePlay, RemovePlayDecider.State, PlayRemoved> {
    data class State(val open: Boolean = false, val cancelled: Boolean = false, val plays: List<PlayLogged> = emptyList(), val removed: Set<PlayKey> = emptySet())

    override val initialState = State()

    override fun query(command: RemovePlay) = Query.of(
        Query.Item(setOf(typeName<GameDayCreated>(), typeName<GameDayCancelled>()), setOf(Tags.gameDay(command.play.gameDay))),
        Query.Item(setOf(typeName<PlayLogged>(), typeName<PlayRemoved>()), setOf(Tags.gameDay(command.play.gameDay), Tags.game(command.play.game))),
    )

    override fun evolve(state: State, event: Event) = when (event) {
        is GameDayCreated -> state.copy(open = true)
        is GameDayCancelled -> state.copy(cancelled = true)
        is PlayLogged -> state.copy(plays = state.plays + event)
        is PlayRemoved -> state.copy(removed = state.removed + event.key)
        else -> state
    }

    override fun decide(command: RemovePlay, state: State): Decision<PlayRemoved> {
        val play = state.plays.firstOrNull { it.key == command.play }
        return when {
            !state.open -> Rejected(Rejection.NoSuchGameDay)
            state.cancelled -> Rejected(Rejection.GameDayCancelled)
            play == null -> Rejected(Rejection.NoSuchPlay)
            command.by != play.loggedBy && command.by !in play.players -> Rejected(Rejection.NotYourPlay)
            command.play in state.removed -> Accepted()
            else -> Accepted(PlayRemoved(play.gameDay, play.game, play.loggedBy, play.at, play.players, command.by, command.at))
        }
    }
}

/** Every play log, per game day. */
class GameDayPlays private constructor(private val byGameDay: Map<GameDayId, List<PlayLogged>>) {
    /** What was played at [gameDay], in the order logged; empty if nothing yet. */
    fun on(gameDay: GameDayId): List<PlayLogged> = byGameDay[gameDay].orEmpty()

    /** Every log from every game day. */
    val all: List<PlayLogged> get() = byGameDay.values.flatten()

    internal fun logged(play: PlayLogged) = GameDayPlays(byGameDay + (play.gameDay to on(play.gameDay) + play))

    internal fun removed(play: PlayKey) = GameDayPlays(byGameDay + (play.gameDay to on(play.gameDay).filterNot { it.key == play }))

    companion object {
        val none = GameDayPlays(emptyMap())
    }
}

object Plays : Projection<GameDayPlays> {
    override val initialState = GameDayPlays.none

    override fun evolve(state: GameDayPlays, event: Event) = when (event) {
        is PlayLogged -> state.logged(event)
        is PlayRemoved -> state.removed(event.key)
        else -> state
    }
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
