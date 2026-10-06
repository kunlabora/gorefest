package gorefest.core.gameday

import gorefest.core.Rejection
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

/** [player] says they really want (or no longer want) to play [game], whoever brought it. */
data class SetPlayWish(val gameDay: GameDayId, val player: PlayerId, val game: BggId, val wantsToPlay: Boolean)

/** How many shelf games one player can mark as "I really want to play this" per game day: a wish only means something when it's scarce. */
const val MAX_WISHES = 3

/** What a bring decision needs to know: is the game day on, did I join, am I bringing this game. */
data class BringState(val open: Boolean = false, val ended: Boolean = false, val joined: Boolean = false, val bringing: Boolean = false)

private fun BringState.cantChange(): Rejection? = when {
    !open -> Rejection.NoSuchGameDay
    ended -> Rejection.GameDayOver
    !joined -> Rejection.NotJoined
    else -> null
}

/** Only this player's bringing of this game is inside the boundary, so everyone can tick games at once. */
object BringGameDecider : Decider<BringGame, BringState, Event> {
    override val initialState = BringState()

    override fun query(command: BringGame) = Query.of(
        Query.Item(setOf(typeName<GameDayCreated>(), typeName<GameDayEnded>()), setOf(Tags.gameDay(command.gameDay))),
        Query.Item(setOf(typeName<PlayerJoined>()), setOf(Tags.gameDay(command.gameDay), Tags.player(command.player))),
        Query.Item(setOf(typeName<GameBrought>(), typeName<GameUnbrought>()), setOf(Tags.gameDay(command.gameDay), Tags.player(command.player), Tags.game(command.game))),
    )

    override fun evolve(state: BringState, event: Event) = when (event) {
        is GameDayCreated -> state.copy(open = true)
        is GameDayEnded -> state.copy(ended = true)
        is PlayerJoined -> state.copy(joined = true)
        is GameBrought -> state.copy(bringing = true)
        is GameUnbrought -> state.copy(bringing = false)
        else -> state
    }

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

/**
 * What a wish decision needs to know: is the game day on, did I join, who brings what, and which games I already wish for.
 * A game leaving the shelf drops everyone's wishes for it (see [Shelves]), so it stops counting against the limit.
 */
data class WishState(
    val open: Boolean = false,
    val ended: Boolean = false,
    val joined: Boolean = false,
    /** The player the decision is about: the query only lets their own join through. */
    val me: PlayerId? = null,
    val owners: Map<BggId, Set<PlayerId>> = emptyMap(),
    val wished: Set<BggId> = emptySet(),
)

/**
 * Any joined player can wish for any game on the shelf, up to [MAX_WISHES]. The boundary holds the day's
 * shelf changes and my own wishes only, so other people's wishes never conflict with mine.
 */
object SetPlayWishDecider : Decider<SetPlayWish, WishState, PlayWishChanged> {
    override val initialState = WishState()

    override fun query(command: SetPlayWish) = Query.of(
        Query.Item(setOf(typeName<GameDayCreated>(), typeName<GameDayEnded>()), setOf(Tags.gameDay(command.gameDay))),
        Query.Item(setOf(typeName<PlayerJoined>()), setOf(Tags.gameDay(command.gameDay), Tags.player(command.player))),
        Query.Item(setOf(typeName<GameBrought>(), typeName<GameUnbrought>()), setOf(Tags.gameDay(command.gameDay))),
        Query.Item(setOf(typeName<PlayWishChanged>()), setOf(Tags.gameDay(command.gameDay), Tags.player(command.player))),
    )

    override fun evolve(state: WishState, event: Event) = when (event) {
        is GameDayCreated -> state.copy(open = true)
        is GameDayEnded -> state.copy(ended = true)
        is PlayerJoined -> state.copy(joined = true, me = event.player)
        is GameBrought -> state.copy(owners = state.owners + (event.game to (state.owners[event.game].orEmpty() + event.player)))
        is GameUnbrought -> {
            val left = state.owners[event.game].orEmpty() - event.player
            // Stepping back as an owner drops your own wish for the game, as it always did; the game leaving drops everyone's.
            val wished = if (left.isEmpty() || event.player == state.me) state.wished - event.game else state.wished
            state.copy(owners = if (left.isEmpty()) state.owners - event.game else state.owners + (event.game to left), wished = wished)
        }
        is PlayWishChanged -> state.copy(wished = if (event.wantsToPlay) state.wished + event.game else state.wished - event.game)
        else -> state
    }

    override fun decide(command: SetPlayWish, state: WishState): Decision<PlayWishChanged> {
        when {
            !state.open -> return Rejected(Rejection.NoSuchGameDay)
            state.ended -> return Rejected(Rejection.GameDayOver)
            !state.joined -> return Rejected(Rejection.NotJoined)
            command.game !in state.owners -> return Rejected(Rejection.NotOnShelf)
        }
        val wants = command.game in state.wished
        return when {
            wants == command.wantsToPlay -> Accepted()
            command.wantsToPlay && state.wished.size >= MAX_WISHES -> Rejected(Rejection.TooManyWishes)
            else -> with(command) { Accepted(PlayWishChanged(gameDay, player, game, wantsToPlay)) }
        }
    }
}

/** One game on a shelf: who brings it, and which of the game day's players really want to play it. */
data class ShelfGame(val game: BggId, val owners: List<PlayerId>, val eager: Set<PlayerId> = emptySet())

/** The shelf of every game day. */
class GameDayShelves private constructor(private val byGameDay: Map<GameDayId, List<ShelfGame>>) {
    /** The games brought to [gameDay], in the order they were first brought; empty if none. */
    fun of(gameDay: GameDayId): List<ShelfGame> = byGameDay[gameDay].orEmpty()

    internal fun changed(gameDay: GameDayId, f: (List<ShelfGame>) -> List<ShelfGame>) =
        GameDayShelves(byGameDay + (gameDay to f(of(gameDay))))

    companion object {
        val none = GameDayShelves(emptyMap())
    }
}

object Shelves : Projection<GameDayShelves> {
    override val initialState = GameDayShelves.none

    override fun evolve(state: GameDayShelves, event: Event): GameDayShelves = when (event) {
        is GameBrought -> state.changed(event.gameDay) { shelf ->
            val existing = shelf.firstOrNull { it.game == event.game }
            if (existing == null) shelf + ShelfGame(event.game, listOf(event.player))
            else shelf.map { if (it.game == event.game && event.player !in it.owners) it.copy(owners = it.owners + event.player) else it }
        }
        is GameUnbrought -> state.changed(event.gameDay) { shelf ->
            shelf.map { if (it.game == event.game) it.copy(owners = it.owners - event.player, eager = it.eager - event.player) else it }
                .filter { it.owners.isNotEmpty() } // gone from the shelf: the rest of its wishes go with it
        }
        is PlayWishChanged -> state.changed(event.gameDay) { shelf ->
            shelf.map {
                if (it.game != event.game) it
                else it.copy(eager = if (event.wantsToPlay) it.eager + event.player else it.eager - event.player)
            }
        }
        else -> state
    }
}
