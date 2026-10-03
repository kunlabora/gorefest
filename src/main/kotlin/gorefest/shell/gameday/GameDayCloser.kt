package gorefest.shell.gameday

import gorefest.core.GameDayId
import gorefest.core.eventsourcing.Decision
import gorefest.core.gameday.EndGameDay
import gorefest.core.gameday.EndGameDayDecider
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.dueToEnd
import gorefest.shell.eventstore.CommandHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration

/** Closes game days at midnight in their time zone and mails everyone who joined a recap. */
class GameDayCloser(
    private val commands: CommandHandler,
    private val gameDays: () -> Map<GameDayId, GameDaySummary>,
    private val recaps: RecapMailer,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val log = LoggerFactory.getLogger(GameDayCloser::class.java)

    /** Ends every due game day. Recaps go out only for the ones this run actually ended. */
    suspend fun closeDue() {
        for (id in gameDays().dueToEnd(clock.instant())) {
            val decision = commands.handle(EndGameDayDecider, EndGameDay(id))
            if (decision is Decision.Accepted && decision.events.isNotEmpty()) {
                runCatching { recaps.send(gameDays().getValue(id)) }.onFailure { log.error("Recap for {} failed", id, it) }
            }
        }
    }

    fun start(every: Duration = Duration.ofMinutes(5)) = scope.launch {
        while (isActive) {
            runCatching { closeDue() }.onFailure { log.error("Closing game days failed", it) }
            delay(every.toMillis())
        }
    }
}
