package gorefest.core.gameday

import gorefest.core.PlayerId
import gorefest.core.game.Game
import gorefest.core.taste.PlayerTastes

enum class TasteStatus { NONE, THIN, READY }

data class PlayerReadiness(val player: PlayerId, val answers: Int, val brought: Int, val status: TasteStatus)

/** A table size the shelf can't serve well; [games] are the few that do work, if any. */
data class Gap(val players: Int, val games: List<Game>)

data class Readiness(val joined: Int, val shelf: Int, val tasteReady: Int, val players: List<PlayerReadiness>, val gaps: List<Gap>) {
    companion object {
        const val READY_ANSWERS = 10

        /**
         * What the host needs to know before the day. Players who need a nudge come first. Gaps are
         * table sizes from 3 up to everyone who joined where fewer than 3 shelf games play well.
         */
        fun of(gameDay: GameDaySummary, shelf: List<Pair<Game, ShelfGame>>, tastes: PlayerTastes): Readiness {
            val players = gameDay.players.map { p ->
                val answers = tastes.of(p).comparisons.size
                val status = when {
                    answers >= READY_ANSWERS -> TasteStatus.READY
                    answers > 0 -> TasteStatus.THIN
                    else -> TasteStatus.NONE
                }
                PlayerReadiness(p, answers, shelf.count { p in it.second.owners }, status)
            }.sortedWith(compareBy<PlayerReadiness> { it.status }.thenBy { it.answers })
            val gaps = if (shelf.isEmpty()) emptyList() else (3..gameDay.players.size).mapNotNull { n ->
                val fits = shelf.map { it.first }.filter { n in it.goodPlayerCounts }
                if (fits.size < 3) Gap(n, fits) else null
            }
            return Readiness(gameDay.players.size, shelf.size, players.count { it.status == TasteStatus.READY }, players, gaps)
        }
    }
}
