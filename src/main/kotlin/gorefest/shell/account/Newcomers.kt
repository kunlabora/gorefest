package gorefest.shell.account

import gorefest.core.PlayerId
import gorefest.shell.auth.AccountEraser
import gorefest.shell.auth.Mail
import gorefest.shell.auth.Mailer
import gorefest.shell.auth.Player
import gorefest.shell.auth.Players
import gorefest.shell.db.Database
import java.time.Clock

/**
 * Everyone who signs up is in at once; the admin hears about each one (`Login.rename`) and rejects the ones they
 * don't know from `/admin/newcomers`. Rejecting is an erasure, since the newcomer may already have joined something.
 */
class Newcomers(
    private val db: Database,
    private val players: Players,
    private val eraser: AccountEraser,
    private val mailer: Mailer,
    private val clock: Clock,
) {
    /** The latest named accounts, newest first. */
    suspend fun latest(): List<Player> = db.transaction { players.newest(LATEST) }

    /** Erases the account, then tells the person (their address is gone afterwards). False if there was no such account. */
    suspend fun reject(id: PlayerId): Boolean {
        val player = db.transaction { players.find(id) }
        val erased = player != null && eraser.erase(id) is AccountEraser.Result.Erased
        if (erased) {
            mailer.send(
                Mail(
                    player.email, "About your Boardfest account",
                    "Sorry, Boardfest is a private group, by invitation only, so your account is removed: your email, name and sign-ins are gone.\n" +
                        "If you know someone in the group, ask them for an invite and sign up again.\n",
                    clock.instant(),
                ),
            )
        }
        return erased
    }

    companion object {
        const val LATEST = 30
    }
}
