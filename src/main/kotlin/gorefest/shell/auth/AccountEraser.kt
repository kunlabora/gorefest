package gorefest.shell.auth

import gorefest.core.PlayerId
import gorefest.core.eventsourcing.Decision
import gorefest.core.player.ErasePlayer
import gorefest.core.player.ErasePlayerDecider
import gorefest.shell.db.Database
import gorefest.shell.eventstore.CommandHandler

/**
 * Erases an account by removing everything that says who it was: the email, name, sessions, collection and login
 * attempts. The event log is not rewritten. Its player ids then point at nobody, which is what makes them anonymous,
 * and [ErasePlayerDecider] records that the erasure happened. Safe to run again after a failure halfway.
 */
class AccountEraser(
    private val db: Database,
    private val commands: CommandHandler,
    private val players: Players = Players(),
    private val attempts: LoginAttempts = LoginAttempts(),
) {
    sealed interface Result {
        data object Erased : Result
        data object NoSuchPlayer : Result
    }

    suspend fun erase(id: PlayerId): Result {
        // The event first: if the rows were deleted and this then failed, there would be nothing left to retry from.
        val email = db.transaction { players.find(id)?.email }
        if (commands.handle(ErasePlayerDecider, ErasePlayer(id)) is Decision.Rejected) return Result.NoSuchPlayer
        db.transaction {
            players.delete(id)
            if (email != null) attempts.deleteForEmail(email)
        }
        return Result.Erased
    }
}
