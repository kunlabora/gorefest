package gorefest.core.gameday

import gorefest.core.Event
import gorefest.core.GameDayCreated
import gorefest.core.GameDayDetailsChanged
import gorefest.core.GameDayCancelled
import gorefest.core.GameDayEnded
import gorefest.core.GameDayId
import gorefest.core.InviteLinkRotated
import gorefest.core.PlayerErased
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
    val cancelled: Boolean = false,
    /** Only invite links signed for this version work. */
    val inviteVersion: Int = 0,
) {
    /** Over, or called off: nothing can change anymore. */
    val closed get() = ended || cancelled
}

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
        is GameDayCancelled -> state.update(event.gameDay) { it.copy(cancelled = true) }
        is GameDayEnded -> state.update(event.gameDay) { it.copy(ended = true) }
        // Gone from tables that are still to be played; over or called-off ones keep their head count, now without a name.
        is PlayerErased -> state.mapValues { (_, day) -> if (day.closed) day else day.copy(players = day.players - event.player) }
        else -> state
    }

    /**
     * The game days [player] hosts or joined: upcoming ones first (soonest first), then past ones (latest first).
     * "Past" is judged in each game day's own time zone.
     */
    fun Map<GameDayId, GameDaySummary>.forPlayer(player: PlayerId, today: (timeZone: String) -> LocalDate): List<GameDaySummary> {
        val (upcoming, past) = values.filter { player in it.players }.partition { !it.ended && !it.cancelled && it.date >= today(it.timeZone) }
        return upcoming.sortedBy { it.date } + past.sortedByDescending { it.date }
    }

    private fun <K, V> Map<K, V>.update(key: K, f: (V) -> V): Map<K, V> =
        get(key)?.let { this + (key to f(it)) } ?: this
}
