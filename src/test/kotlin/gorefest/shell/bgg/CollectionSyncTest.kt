package gorefest.shell.bgg

import gorefest.contract.Obligation
import gorefest.core.BggAccountLinked
import gorefest.core.BggId
import gorefest.core.PlayerId
import gorefest.shell.web.TestApp
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid

class CollectionSyncTest {
    private val test = TestApp()
    private val app = test.app
    private val kimId = PlayerId(Uuid.random())

    private val brass = BggId(224517)
    private val azul = BggId(230802)

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        test.loggedIn("Kim", id = kimId)
        app.db.transaction { app.store.append(listOf(BggAccountLinked(kimId, "NewName"))) }
        app.catchUp()
    }

    @Test
    @Obligation("BGG-14")
    suspend fun `relinking to another username during an in-flight sync ends with the new collection`() {
        test.bgg.setCollection("OldName", setOf(brass))
        test.bgg.setCollection("NewName", setOf(azul))
        test.bgg.queuedFor["oldname"] = 100 // the old sync keeps being told "queued"

        val old = app.collectionSync.request(kimId, "OldName", BggPriority.LINK)
        val new = app.collectionSync.request(kimId, "NewName", BggPriority.LINK)
        new.join()
        old.join()

        app.collectionSync.collection(kimId) shouldBe setOf(azul)
        app.collectionSync.status(kimId)!!.let { it.username to it.status } shouldBe ("NewName" to SyncStatus.DONE)
    }

    @Test
    @Obligation("BGG-13")
    suspend fun `a rejected token fails the sync at once instead of retrying`() {
        val calls = AtomicInteger()
        val rejecting = object : BggApi by FakeBggApi() {
            override suspend fun collection(username: String): CollectionResult {
                calls.incrementAndGet()
                throw BggUnauthorized("401")
            }
        }
        val scope = CoroutineScope(Dispatchers.Default)
        val gateway = BggGateway(rejecting, Duration.ZERO).also { it.start(scope) }
        val sync = CollectionSync(app.db, gateway, app.catalog, test.clock, scope, Duration.ofMillis(1))

        sync.request(kimId, "NewName", BggPriority.LINK).join()

        calls.get() shouldBe 1
        sync.status(kimId)!!.status shouldBe SyncStatus.FAILED
        scope.cancel()
    }
}
