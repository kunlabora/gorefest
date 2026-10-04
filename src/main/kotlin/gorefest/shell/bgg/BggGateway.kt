package gorefest.shell.bgg

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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
 *
 * The queue is bounded ([maxQueue]; beyond that calls fail fast with [BggUnavailable]), calls whose
 * caller gave up are dropped without costing a token, and every call and every wait has a timeout.
 */
class BggGateway(
    private val api: BggApi,
    private val interval: Duration = Duration.ofSeconds(5),
    private val burst: Int = 3,
    private val maxQueue: Int = 50,
    /** Longest one BGG call may run. */
    private val callTimeout: Duration = Duration.ofSeconds(45),
    /** Longest a caller waits for its answer, queue time included. */
    private val waitTimeout: Duration = Duration.ofSeconds(120),
) {
    private val log = LoggerFactory.getLogger(BggGateway::class.java)

    private class Job(val priority: BggPriority, val order: Long, val abandoned: () -> Boolean, val run: suspend () -> Unit)

    private val queue = PriorityQueue<Job>(compareBy<Job>({ it.priority }, { it.order }))
    private val order = AtomicLong()
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    suspend fun <T> call(priority: BggPriority, block: suspend BggApi.() -> T): T {
        val result = CompletableDeferred<T>()
        val job = Job(priority, order.incrementAndGet(), abandoned = { result.isCompleted }) {
            try {
                result.complete(withTimeout(callTimeout.toMillis()) { api.block() })
            } catch (e: TimeoutCancellationException) {
                result.completeExceptionally(BggUnavailable("BGG took longer than ${callTimeout.seconds}s"))
            } catch (e: Throwable) {
                result.completeExceptionally(e)
            }
        }
        synchronized(queue) {
            queue.removeAll { it.abandoned() }
            if (queue.size >= maxQueue) throw BggUnavailable("BGG queue is full")
            queue += job
        }
        wakeUp.trySend(Unit)
        try {
            return try {
                withTimeout(waitTimeout.toMillis()) { result.await() }
            } catch (e: TimeoutCancellationException) {
                throw BggUnavailable("BGG didn't answer within ${waitTimeout.seconds}s")
            }
        } finally {
            result.cancel() // a caller that gave up leaves the queue without costing a rate-limit token
        }
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
            val job = synchronized(queue) { generateSequence { queue.poll() }.firstOrNull { !it.abandoned() } }
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
                if (job.abandoned()) continue // gave up while we waited for a token: keep it for the next job
                tokens -= 1
            }
            runCatching { job.run() }.onFailure { log.warn("BGG call failed", it) }
        }
    }
}
