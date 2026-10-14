package gorefest.core.gameday

import gorefest.core.BggId
import gorefest.core.BringRequestDeclined
import gorefest.core.BringRequestWithdrawn
import gorefest.core.BringRequested
import gorefest.core.BringRequestsAllowedChanged
import gorefest.core.Event
import gorefest.core.GameBrought
import gorefest.core.GameDayCancelled
import gorefest.core.GameDayCreated
import gorefest.core.GameDayEnded
import gorefest.core.GameDayId
import gorefest.core.GameUnbrought
import gorefest.core.PlayerErased
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.Rejection
import gorefest.core.Tags
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.Decision.Accepted
import gorefest.core.eventsourcing.Decision.Rejected
import gorefest.core.eventsourcing.Projection
import gorefest.core.eventsourcing.Query
import gorefest.core.typeName

/** [asker] asks [owner] to bring [game] to [gameDay]. The shell has checked that [owner] has the game: the event log doesn't know collections. */
data class RequestBring(val gameDay: GameDayId, val asker: PlayerId, val owner: PlayerId, val game: BggId)

/** The asker takes their request back. */
data class WithdrawBringRequest(val gameDay: GameDayId, val asker: PlayerId, val owner: PlayerId, val game: BggId)

/** The owner says "not this time" to [asker]'s request. */
data class DeclineBringRequest(val gameDay: GameDayId, val owner: PlayerId, val game: BggId, val asker: PlayerId)

data class SetBringRequestsAllowed(val player: PlayerId, val allowed: Boolean)

/** How many requests one player can have standing per game day: a request only means something when it is scarce. */
const val MAX_BRING_REQUESTS = 3

/** How many requests one player can be sent per game day, so nobody's bag is decided by the group. */
const val MAX_ASKS_PER_OWNER = 5

/**
 * One request as the deciders see it. [open] is false once the owner brought the game (answered) or declined;
 * the request still stands, and counts against both caps, until it is withdrawn.
 */
data class Standing(val asker: PlayerId, val owner: PlayerId, val game: BggId, val open: Boolean = true) {
    fun isFor(asker: PlayerId, owner: PlayerId, game: BggId) = this.asker == asker && this.owner == owner && this.game == game
}

/** The requests a decision's events leave standing, in the order they were made. */
private fun List<Standing>.after(event: Event): List<Standing> = when (event) {
    is BringRequested -> this + Standing(event.asker, event.owner, event.game)
    is BringRequestWithdrawn -> filterNot { it.isFor(event.asker, event.owner, event.game) }
    is BringRequestDeclined -> map { if (it.isFor(event.asker, event.owner, event.game)) it.copy(open = false) else it }
    // Answered: the owner ticked the game, by whatever route.
    is GameBrought -> map { if (it.owner == event.player && it.game == event.game) it.copy(open = false) else it }
    // An erased player's requests, both ways, stop standing: nobody can answer or take them back.
    is PlayerErased -> filterNot { it.asker == event.player || it.owner == event.player }
    else -> this
}

private val dayTypes = setOf(typeName<GameDayCreated>(), typeName<GameDayEnded>(), typeName<GameDayCancelled>())
private val requestTypes = setOf(typeName<BringRequested>(), typeName<BringRequestWithdrawn>(), typeName<BringRequestDeclined>())

data class RequestState(
    val open: Boolean = false,
    val ended: Boolean = false,
    val cancelled: Boolean = false,
    val joined: Set<PlayerId> = emptySet(),
    val allowed: Boolean = true,
    val standing: List<Standing> = emptyList(),
    /** Whether the owner already brings the game: set by their own events only, which is all the query lets through. */
    val bringing: Boolean = false,
)

private fun dayRejection(open: Boolean, cancelled: Boolean, ended: Boolean): Rejection? = when {
    !open -> Rejection.NoSuchGameDay
    cancelled -> Rejection.GameDayCancelled
    ended -> Rejection.GameDayOver
    else -> null
}

/**
 * Ask someone to bring a game. The boundary holds the day, both players' joins, the owner's opt-out, whether the owner already
 * brings the game, and every request the asker or the owner is part of, so two asks can't both slip past a cap.
 */
object RequestBringDecider : Decider<RequestBring, RequestState, BringRequested> {
    override val initialState = RequestState()

    override fun query(command: RequestBring) = Query.of(
        Query.Item(dayTypes, setOf(Tags.gameDay(command.gameDay))),
        Query.Item(setOf(typeName<PlayerJoined>()), setOf(Tags.gameDay(command.gameDay), Tags.player(command.asker))),
        Query.Item(setOf(typeName<PlayerJoined>()), setOf(Tags.gameDay(command.gameDay), Tags.player(command.owner))),
        Query.Item(setOf(typeName<BringRequestsAllowedChanged>()), setOf(Tags.player(command.owner))),
        Query.Item(requestTypes, setOf(Tags.gameDay(command.gameDay), Tags.player(command.asker))),
        Query.Item(requestTypes, setOf(Tags.gameDay(command.gameDay), Tags.player(command.owner))),
        Query.Item(setOf(typeName<GameBrought>(), typeName<GameUnbrought>()), setOf(Tags.gameDay(command.gameDay), Tags.player(command.owner), Tags.game(command.game))),
        // Erasures are rare: any of them can free a place under a cap.
        Query.Item(setOf(typeName<PlayerErased>()), emptySet()),
    )

