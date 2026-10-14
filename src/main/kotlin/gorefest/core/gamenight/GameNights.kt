package gorefest.core.gamenight

import gorefest.core.BggId
import gorefest.core.DatePollId
import gorefest.core.Event
import gorefest.core.GameNightCreated
import gorefest.core.GameNightId
import gorefest.core.PlannedGame
import gorefest.core.PlayerErased
import gorefest.core.PlayerId
import gorefest.core.eventsourcing.Projection
import java.time.LocalDate

/** A game night (U11) as its people see it: who's coming, the plan with who brings what, and what nobody can bring. */
data class GameNightSummary(
    val id: GameNightId,
    val poll: DatePollId,
    val host: PlayerId,
    val title: String,
    val date: LocalDate,
    val place: String,
    val timeZone: String,
    /** The host first. */
    val players: List<PlayerId>,
    val plan: List<PlannedGame>,
    val missing: List<BggId>,
) {
    /**
     * An erased player leaves the night: off the list and their yeses gone. A game they were to bring goes to the next one
     * coming who offered, or becomes missing if nobody did. A game night has no end yet, so unlike a game day there's no history to keep.
     */
    internal fun erased(player: PlayerId): GameNightSummary {
        val after = plan.map { it.copy(wantedBy = it.wantedBy - player, others = it.others - player) }
            .map { if (it.bringer == player && it.others.isNotEmpty()) it.copy(bringer = it.others.first(), others = it.others.drop(1)) else it }
        val (gone, kept) = after.partition { it.bringer == player }
        return copy(players = players - player, plan = kept, missing = missing + gone.map { it.game })
    }

    companion object {
        fun of(event: GameNightCreated) = with(event) {
            GameNightSummary(gameNight, poll, host, title, date, place, timeZone, players, plan, missing)
        }
    }
}

/** The game nights, by id; [of] and [forPlayer] are the questions callers ask. */
class GameNightBoard private constructor(private val byId: Map<GameNightId, GameNightSummary>) {
    fun of(night: GameNightId): GameNightSummary? = byId[night]

    /** The game nights [player] is on, soonest first. */
    fun forPlayer(player: PlayerId): List<GameNightSummary> = byId.values.filter { player in it.players }.sortedBy { it.date }

    internal fun added(night: GameNightSummary) = GameNightBoard(byId + (night.id to night))

    internal fun erased(player: PlayerId) = GameNightBoard(byId.mapValues { (_, night) -> night.erased(player) })

    companion object {
        val none = GameNightBoard(emptyMap())
    }
}

object GameNights : Projection<GameNightBoard> {
    override val initialState = GameNightBoard.none

    override fun evolve(state: GameNightBoard, event: Event): GameNightBoard = when (event) {
        is GameNightCreated -> state.added(GameNightSummary.of(event))
        is PlayerErased -> state.erased(event.player)
        else -> state
    }
}
