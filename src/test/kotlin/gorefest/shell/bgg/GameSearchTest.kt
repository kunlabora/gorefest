package gorefest.shell.bgg

import gorefest.contract.Obligation
import gorefest.core.BggId
import gorefest.core.game.Game
import gorefest.shell.TestClock
import gorefest.shell.TestDatabase
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class GameSearchTest {
    private val clock = TestClock()
    private val searches = AtomicInteger()
    private var failing = false
    private val api = object : BggApi by FakeBggApi() {
        override suspend fun search(query: String): List<SearchHit> {
            searches.incrementAndGet()
            if (failing) throw BggUnavailable("429")
            return emptyList()
        }
    }
    private val scope = CoroutineScope(Dispatchers.Default)
    private val gateway = BggGateway(api, Duration.ZERO).also { it.start(scope) }

    @BeforeEach
    fun reset() = kotlinx.coroutines.runBlocking { TestDatabase.reset() }

    private fun search() = GameSearch(Catalog(TestDatabase.db), gateway, clock)

    @Test
    @Obligation("BGG-22")
    suspend fun `a search BGG had nothing for isn't repeated until the entry expires`() {
        val search = search()

        search.search("zzzzqq").games shouldBe emptyList<Game>()
        search.search("ZZZZQQ ").games shouldBe emptyList<Game>()
        searches.get() shouldBe 1

        clock.advance(Duration.ofMinutes(11))
        search.search("zzzzqq")
        searches.get() shouldBe 2
        scope.cancel()
    }

    @Test
    @Obligation("BGG-21", "BGG-22")
    suspend fun `a failed search is remembered briefly and still reports BGG as unavailable`() {
        val search = search()
        failing = true

        search.search("zzzzqq").bggUnavailable shouldBe true
        search.search("zzzzqq").bggUnavailable shouldBe true
        searches.get() shouldBe 1

        clock.advance(Duration.ofMinutes(2))
        failing = false
        search.search("zzzzqq").bggUnavailable shouldBe false
        searches.get() shouldBe 2
        scope.cancel()
    }
}
