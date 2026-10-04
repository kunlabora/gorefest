package gorefest.shell.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/** At most [limit] events per key in any [window]. In memory: fine for one server, reset on restart. */
class RateLimiter(private val limit: Int, private val window: Duration, private val clock: Clock) {
    private val events = ConcurrentHashMap<String, ArrayDeque<Instant>>()
    private val lastSweep = AtomicReference(Instant.MIN)

    /** Keys currently tracked; stays bounded because idle keys are evicted. */
    val trackedKeys: Int get() = events.size

    fun tryAcquire(key: String): Boolean {
        val now = clock.instant()
        sweep(now)
        var allowed = false
        events.compute(key) { _, existing ->
            val queue = existing ?: ArrayDeque()
            queue.prune(now)
            allowed = queue.size < limit
            if (allowed) queue.addLast(now)
            queue.takeIf { it.isNotEmpty() }
        }
        return allowed
    }

    /** Once per window, drops keys whose events have all expired. */
    private fun sweep(now: Instant) {
        val last = lastSweep.get()
        if (last.plus(window).isAfter(now) || !lastSweep.compareAndSet(last, now)) return
        events.keys.forEach { key ->
            events.computeIfPresent(key) { _, queue -> queue.apply { prune(now) }.takeIf { it.isNotEmpty() } }
        }
    }

    private fun ArrayDeque<Instant>.prune(now: Instant) {
        while (isNotEmpty() && !first().isAfter(now - window)) removeFirst()
    }
}
