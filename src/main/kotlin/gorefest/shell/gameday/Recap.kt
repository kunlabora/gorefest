package gorefest.shell.gameday

import gorefest.core.BggId
import gorefest.core.PlayLogged
import gorefest.core.PlayerId

/** One play log, in words: which game, who sat at the table, and for how long. */
data class RecapLine(val game: String, val players: List<String>, val minutes: Int)

/** What was played at a game day: one line per play log, plus the totals. */
data class Recap(val lines: List<RecapLine>) {
    val games: Int get() = lines.size
    val minutes: Int get() = lines.sumOf { it.minutes }

    companion object {
        fun of(plays: List<PlayLogged>, gameName: (BggId) -> String?, playerName: (PlayerId) -> String?) = Recap(
            plays.map { play ->
                RecapLine(
                    game = gameName(play.game) ?: "A game",
                    players = play.players.map { playerName(it) ?: "someone" },
                    minutes = play.minutes,
                )
            },
        )
    }
}
