package gorefest.shell.account

import gorefest.core.PlayerId
import gorefest.shell.AppConfig
import gorefest.shell.auth.AccountEraser
import gorefest.shell.auth.Mail
import gorefest.shell.auth.Mailer
import gorefest.shell.auth.Players
import gorefest.shell.db.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException

/**
 * Accounts nobody signs in to are not kept forever: after [AppConfig.inactiveAccountMonths] without a sign-in
 * the person gets a warning, and 30 days later, if they haven't signed in, the account is erased ([AccountEraser]).
 * Signing in at any point resets it. The admin, and people hosting a game day still to come, are left alone.
 */
class InactiveAccounts(
    private val db: Database,
    private val players: Players,
    private val eraser: AccountEraser,
    private val mailer: Mailer,
    private val config: AppConfig,
    private val clock: Clock,
    private val hostsUpcomingGameDay: (PlayerId) -> Boolean,
) {
    private val log = LoggerFactory.getLogger(InactiveAccounts::class.java)

    /** Warns and erases whoever is due. Returns how many were warned and how many erased. */
    suspend fun sweep(): Pair<Int, Int> {
        if (config.inactiveAccountMonths <= 0) return 0 to 0
        val now = clock.instant()
        val inactiveSince = ZonedDateTime.ofInstant(now, ZoneOffset.UTC).minusMonths(config.inactiveAccountMonths.toLong()).toInstant()
        val skip = { id: PlayerId, email: String -> config.isAdmin(email) || hostsUpcomingGameDay(id) }

        val toWarn = db.transaction { players.toWarn(inactiveSince) }.filterNot { skip(it.id, it.email) }
        toWarn.forEach { player ->
            mailer.send(
                Mail(
                    player.email, "Your Boardfest account will be deleted",
                    "Hi ${player.name ?: "there"},\n\nYou haven't signed in to Boardfest for ${config.inactiveAccountMonths} months. " +
                        "In $WARNING_DAYS days your account is deleted: your email, name, collection and sign-ins are removed.\n" +
                        "To keep it, just sign in once: ${config.baseUrl}/\n",
                    now,
                ),
            )
            db.transaction { players.markWarned(player.id, now) }
        }

        val toErase = db.transaction { players.toErase(now - Duration.ofDays(WARNING_DAYS)) }.filterNot { skip(it.id, it.email) }
        toErase.forEach { player ->
            if (eraser.erase(player.id) == AccountEraser.Result.Erased) {
                mailer.send(Mail(player.email, "Your Boardfest account is deleted", "Your Boardfest account was deleted because you hadn't signed in for a long time, as the warning said.\nYou can always sign up again.\n", now))
            }
        }
        return toWarn.size to toErase.size
    }

    /** Sweeps once a day until [scope] is cancelled. A failed sweep is logged and tried again tomorrow. */
    fun start(scope: CoroutineScope, every: Duration = Duration.ofDays(1)) = scope.launch {
        while (isActive) {
            try {
                sweep()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Inactive-account sweep failed", e)
            }
            delay(every.toMillis())
        }
    }

    companion object {
        const val WARNING_DAYS = 30L
    }
}
