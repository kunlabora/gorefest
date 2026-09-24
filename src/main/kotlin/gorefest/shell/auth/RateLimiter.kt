package gorefest.shell.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** At most [limit] events per key in any [window]. In memory: fine for one server, reset on restart. */
class RateLimiter(private val limit: Int, private val window: Duration, private val clock: Clock) {
    private val events = ConcurrentHashMap<String, ArrayDeque<Instant>>()

    fun tryAcquire(key: String): Boolean {
        val now = clock.instant()
        val queue = events.computeIfAbsent(key) { ArrayDeque() }
        synchronized(queue) {
            while (queue.isNotEmpty() && !queue.first().isAfter(now - window)) queue.removeFirst()
            if (queue.size >= limit) return false
            queue.addLast(now)
            return true
        }
    }
}