    override fun evolve(state: RequestState, event: Event) = when (event) {
        is GameDayCreated -> state.copy(open = true)
        is GameDayEnded -> state.copy(ended = true)
        is GameDayCancelled -> state.copy(cancelled = true)
        is PlayerJoined -> state.copy(joined = state.joined + event.player)
        is BringRequestsAllowedChanged -> state.copy(allowed = event.allowed)
        is GameBrought -> state.copy(bringing = true, standing = state.standing.after(event))
        is GameUnbrought -> state.copy(bringing = false)
        is BringRequested, is BringRequestWithdrawn, is BringRequestDeclined -> state.copy(standing = state.standing.after(event))
        is PlayerErased -> state.copy(joined = state.joined - event.player, standing = state.standing.after(event))
        else -> state
    }

    override fun decide(command: RequestBring, state: RequestState): Decision<BringRequested> {
        dayRejection(state.open, state.cancelled, state.ended)?.let { return Rejected(it) }
        val (gameDay, asker, owner, game) = command
        when {
            asker !in state.joined -> return Rejected(Rejection.NotJoined)
            asker == owner -> return Rejected(Rejection.CantAskYourself)
            owner !in state.joined -> return Rejected(Rejection.OwnerNotJoined)
            !state.allowed -> return Rejected(Rejection.OwnerNotAcceptingRequests)
            state.bringing -> return Rejected(Rejection.AlreadyBringing)
            state.standing.any { it.open && it.isFor(asker, owner, game) } -> return Accepted()
            state.standing.count { it.asker == asker } >= MAX_BRING_REQUESTS -> return Rejected(Rejection.TooManyBringRequests)
            state.standing.count { it.owner == owner } >= MAX_ASKS_PER_OWNER -> return Rejected(Rejection.OwnerAskedEnough)
        }
        return Accepted(BringRequested(gameDay, asker, owner, game))
    }
}

data class RequestStandingState(
    val open: Boolean = false,
    val ended: Boolean = false,
    val cancelled: Boolean = false,
    val standing: List<Standing> = emptyList(),
)

private fun requestQuery(gameDay: GameDayId, asker: PlayerId, owner: PlayerId, game: BggId) = Query.of(
    Query.Item(dayTypes, setOf(Tags.gameDay(gameDay))),
    Query.Item(requestTypes, setOf(Tags.gameDay(gameDay), Tags.player(asker), Tags.player(owner), Tags.game(game))),
    Query.Item(setOf(typeName<GameBrought>()), setOf(Tags.gameDay(gameDay), Tags.player(owner), Tags.game(game))),
)

private fun evolveStanding(state: RequestStandingState, event: Event) = when (event) {
    is GameDayCreated -> state.copy(open = true)
    is GameDayEnded -> state.copy(ended = true)
    is GameDayCancelled -> state.copy(cancelled = true)
    else -> state.copy(standing = state.standing.after(event))
}

/** Only the asker, only while the request is open. Taking back what isn't open (a double tap, an answer in between) is a no-op. */
object WithdrawBringRequestDecider : Decider<WithdrawBringRequest, RequestStandingState, BringRequestWithdrawn> {
    override val initialState = RequestStandingState()
    override fun query(command: WithdrawBringRequest) = requestQuery(command.gameDay, command.asker, command.owner, command.game)
    override fun evolve(state: RequestStandingState, event: Event) = evolveStanding(state, event)

    override fun decide(command: WithdrawBringRequest, state: RequestStandingState): Decision<BringRequestWithdrawn> {
        dayRejection(state.open, state.cancelled, state.ended)?.let { return Rejected(it) }
        if (state.standing.none { it.open && it.isFor(command.asker, command.owner, command.game) }) return Accepted()
        return Accepted(BringRequestWithdrawn(command.gameDay, command.asker, command.owner, command.game))
    }
}

/** Only the owner, only while the request is open; no-op otherwise. */
object DeclineBringRequestDecider : Decider<DeclineBringRequest, RequestStandingState, BringRequestDeclined> {
    override val initialState = RequestStandingState()
    override fun query(command: DeclineBringRequest) = requestQuery(command.gameDay, command.asker, command.owner, command.game)
    override fun evolve(state: RequestStandingState, event: Event) = evolveStanding(state, event)

    override fun decide(command: DeclineBringRequest, state: RequestStandingState): Decision<BringRequestDeclined> {
        dayRejection(state.open, state.cancelled, state.ended)?.let { return Rejected(it) }
        if (state.standing.none { it.open && it.isFor(command.asker, command.owner, command.game) }) return Accepted()
        return Accepted(BringRequestDeclined(command.gameDay, command.owner, command.game, command.asker))
    }
}

