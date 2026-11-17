package gorefest.core.taste

import gorefest.core.Rejection
import gorefest.core.Basis
import gorefest.core.BggId
import gorefest.core.CanExplainChanged
import gorefest.core.ComparisonRecorded
import gorefest.core.Event
import gorefest.core.GamePlayStatusDeclared
import gorefest.core.PlayLogged
import gorefest.core.PlayRemoved
import gorefest.core.gameday.PlayKey
import gorefest.core.gameday.key
import gorefest.core.PlayerId
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.Decision.Accepted
import gorefest.core.eventsourcing.Decision.Rejected
import gorefest.core.eventsourcing.Projection
import gorefest.core.eventsourcing.Query

/** Everything a player told us about their taste. Models are fitted from this on demand. */
data class TasteHistory(
    val comparisons: List<ComparisonRecorded> = emptyList(),
    /** Whether the player played each game, by the last word on it (a declaration or a play log); absent means we don't know. */
    val played: Map<BggId, Boolean> = emptyMap(),
    /** How [played] came about, oldest word first, so a play taken back (U23) gives back what was true before it. */
    val words: Map<BggId, List<PlayWord>> = emptyMap(),
) {
    internal fun told(game: BggId, f: (List<PlayWord>) -> List<PlayWord>): TasteHistory {
        val now = f(words[game].orEmpty())
        val last = now.lastOrNull()
        return copy(
            words = if (now.isEmpty()) words - game else words + (game to now),
            played = if (last == null) played - game else played + (game to (last !is PlayWord.Said || last.played)),
        )
    }

    fun scores(model: TasteModel, basis: Basis, now: java.time.Instant) = model.fit(comparisons, basis, now)

    val comparedGames: Set<BggId> get() = comparisons.flatMap { listOf(it.a, it.b) }.toSet()
}

/** One thing that told us whether a player played a game. */
sealed interface PlayWord {
    /** They said so: "haven't played", or a game they can explain (U22). */
    data class Said(val played: Boolean) : PlayWord

    /** A play log named them; gone again when the play is removed. */
    data class Logged(val play: PlayKey) : PlayWord
}

/** What every player told us about their taste. */
class PlayerTastes private constructor(private val byPlayer: Map<PlayerId, TasteHistory>) {
    /** [player]'s history; empty if they haven't told us anything yet. */
    fun of(player: PlayerId): TasteHistory = byPlayer[player] ?: TasteHistory()

    internal fun changed(player: PlayerId, f: (TasteHistory) -> TasteHistory) =
        PlayerTastes(byPlayer + (player to f(of(player))))

    companion object {
        val none = PlayerTastes(emptyMap())
    }
}

object Tastes : Projection<PlayerTastes> {
    override val initialState = PlayerTastes.none

    override fun evolve(state: PlayerTastes, event: Event) = when (event) {
        is ComparisonRecorded -> state.changed(event.player) { it.copy(comparisons = it.comparisons + event) }
        is GamePlayStatusDeclared -> state.changed(event.player) { it.told(event.game) { words -> words + PlayWord.Said(event.played) } }
        // You can't explain a game you never played: the mark makes it played (U22); taking it back doesn't unplay it.
        is CanExplainChanged -> if (event.canExplain) state.changed(event.player) { it.told(event.game) { words -> words + PlayWord.Said(true) } } else state
        is PlayLogged -> event.players.fold(state) { s, p -> s.changed(p) { it.told(event.game) { words -> words + PlayWord.Logged(event.key) } } }
        is PlayRemoved -> event.players.fold(state) { s, p -> s.changed(p) { it.told(event.game) { words -> words - PlayWord.Logged(event.key) } } }
        else -> state
    }
}

/** A comparison has no invariant across events, so its boundary is empty: it never conflicts. */
object RecordComparisonDecider : Decider<ComparisonRecorded, Unit, ComparisonRecorded> {
    override val initialState = Unit
    override fun query(command: ComparisonRecorded) = Query(emptyList())
    override fun evolve(state: Unit, event: Event) = state
    override fun decide(command: ComparisonRecorded, state: Unit): Decision<ComparisonRecorded> =
        if (command.a == command.b) Rejected(Rejection.SameGameTwice) else Accepted(command)
}

object DeclarePlayStatusDecider : Decider<GamePlayStatusDeclared, Unit, GamePlayStatusDeclared> {
    override val initialState = Unit
    override fun query(command: GamePlayStatusDeclared) = Query(emptyList())
    override fun evolve(state: Unit, event: Event) = state
    override fun decide(command: GamePlayStatusDeclared, state: Unit): Decision<GamePlayStatusDeclared> = Accepted(command)
}
