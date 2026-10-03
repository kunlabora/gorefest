package gorefest.core.taste

import gorefest.core.Basis
import gorefest.core.BggId
import gorefest.core.ComparisonRecorded
import gorefest.core.Event
import gorefest.core.GamePlayStatusDeclared
import gorefest.core.PlayLogged
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
    /** What the player declared; absent means we don't know. */
    val played: Map<BggId, Boolean> = emptyMap(),
) {
    fun scores(model: TasteModel, basis: Basis, now: java.time.Instant) = model.fit(comparisons, basis, now)

    val comparedGames: Set<BggId> get() = comparisons.flatMap { listOf(it.a, it.b) }.toSet()
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
        is GamePlayStatusDeclared -> state.changed(event.player) { it.copy(played = it.played + (event.game to event.played)) }
        is PlayLogged -> event.players.fold(state) { s, p -> s.changed(p) { it.copy(played = it.played + (event.game to true)) } }
        else -> state
    }
}

/** A comparison has no invariant across events, so its boundary is empty: it never conflicts. */
object RecordComparisonDecider : Decider<ComparisonRecorded, Unit, ComparisonRecorded> {
    override val initialState = Unit
    override fun query(command: ComparisonRecorded) = Query(emptyList())
    override fun evolve(state: Unit, event: Event) = state
    override fun decide(command: ComparisonRecorded, state: Unit): Decision<ComparisonRecorded> =
        if (command.a == command.b) Rejected("Compare two different games") else Accepted(command)
}

object DeclarePlayStatusDecider : Decider<GamePlayStatusDeclared, Unit, GamePlayStatusDeclared> {
    override val initialState = Unit
    override fun query(command: GamePlayStatusDeclared) = Query(emptyList())
    override fun evolve(state: Unit, event: Event) = state
    override fun decide(command: GamePlayStatusDeclared, state: Unit): Decision<GamePlayStatusDeclared> = Accepted(command)
}
