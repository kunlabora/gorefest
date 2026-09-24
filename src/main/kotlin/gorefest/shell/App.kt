package gorefest.shell

import gorefest.core.gameday.GameDays
import gorefest.shell.db.Database
import gorefest.shell.eventstore.EventStore
import java.time.Clock

/** Everything the web layer needs, wired once at startup. */
class App(val db: Database, val clock: Clock) {
    val store = EventStore()
    val gameDays = Projector(GameDays, db, store)
    val commands = CommandHandler(db, store, afterAppend = { gameDays.catchUp() })

    suspend fun start() {
        db.migrate()
        gameDays.catchUp()
    }
}
