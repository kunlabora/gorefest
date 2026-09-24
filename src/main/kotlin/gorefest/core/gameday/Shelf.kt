package gorefest.core.gameday

import gorefest.core.BggId
import gorefest.core.Event
import gorefest.core.GameBrought
import gorefest.core.GameDayCreated
import gorefest.core.GameDayEnded
import gorefest.core.GameDayId
import gorefest.core.GameUnbrought
import gorefest.core.PlayWishChanged
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.Tags
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.Decision.Accepted
import gorefest.core.eventsourcing.Decision.Rejected
import gorefest.core.eventsourcing.Projection
import gorefest.core.eventsourcing.Query
import gorefest.core.typeName

data class BringGame(val gameDay: GameDayId, val player: PlayerId, val game: BggId, val bringing: Boolean)

data class SetPlayWish(val gameDay: GameDayId, val player: PlayerId, val game: BggId, val wantsToPlay: Boolean)

/** What a bring decision needs to know: is the game day on, did I join, am I bringing this game, do I want to play it. */
data class BringState(val open: Boolean = false, val ended: Boolean = false, val joined: Boolean = false, val bringing: Boolean = false, val wants: Boolean = false)

private fun bringQuery(gameDay: GameDayId, player: PlayerId, game: BggId) = Query.of(
    Query.Item(setOf(typeName<GameDayCreated>(), typeName<GameDayEnded>()), setOf(Tags.gameDay(gameDay))),
    Query.Item(setOf(typeName<PlayerJoined>()), setOf(Tags.gameDay(gameDay), Tags.player(player))),
    Query.Item(
        setOf(typeName<GameBrought>(), typeName<GameUnbrought>(), typeName<PlayWishChanged>()),
        setOf(Tags.gameDay(gameDay), Tags.player(player), Tags.game(game)),
    ),
)

private fun evolveBring(state: BringState, event: Event) = when (event) {
    is GameDayCreated -> state.copy(open = true)
    is GameDayEnded -> state.copy(ended = true)
    is PlayerJoined -> state.copy(joined = true)
    is GameBrought -> state.copy(bringing = true)
    is GameUnbrought -> state.copy(bringing = false, wants = false)
    is PlayWishChanged -> state.copy(wants = event.wantsToPlay)
    else -> state
}

private fun BringState.cantChange(): String? = when {
    !open -> "No such game day"
    ended -> "This game day has ended"
    !joined -> "Join the game day first"
    else -> null
}

/** Only this player's bringing of this game is inside the boundary, so everyone can tick games at once. */
object BringGameDecider : Decider<BringGame, BringState, Event> {
    override val initialState = BringState()
    override fun query(command: BringGame) = bringQuery(command.gameDay, command.player, command.game)
    override fun evolve(state: BringState, event: Event) = evolveBring(state, event)

    override fun decide(command: BringGame, state: BringState): Decision<Event> {
        state.cantChange()?.let { return Rejected(it) }
        return with(command) {
            when {
                bringing == state.bringing -> Accepted()
                bringing -> Accepted(GameBrought(gameDay, player, game))
                else -> Accepted(GameUnbrought(gameDay, player, game))
            }
        }
    }
}

object SetPlayWishDecider : Decider<SetPlayWish, BringState, PlayWishChanged> {
    override val initialState = BringState()
    override fun query(command: SetPlayWish) = bringQuery(command.gameDay, command.player, command.game)
    override fun evolve(state: BringState, event: Event) = evolveBring(state, event)

    override fun decide(command: SetPlayWish, state: BringState): Decision<PlayWishChanged> {
        state.cantChange()?.let { return Rejected(it) }
        return when {
            !state.bringing -> Rejected("Bring the game first")
            state.wants == command.wantsToPlay -> Accepted()
            else -> with(command) { Accepted(PlayWishChanged(gameDay, player, game, wantsToPlay)) }
        }
    }
}

/** One game on a shelf: who brings it, and which of them really wants to play it. */
data class ShelfGame(val game: BggId, val owners: List<PlayerId>, val eager: Set<PlayerId> = emptySet())

/** Every game day's shelf, in the order games were first brought. */
object Shelves : Projection<Map<GameDayId, List<ShelfGame>>> {
    override val initialState = emptyMap<GameDayId, List<ShelfGame>>()

    override fun evolve(state: Map<GameDayId, List<ShelfGame>>, event: Event): Map<GameDayId, List<ShelfGame>> = when (event) {
        is GameBrought -> state.change(event.gameDay) { shelf ->
            val existing = shelf.firstOrNull { it.game == event.game }
            if (existing == null) shelf + ShelfGame(event.game, listOf(event.player))
            else shelf.map { if (it.game == event.game && event.player !in it.owners) it.copy(owners = it.owners + event.player) else it }
        }
        is GameUnbrought -> state.change(event.gameDay) { shelf ->
            shelf.map { if (it.game == event.game) it.copy(owners = it.owners - event.player, eager = it.eager - event.player) else it }
                .filter { it.owners.isNotEmpty() }
        }
        is PlayWishChanged -> state.change(event.gameDay) { shelf ->
            shelf.map {
                if (it.game != event.game) it
                else it.copy(eager = if (event.wantsToPlay) it.eager + event.player else it.eager - event.player)
            }
        }
        else -> state
    }

    private fun Map<GameDayId, List<ShelfGame>>.change(id: GameDayId, f: (List<ShelfGame>) -> List<ShelfGame>) =
        this + (id to f(this[id].orEmpty()))
}
