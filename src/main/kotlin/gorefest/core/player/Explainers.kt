package gorefest.core.player

import gorefest.core.BggId
import gorefest.core.CanExplainChanged
import gorefest.core.Event
import gorefest.core.PlayerErased
import gorefest.core.PlayerId
import gorefest.core.Tags
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.Decision.Accepted
import gorefest.core.eventsourcing.Projection
import gorefest.core.eventsourcing.Query
import gorefest.core.typeName

/**
 * [player] says they can (or can no longer) explain [game]. The shell has checked that the game is in their collection
 * or on the shelf of a game day they joined: the event log doesn't know collections.
 */
data class SetCanExplain(val player: PlayerId, val game: BggId, val canExplain: Boolean)

/** Nothing outside this player's mark on this game can conflict; saying what is already true records nothing. */
object SetCanExplainDecider : Decider<SetCanExplain, Boolean, CanExplainChanged> {
    override val initialState = false
    override fun query(command: SetCanExplain) = Query.of(
        Query.Item(setOf(typeName<CanExplainChanged>()), setOf(Tags.player(command.player), Tags.game(command.game))),
    )

    override fun evolve(state: Boolean, event: Event) = if (event is CanExplainChanged) event.canExplain else state

    override fun decide(command: SetCanExplain, state: Boolean): Decision<CanExplainChanged> =
        if (command.canExplain == state) Accepted() else Accepted(CanExplainChanged(command.player, command.game, command.canExplain))
}

/** Who can explain which games (U22). */
class GameExplainers private constructor(private val byPlayer: Map<PlayerId, Set<BggId>>) {
    fun canExplain(player: PlayerId, game: BggId) = game in gamesOf(player)

    /** The games [player] can explain; empty if none. */
    fun gamesOf(player: PlayerId): Set<BggId> = byPlayer[player].orEmpty()

    /** Those of [players] who can explain [game], in the order given. */
    fun of(game: BggId, among: Collection<PlayerId>): List<PlayerId> = among.filter { canExplain(it, game) }

    internal fun changed(player: PlayerId, f: (Set<BggId>) -> Set<BggId>) =
        GameExplainers(byPlayer + (player to f(gamesOf(player))))

    /** The marks are the person's own, not a game day's history: erasure takes them all. */
    internal fun erased(player: PlayerId) = GameExplainers(byPlayer - player)

    companion object {
        val none = GameExplainers(emptyMap())
    }
}

object Explainers : Projection<GameExplainers> {
    override val initialState = GameExplainers.none

    override fun evolve(state: GameExplainers, event: Event) = when (event) {
        is CanExplainChanged -> state.changed(event.player) { if (event.canExplain) it + event.game else it - event.game }
        is PlayerErased -> state.erased(event.player)
        else -> state
    }
}
