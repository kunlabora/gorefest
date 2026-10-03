package gorefest.shell

import gorefest.core.GameDayId
import gorefest.core.PlayLogged
import gorefest.core.PlayerId
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.GameDays
import gorefest.core.gameday.Plays
import gorefest.core.gameday.ShelfGame
import gorefest.core.gameday.Shelves
import gorefest.core.matching.Matcher
import gorefest.core.player.BggAccounts
import gorefest.core.taste.FeatureModel
import gorefest.core.taste.Predictor
import gorefest.core.taste.TasteHistory
import gorefest.core.taste.TasteModel
import gorefest.core.taste.Tastes
import gorefest.core.taste.Wizard
import gorefest.shell.auth.Login
import gorefest.shell.auth.Mailer
import gorefest.shell.bgg.BggApi
import gorefest.shell.bgg.BggGateway
import gorefest.shell.bgg.BggVerification
import gorefest.shell.bgg.Catalog
import gorefest.shell.bgg.CollectionSync
import gorefest.shell.bgg.GameSearch
import gorefest.shell.db.Database
import gorefest.shell.eventstore.CommandHandler
import gorefest.shell.eventstore.EventStore
import gorefest.shell.eventstore.Projector
import gorefest.shell.gameday.GameDayCloser
import gorefest.shell.gameday.InviteLinks
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.slf4j.LoggerFactory
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
    bggRetry: Duration = Duration.ofSeconds(5),
) {
    /** Background work (the BGG worker); cancelled by [stop]. */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> LoggerFactory.getLogger(App::class.java).error("Background job failed", e) },
    )

    val store = EventStore()
    private val gameDayProjector = Projector(GameDays, db, store)
    private val bggAccountProjector = Projector(BggAccounts, db, store)
    private val shelfProjector = Projector(Shelves, db, store)
    private val tasteProjector = Projector(Tastes, db, store)
    private val playProjector = Projector(Plays, db, store)
    private val projectors = listOf(gameDayProjector, bggAccountProjector, shelfProjector, tasteProjector, playProjector)

    /** Read models: the latest snapshot of each projection, kept current by [commands] and [start]. */
    val gameDaysById: Map<GameDayId, GameDaySummary> get() = gameDayProjector.state
    val bggUsernames: Map<PlayerId, String> get() = bggAccountProjector.state
    val shelfByGameDay: Map<GameDayId, List<ShelfGame>> get() = shelfProjector.state
    val tasteByPlayer: Map<PlayerId, TasteHistory> get() = tasteProjector.state
    val playsByGameDay: Map<GameDayId, List<PlayLogged>> get() = playProjector.state
    val commands = CommandHandler(db, store, afterAppend = { catchUp() })

    val login = Login(db, commands, mailer, config, clock, ids)
    val invites = InviteLinks(config.secretKey)
    val bgg = BggGateway(bggApi, bggInterval)
    val bggVerification = BggVerification(config.secretKey)
    val catalog = Catalog(db)
    val collectionSync = CollectionSync(db, bgg, catalog, clock, scope, bggRetry)
    val gameSearch = GameSearch(catalog, bgg, clock)

    /** The taste model in use: v1 (features) since it beat v0 on replay. Swap here to compare versions. */
    val tasteModel: TasteModel = FeatureModel(catalog::get)
    val wizard = Wizard(tasteModel)
    val topUpWizard = Wizard(tasteModel, minPairs = 3, maxPairs = 5)
    val predictor = Predictor(tasteModel)
    val matcher = Matcher(predictor)
    val closer = GameDayCloser(this)

    suspend fun start() {
        db.migrate()
        catchUp()
        catalog.load()
        bgg.start(scope)
    }

    /** Background jobs that shouldn't run in tests: the periodic collection refresh. */
    fun startBackgroundJobs() {
        collectionSync.startRefreshing()
        closer.start()
    }

    fun stop() = scope.cancel()

    /** How fast these players play compared to BGG's box times, learned from all their play logs. */
    fun groupSpeed(players: List<gorefest.core.PlayerId>): Double =
        gorefest.core.gameday.groupSpeed(playsByGameDay.values.flatten(), catalog::get, players)

    /** Bring every read model up to date with the log; for code that appends events outside [commands]. */
    suspend fun catchUp() = projectors.forEach { it.catchUp() }
}
