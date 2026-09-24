package gorefest.core.gameday

import gorefest.core.Event
import gorefest.core.GameDayCreated
import gorefest.core.GameDayDetailsChanged
import gorefest.core.GameDayEnded
import gorefest.core.GameDayId
import gorefest.core.InviteLinkRotated
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
    val timeZone: String,
    val players: List<PlayerId>,
    val ended: Boolean,
    /** Only invite links signed for this version work. */
    val inviteVersion: Int = 0,
)

/** Read model of all game days, keyed by id. */
object GameDays : Projection<Map<GameDayId, GameDaySummary>> {
    override val initialState = emptyMap<GameDayId, GameDaySummary>()

    override fun evolve(state: Map<GameDayId, GameDaySummary>, event: Event) = when (event) {
        is GameDayCreated -> state + (event.gameDay to with(event) {
            GameDaySummary(gameDay, host, title, date, place, timeZone, players = emptyList(), ended = false)
        })
        is GameDayDetailsChanged -> state.update(event.gameDay) { it.copy(title = event.title, date = event.date, place = event.place) }
        is InviteLinkRotated -> state.update(event.gameDay) { it.copy(inviteVersion = event.version) }
        is PlayerJoined -> state.update(event.gameDay) { it.copy(players = it.players + event.player) }
        is GameDayEnded -> state.update(event.gameDay) { it.copy(ended = true) }
        else -> state
    }

    /**
     * The game days [player] hosts or joined: upcoming ones first (soonest first), then past ones (latest first).
     * "Past" is judged in each game day's own time zone.
     */
    fun Map<GameDayId, GameDaySummary>.forPlayer(player: PlayerId, today: (timeZone: String) -> LocalDate): List<GameDaySummary> {
        val (upcoming, past) = values.filter { player in it.players }.partition { !it.ended && it.date >= today(it.timeZone) }
        return upcoming.sortedBy { it.date } + past.sortedByDescending { it.date }
    }

    private fun <K, V> Map<K, V>.update(key: K, f: (V) -> V): Map<K, V> =
        get(key)?.let { this + (key to f(it)) } ?: this
}