/** Turning requests off (or on again). The boundary is the player's own switch, so it never conflicts with anything else. */
object SetBringRequestsAllowedDecider : Decider<SetBringRequestsAllowed, Boolean, BringRequestsAllowedChanged> {
    override val initialState = true
    override fun query(command: SetBringRequestsAllowed) = Query.of(
        Query.Item(setOf(typeName<BringRequestsAllowedChanged>()), setOf(Tags.player(command.player))),
    )

    override fun evolve(state: Boolean, event: Event) = if (event is BringRequestsAllowedChanged) event.allowed else state

    override fun decide(command: SetBringRequestsAllowed, state: Boolean): Decision<BringRequestsAllowedChanged> =
        if (command.allowed == state) Accepted() else Accepted(BringRequestsAllowedChanged(command.player, command.allowed))
}

enum class RequestStatus { OPEN, ANSWERED, DECLINED }

/** One request on a game day, as the screens show it. A withdrawn one is gone. */
data class BringRequest(val asker: PlayerId, val owner: PlayerId, val game: BggId, val status: RequestStatus)

/** The bring requests of every game day, and who turned requests off. */
class GameDayBringRequests private constructor(
    private val byGameDay: Map<GameDayId, List<BringRequest>>,
    private val optedOut: Set<PlayerId>,
    private val closed: Set<GameDayId> = emptySet(),
) {
    /** Every standing request of [gameDay], oldest first. */
    fun on(gameDay: GameDayId): List<BringRequest> = byGameDay[gameDay].orEmpty()

    /** What [owner] still has to answer. */
    fun openFor(owner: PlayerId, gameDay: GameDayId) = on(gameDay).filter { it.owner == owner && it.status == RequestStatus.OPEN }

    /** What [asker] has asked, with how it went: open, answered or declined. */
    fun by(asker: PlayerId, gameDay: GameDayId) = on(gameDay).filter { it.asker == asker }

    fun openBy(asker: PlayerId, gameDay: GameDayId) = by(asker, gameDay).filter { it.status == RequestStatus.OPEN }

    /** How many requests wait for an answer on [gameDay]. */
    fun count(gameDay: GameDayId) = on(gameDay).count { it.status == RequestStatus.OPEN }

    fun isOpen(gameDay: GameDayId, asker: PlayerId, owner: PlayerId, game: BggId) =
        on(gameDay).any { it.status == RequestStatus.OPEN && it.asker == asker && it.owner == owner && it.game == game }

    /** Whether people on [player]'s game days may ask them to bring games (the default). */
    fun accepts(player: PlayerId) = player !in optedOut

    internal fun changed(gameDay: GameDayId, f: (List<BringRequest>) -> List<BringRequest>) =
        GameDayBringRequests(byGameDay + (gameDay to f(on(gameDay))), optedOut, closed)

    internal fun allowing(player: PlayerId, allowed: Boolean) =
        GameDayBringRequests(byGameDay, if (allowed) optedOut - player else optedOut + player, closed)

    internal fun closed(gameDay: GameDayId) = GameDayBringRequests(byGameDay, optedOut, closed + gameDay)

    /** An erased player's requests, asked or asked of them, go from game days still to be played; a closed day keeps its history. */
    internal fun erased(player: PlayerId) = GameDayBringRequests(
        byGameDay.mapValues { (gameDay, requests) -> if (gameDay in closed) requests else requests.filterNot { it.asker == player || it.owner == player } },
        optedOut - player,
        closed,
    )

    companion object {
        val none = GameDayBringRequests(emptyMap(), emptySet())
    }
}

object BringRequests : Projection<GameDayBringRequests> {
    override val initialState = GameDayBringRequests.none

    override fun evolve(state: GameDayBringRequests, event: Event): GameDayBringRequests = when (event) {
        is BringRequested -> state.changed(event.gameDay) { requests ->
            // Asking again while the first is open changes nothing; after an answer or a decline it is a new request.
            if (requests.any { it.asker == event.asker && it.owner == event.owner && it.game == event.game && it.status == RequestStatus.OPEN }) requests
            else requests + BringRequest(event.asker, event.owner, event.game, RequestStatus.OPEN)
        }
        is BringRequestWithdrawn -> state.changed(event.gameDay) { requests ->
            requests.filterNot { it.asker == event.asker && it.owner == event.owner && it.game == event.game }
        }
        is BringRequestDeclined -> state.changed(event.gameDay) { requests ->
            requests.map { if (it.asker == event.asker && it.owner == event.owner && it.game == event.game && it.status == RequestStatus.OPEN) it.copy(status = RequestStatus.DECLINED) else it }
        }
        // Ticking the game by any route answers every open request for it.
        is GameBrought -> state.changed(event.gameDay) { requests ->
            requests.map { if (it.owner == event.player && it.game == event.game && it.status == RequestStatus.OPEN) it.copy(status = RequestStatus.ANSWERED) else it }
        }
        is BringRequestsAllowedChanged -> state.allowing(event.player, event.allowed)
        is GameDayEnded -> state.closed(event.gameDay)
        is GameDayCancelled -> state.closed(event.gameDay)
        is PlayerErased -> state.erased(event.player)
        else -> state
    }
}
