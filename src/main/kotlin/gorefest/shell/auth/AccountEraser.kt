package gorefest.shell.auth

import gorefest.core.PlayerId
import gorefest.core.eventsourcing.Decision
import gorefest.core.player.ErasePlayer
import gorefest.core.player.ErasePlayerDecider
import gorefest.shell.db.Database
import gorefest.shell.eventstore.CommandHandler
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

/**
 * Erases an account by removing everything that says who it was: the email, name, sessions, collection and login
 * attempts. The event log is not rewritten. Its player ids then point at nobody, which is what makes them anonymous,
 * and [ErasePlayerDecider] records that the erasure happened. Safe to run again after a failure halfway.
 *
 * Every erasure is also posted to the admin's channel, as the id only: after restoring a backup the ids erased since
 * are the list to run again ([reapply]). The database can't hold that list, since a restore replaces it.
 */
class AccountEraser(
    private val db: Database,
    private val commands: CommandHandler,
    private val admin: AdminNotifier,
    private val players: Players = Players(),
    private val attempts: LoginAttempts = LoginAttempts(),
) {
    private val log = LoggerFactory.getLogger(AccountEraser::class.java)

    sealed interface Result {
        data object Erased : Result
        data object NoSuchPlayer : Result
    }

    suspend fun erase(id: PlayerId): Result = erase(id, announce = true)

    /** After a restore: erases each id again. Ids that no longer exist are skipped. Returns how many were erased. */
    suspend fun reapply(ids: List<PlayerId>): Int = ids.count { erase(it, announce = false) == Result.Erased }

    private suspend fun erase(id: PlayerId, announce: Boolean): Result {
        // The event first: if the rows were deleted and this then failed, there would be nothing left to retry from.
        val email = db.transaction { players.find(id)?.email }
        if (commands.handle(ErasePlayerDecider, ErasePlayer(id)) is Decision.Rejected) return Result.NoSuchPlayer
        db.transaction {
            players.delete(id)
            if (email != null) attempts.deleteForEmail(email)
        }
        log.info("Erased account {}", id)
        if (announce) tell(id)
        return Result.Erased
    }

    private suspend fun tell(id: PlayerId) {
        try {
            admin.notify("Account erased", "Player $id. Keep this id: after restoring a backup, put the ids erased since in REAPPLY_ERASURES (deploy/README.md).")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Could not tell the admin that {} was erased", id, e)
        }
    }
}
