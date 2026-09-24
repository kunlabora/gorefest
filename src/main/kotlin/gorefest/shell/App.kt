package gorefest.shell

import gorefest.core.gameday.GameDays
import gorefest.shell.auth.Login
import gorefest.shell.auth.Mailer
import gorefest.shell.db.Database
import gorefest.shell.eventstore.EventStore
import java.time.Clock

/** Everything the web layer needs, wired once at startup. */
class App(
    val db: Database,
    val clock: Clock,
    val ids: IdGenerator = IdGenerator.random,
    val config: AppConfig = AppConfig.dev(),
    val mailer: Mailer,
) {
    val store = EventStore()
    val gameDays = Projector(GameDays, db, store)
    val commands = CommandHandler(db, store, afterAppend = { gameDays.catchUp() })
    val login = Login(db, commands, mailer, config, clock, ids)
    val invites = InviteLinks(config.secretKey)

    suspend fun start() {
        db.migrate()
        gameDays.catchUp()
    }
}
