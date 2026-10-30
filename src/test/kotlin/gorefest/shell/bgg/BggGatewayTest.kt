package gorefest.shell.bgg

import gorefest.contract.Obligation
import gorefest.core.BggId
import gorefest.core.game.Game
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
    @Obligation("BGG-25")
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
    @Obligation("BGG-24")
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
    @Obligation("BGG-26")
    suspend fun `a cancelled caller's job is skipped without costing a rate-limit token`() {
        val api = Recording()
        // one token refills per hour: only a skipped job lets the third call through in time
        val gateway = BggGateway(api, interval = Duration.ofHours(1), burst = 2)
        val scope = CoroutineScope(Dispatchers.Default)
        gateway.start(scope)

        coroutineScope {
            val first = async { gateway.call(BggPriority.LIVE) { search("first") } }
            delay(50) // the worker is busy with "first" (it took the first token)
            val cancelled = async { gateway.call(BggPriority.LIVE) { search("cancelled") } }
            delay(50)
            cancelled.cancel()
            delay(50)
            val third = async { gateway.call(BggPriority.LIVE) { search("third") } }
            delay(50)
            api.release.complete(Unit)
            first.await()
            kotlinx.coroutines.withTimeout(5_000) { third.await() }
        }

        api.calls shouldBe listOf("first", "third")
        scope.cancel()
    }

    @Test
    @Obligation("BGG-27")
    suspend fun `a full queue fails fast`() {
        val api = Recording()
        val gateway = BggGateway(api, interval = Duration.ZERO, maxQueue = 2)
        val scope = CoroutineScope(Dispatchers.Default)
        gateway.start(scope)

        coroutineScope {
            val running = async { gateway.call(BggPriority.ROUTINE) { search("running") } }
            delay(50)
            val queued = List(2) { i -> async { gateway.call(BggPriority.ROUTINE) { search("queued-$i") } } }
            delay(50)

            runCatching { gateway.call(BggPriority.LIVE) { search("overflow") } }.exceptionOrNull().shouldBeInstanceOf<BggUnavailable>()

            api.release.complete(Unit)
            (listOf(running) + queued).awaitAll()
        }
        api.calls shouldBe listOf("running", "queued-0", "queued-1")
        scope.cancel()
    }

    @Test
    @Obligation("BGG-27")
    suspend fun `a call that hangs times out as unavailable`() {
        val hanging = object : BggApi by FakeBggApi() {
            override suspend fun search(query: String): List<SearchHit> = CompletableDeferred<List<SearchHit>>().await()
        }
        val gateway = BggGateway(hanging, interval = Duration.ZERO, callTimeout = Duration.ofMillis(50))
        val scope = CoroutineScope(Dispatchers.Default)
        gateway.start(scope)

        runCatching { gateway.call(BggPriority.LIVE) { search("x") } }.exceptionOrNull().shouldBeInstanceOf<BggUnavailable>()
        scope.cancel()
    }

    @Test
    @Obligation("BGG-27")
    suspend fun `a caller stops waiting after the wait timeout`() {
        val gateway = BggGateway(FakeBggApi(), interval = Duration.ZERO, waitTimeout = Duration.ofMillis(50))
        // the worker is never started, so the call just sits in the queue
        runCatching { gateway.call(BggPriority.LIVE) { search("x") } }.exceptionOrNull().shouldBeInstanceOf<BggUnavailable>()
    }

    @Test
    @Obligation("BGG-27")
    suspend fun `failures reach the caller`() {
        val gateway = BggGateway(FakeBggApi(), interval = Duration.ZERO)
        val scope = CoroutineScope(Dispatchers.Default)
        gateway.start(scope)

        runCatching { gateway.call(BggPriority.LIVE) { throw BggUnavailable("429") } }.exceptionOrNull()?.message shouldBe "429"
        scope.cancel()
    }
}
