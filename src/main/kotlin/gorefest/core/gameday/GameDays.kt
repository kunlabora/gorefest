package gorefest.core.gameday

import gorefest.core.Event
import gorefest.core.GameDayCreated
import gorefest.core.GameDayEnded
import gorefest.core.GameDayId
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.eventsourcing.Projection
import java.time.LocalDate

data class GameDaySummary(
    val id: GameDayId,
    val host: PlayerId,
    val title: String,
    val date: LocalDate,
    val place: String,
    val players: List<PlayerId>,
    val ended: Boolean,
)

/** Read model of all game days, keyed by id. */
object GameDays : Projection<Map<GameDayId, GameDaySummary>> {
    override val initialState = emptyMap<GameDayId, GameDaySummary>()

    override fun evolve(state: Map<GameDayId, GameDaySummary>, event: Event) = when (event) {
        is GameDayCreated -> state + (event.gameDay to with(event) {
            GameDaySummary(gameDay, host, title, date, place, players = emptyList(), ended = false)
        })
        is PlayerJoined -> state.update(event.gameDay) { it.copy(players = it.players + event.player) }
        is GameDayEnded -> state.update(event.gameDay) { it.copy(ended = true) }
        else -> state
    }

    private fun <K, V> Map<K, V>.update(key: K, f: (V) -> V): Map<K, V> =
        get(key)?.let { this + (key to f(it)) } ?: this
}
