package gorefest.shell.web.bgg

import gorefest.core.BggAccountLinked
import gorefest.core.BggId
import gorefest.core.PlayerId
import gorefest.shell.bgg.BggPriority
import gorefest.shell.bgg.SyncStatus
import gorefest.shell.web.TestApp
import gorefest.shell.web.components.notice
import gorefest.shell.web.components.page
import gorefest.shell.web.fragment
import gorefest.shell.web.one
import gorefest.shell.web.page
import gorefest.shell.web.shouldReplace
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.uuid.Uuid

class CollectionTest {
    private val test = TestApp()
    private val app = test.app
    private val sync = app.collectionSync
    private val kimId = PlayerId(Uuid.random())
    private val bramId = PlayerId(Uuid.random())
    private lateinit var kim: String

    private val brass = BggId(224517)
    private val azul = BggId(230802)
    private val catan = BggId(13)

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        kim = test.loggedIn("Kim", id = kimId)
        test.loggedIn("Bram", id = bramId)
        app.db.transaction { app.store.append(listOf(BggAccountLinked(kimId, "KimPlays"))) }
        app.bggAccounts.catchUp()
        test.bgg.setCollection("KimPlays", setOf(brass, azul))
    }

    @Test
    suspend fun `a sync stores owned games and their details`() {
        sync.request(kimId, "KimPlays", BggPriority.LINK).join()

        sync.collection(kimId) shouldBe setOf(brass, azul)
        app.catalog[brass]!!.name shouldBe "Brass: Birmingham"
        sync.status(kimId)!!.status shouldBe SyncStatus.DONE
    }

    @Test
    suspend fun `BGG saying 'queued' is retried until the collection is ready`() {
        test.bgg.queuedFor["kimplays"] = 2

        sync.request(kimId, "KimPlays", BggPriority.LINK).join()

        sync.status(kimId)!!.status shouldBe SyncStatus.DONE
        sync.collection(kimId) shouldBe setOf(brass, azul)
    }

    @Test
    suspend fun `an unknown BGG user fails with a reason`() {
        test.bgg.missingUsers += "kimplays"

        sync.request(kimId, "KimPlays", BggPriority.LINK).join()

        sync.status(kimId)!!.let { (it.status to it.message) shouldBe (SyncStatus.FAILED to "BGG doesn't know KimPlays") }
    }

    @Test
    suspend fun `games removed on BGG disappear on the next sync`() {
        sync.request(kimId, "KimPlays", BggPriority.LINK).join()
        test.bgg.setCollection("KimPlays", setOf(azul))

        sync.request(kimId, "KimPlays", BggPriority.ROUTINE).join()

        sync.collection(kimId) shouldBe setOf(azul)
    }

    @Test
    suspend fun `details are fetched once per game, for everyone`() {
        test.bgg.setCollection("BramPlays", setOf(azul, catan))

        sync.request(kimId, "KimPlays", BggPriority.LINK).join()
        sync.request(bramId, "BramPlays", BggPriority.LINK).join()

        test.bgg.thingCalls.map { it.toSet() } shouldContainExactly listOf(setOf(brass, azul), setOf(catan))
    }

    @Test
    suspend fun `old collections are due for a refresh`() {
        sync.request(kimId, "KimPlays", BggPriority.LINK).join()
        suspend fun due() = app.db.transaction { gorefest.shell.bgg.Collections().due(before = test.clock.instant() - Duration.ofDays(7)) }

        due() shouldBe emptyList()
        test.clock.advance(Duration.ofDays(8))
        due() shouldContainExactlyInAnyOrder listOf(kimId to "KimPlays")
    }

    @Test
    fun `the page polls while importing, then lists the games alphabetically`() = test.webTest(session = kim) { client ->
        app.db.transaction { gorefest.shell.bgg.Collections().setStatus(kimId, "KimPlays", SyncStatus.QUEUED, "BGG is preparing your collection", test.clock.instant()) }

        val waiting = client.get("/collection").page().one("#collection")
        waiting.attr("hx-trigger") shouldBe "every 2s"
        waiting.attr("hx-get") shouldBe "/collection/status"
        waiting.one(".gf-notice p").text() shouldBe "BGG is preparing your collection"

        sync.request(kimId, "KimPlays", BggPriority.LINK).join()

        val done = client.get("/collection/status").fragment()
        done shouldReplace "collection"
        done.hasAttr("hx-trigger") shouldBe false
        done.select(".gf-game__name").map { it.text() } shouldContainExactly listOf("Azul", "Brass: Birmingham")
        done.select(".gf-game__meta").first()!!.text() shouldBe "30–45 min · medium-light · 2–4"
    }

    @Test
    fun `a failed import offers to try again`() = test.webTest(session = kim) { client ->
        test.bgg.missingUsers += "kimplays"
        sync.request(kimId, "KimPlays", BggPriority.LINK).join()

        val page = client.get("/collection").page()
        page.one("[role=alert]").text() shouldBe "BGG doesn't know KimPlays"
        page.one("#collection button").attr("hx-post") shouldBe "/collection/sync"
    }

    @Test
    fun `without a linked account the collection page sends you to link one`() {
        val other = kotlinx.coroutines.runBlocking { test.loggedIn("Other") }
        test.webTest(session = other) { client ->
            client.get("/collection").headers["Location"] shouldBe "/bgg?returnTo=%2Fcollection"
        }
    }
}
