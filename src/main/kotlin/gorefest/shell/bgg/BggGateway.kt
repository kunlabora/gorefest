package gorefest.shell.bgg

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicLong

/** Who's waiting decides who goes first. */
enum class BggPriority { LIVE, LINK, ROUTINE }

/**
 * The one way to reach BGG: a single worker coroutine taking calls from a priority queue,
 * paced by a token bucket (a small burst, then one call per [interval]).
 * Live searches and fresh account links jump ahead of routine refreshes.
 */
class BggGateway(
    private val api: BggApi,
    private val interval: Duration = Duration.ofSeconds(5),
    private val burst: Int = 3,
) {
    private val log = LoggerFactory.getLogger(BggGateway::class.java)

    private class Job(val priority: BggPriority, val order: Long, val run: suspend () -> Unit)

    private val queue = PriorityQueue<Job>(compareBy<Job>({ it.priority }, { it.order }))
    private val order = AtomicLong()
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    suspend fun <T> call(priority: BggPriority, block: suspend BggApi.() -> T): T {
        val result = CompletableDeferred<T>()
        val job = Job(priority, order.incrementAndGet()) {
            try {
                result.complete(api.block())
            } catch (e: Throwable) {
                result.completeExceptionally(e)
            }
        }
        synchronized(queue) { queue += job }
        wakeUp.trySend(Unit)
        return result.await()
    }

    private val started = java.util.concurrent.atomic.AtomicBoolean()

    /** Starts the worker in [scope] (once; later calls do nothing). It runs until the scope is cancelled. */
    fun start(scope: CoroutineScope) {
        if (started.compareAndSet(false, true)) scope.launch { work() }
    }

    private suspend fun work() {
        var tokens = burst.toDouble()
        var last = System.nanoTime()
        val perToken = interval.toNanos().toDouble()
        while (true) {
            val job = synchronized(queue) { queue.poll() }
            if (job == null) {
                wakeUp.receive()
                continue
            }
            if (perToken > 0) {
                val now = System.nanoTime()
                tokens = minOf(burst.toDouble(), tokens + (now - last) / perToken)
                last = now
                if (tokens < 1) {
                    delay(((1 - tokens) * perToken / 1_000_000).toLong() + 1)
                    tokens = 1.0
                    last = System.nanoTime()
                }
                tokens -= 1
            }
            runCatching { job.run() }.onFailure { log.warn("BGG call failed", it) }
        }
    }
}
