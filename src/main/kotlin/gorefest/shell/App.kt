package gorefest.shell

import gorefest.core.gameday.GameDays
import gorefest.core.player.BggAccounts
import gorefest.shell.auth.Login
import gorefest.shell.auth.Mailer
import gorefest.shell.bgg.BggApi
import gorefest.shell.bgg.BggGateway
import gorefest.shell.bgg.BggVerification
import gorefest.shell.db.Database
import gorefest.shell.eventstore.EventStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.time.Clock
import java.time.Duration

/** Everything the web layer needs, wired once at startup. */
class App(
    val db: Database,
    val clock: Clock,
    val ids: IdGenerator = IdGenerator.random,
    val config: AppConfig = AppConfig.dev(),
    val mailer: Mailer,
    val bggApi: BggApi,
    bggInterval: Duration = Duration.ofSeconds(5),
) {
    /** Background work (the BGG worker); cancelled by [stop]. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val store = EventStore()
    val gameDays = Projector(GameDays, db, store)
    val bggAccounts = Projector(BggAccounts, db, store)
    private val projectors = listOf(gameDays, bggAccounts)
    val commands = CommandHandler(db, store, afterAppend = { catchUp() })

    val login = Login(db, commands, mailer, config, clock, ids)
    val invites = InviteLinks(config.secretKey)
    val bgg = BggGateway(bggApi, bggInterval)
    val bggVerification = BggVerification(config.secretKey)

    suspend fun start() {
        db.migrate()
        catchUp()
        bgg.start(scope)
    }

    fun stop() = scope.cancel()

    private suspend fun catchUp() = projectors.forEach { it.catchUp() }
}
