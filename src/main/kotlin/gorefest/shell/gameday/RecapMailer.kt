package gorefest.shell.gameday

import gorefest.core.GameDayId
import gorefest.core.gameday.GameDayPlays
import gorefest.core.gameday.GameDaySummary
import gorefest.shell.AppConfig
import gorefest.shell.auth.Mail
import gorefest.shell.auth.Mailer
import gorefest.shell.auth.Players
import gorefest.shell.bgg.Catalog
import gorefest.shell.db.Database
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.time.Clock

/** Mails everyone who joined a game day a [Recap] of what was played. */
class RecapMailer(
    private val db: Database,
    private val players: Players,
    private val plays: () -> GameDayPlays,
    private val catalog: Catalog,
    private val mailer: Mailer,
    private val config: AppConfig,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(RecapMailer::class.java)

    /** Sends to each recipient independently: one bad address must not stop the others (nothing retries a recap). */
    suspend fun send(gameDay: GameDaySummary) {
        val (names, emails) = db.transaction { players.names(gameDay.players) to players.emails(gameDay.players) }
        val recap = Recap.of(plays().on(gameDay.id), { catalog[it]?.name }, names::get)
        emails.forEach { (player, email) ->
            try {
                mailer.send(
                    Mail(
                        to = email,
                        subject = "${gameDay.title}: what you played",
                        text = text(gameDay.title, gameDay.id, names[player], recap),
                        sentAt = clock.instant(),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Recap for {} to player {} failed", gameDay.id, player, e)
            }
        }
    }

    private fun text(title: String, id: GameDayId, recipient: String?, recap: Recap): String {
        val hi = recipient?.let { "Hi $it,\n\n" } ?: ""
        val body = if (recap.lines.isEmpty()) {
            "Nobody logged a game this time. Log what you play at the checkpoints and the suggestions get better every game day."
        } else {
            val lines = recap.lines.joinToString("\n") { "- ${it.game}: ${it.players.joinToString(", ")} · ${it.minutes} min" }
            "What you played:\n\n$lines\n\n${recap.games} ${if (recap.games == 1) "game" else "games"}, ${recap.minutes} minutes at the table."
        }
        return "$hi$title is over. $body\n\nThanks for coming. See you at the next one.\n${config.baseUrl}/gamedays/$id"
    }
}
