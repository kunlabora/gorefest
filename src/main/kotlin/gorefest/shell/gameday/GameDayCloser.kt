package gorefest.shell.gameday

import gorefest.core.eventsourcing.Decision
import gorefest.core.gameday.EndGameDay
import gorefest.core.gameday.EndGameDayDecider
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.dueToEnd
import gorefest.shell.App
import gorefest.shell.auth.Mail
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Duration

/** Closes game days at midnight in their time zone and mails everyone who joined a summary. */
class GameDayCloser(private val app: App) {
    private val log = LoggerFactory.getLogger(GameDayCloser::class.java)

    /** Ends every due game day. Summaries go out only for the ones this run actually ended. */
    suspend fun closeDue() {
        for (id in app.gameDaysById.dueToEnd(app.clock.instant())) {
            val decision = app.commands.handle(EndGameDayDecider, EndGameDay(id))
            if (decision is Decision.Accepted && decision.events.isNotEmpty()) {
                runCatching { sendSummary(app.gameDaysById.getValue(id)) }.onFailure { log.error("Summary for {} failed", id, it) }
            }
        }
    }

    fun start(every: Duration = Duration.ofMinutes(5)) = app.scope.launch {
        while (isActive) {
            runCatching { closeDue() }.onFailure { log.error("Closing game days failed", it) }
            delay(every.toMillis())
        }
    }

    private suspend fun sendSummary(gameDay: GameDaySummary) {
        val people = app.db.transaction { app.login.players.emails(gameDay.players) }
        val names = app.login.names(gameDay.players)
        val plays = app.playsByGameDay[gameDay.id].orEmpty()
        val lines = plays.map { play ->
            val game = app.catalog[play.game]?.name ?: "A game"
            "- $game: ${play.players.map { names[it] ?: "someone" }.joinToString(", ")} · ${play.minutes} min"
        }
        val body = if (lines.isEmpty()) {
            "Nobody logged a game this time. Log what you play at the checkpoints and the suggestions get better every game day."
        } else {
            "What you played:\n\n" + lines.joinToString("\n") + "\n\n${plays.size} ${if (plays.size == 1) "game" else "games"}, ${plays.sumOf { it.minutes }} minutes at the table."
        }
        people.forEach { (player, email) ->
            val hi = names[player]?.let { "Hi $it,\n\n" } ?: ""
            app.mailer.send(
                Mail(
                    to = email,
                    subject = "${gameDay.title}: what you played",
                    text = "$hi${gameDay.title} is over. $body\n\nThanks for coming. See you at the next one.\n${app.config.baseUrl}/gamedays/${gameDay.id}",
                    sentAt = app.clock.instant(),
                ),
            )
        }
    }
}
