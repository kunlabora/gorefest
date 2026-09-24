package gorefest.shell.bgg

import gorefest.core.BggId
import gorefest.core.bgg.Game
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Collections

class BggGatewayTest {
    /** Records which searches ran, in order. The first call blocks until released. */
    private class Recording : BggApi {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val release = CompletableDeferred<Unit>()
        override suspend fun search(query: String): List<SearchHit> {
            if (calls.isEmpty()) release.await()
            calls += query
            return emptyList()
        }
        override suspend fun user(name: String) = null
        override suspend fun collection(username: String) = CollectionResult.Queued
        override suspend fun things(ids: Collection<BggId>) = emptyList<Game>()
    }

    @Test
    suspend fun `live calls jump ahead of queued routine ones`() {
        val api = Recording()
        val gateway = BggGateway(api, interval = Duration.ZERO)
        val scope = CoroutineScope(Dispatchers.Default)
        gateway.start(scope)

        coroutineScope {
            val first = async { gateway.call(BggPriority.ROUTINE) { search("routine-1") } }
            delay(50) // the worker is now busy with routine-1
            val queued = listOf("routine-2", "routine-3").map { q -> async { gateway.call(BggPriority.ROUTINE) { search(q) } } }
            delay(50)
            val live = async { gateway.call(BggPriority.LIVE) { search("live") } }
            delay(50)
            api.release.complete(Unit)
            (listOf(first, live) + queued).awaitAll()
        }

        api.calls shouldBe listOf("routine-1", "live", "routine-2", "routine-3")
        scope.cancel()
    }

    @Test
    suspend fun `after a burst, calls are paced`() {
        val api = FakeBggApi()
        val gateway = BggGateway(api, interval = Duration.ofMillis(100), burst = 2)
        val scope = CoroutineScope(Dispatchers.Default)
        gateway.start(scope)

        val started = System.nanoTime()
        coroutineScope { List(4) { async { gateway.call(BggPriority.ROUTINE) { search("x") } } }.awaitAll() }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        (elapsedMs >= 180) shouldBe true // 2 free, then 2 × 100 ms
        scope.cancel()
    }

    @Test
    suspend fun `failures reach the caller`() {
        val gateway = BggGateway(FakeBggApi(), interval = Duration.ZERO)
        val scope = CoroutineScope(Dispatchers.Default)
        gateway.start(scope)

        runCatching { gateway.call(BggPriority.LIVE) { throw BggUnavailable("429") } }.exceptionOrNull()?.message shouldBe "429"
        scope.cancel()
    }
}
