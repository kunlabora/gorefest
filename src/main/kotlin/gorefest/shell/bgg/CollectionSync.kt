package gorefest.shell.bgg

import gorefest.core.PlayerId
import gorefest.shell.db.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Imports players' BGG collections: owned games replace what we had, and metadata for games we
 * haven't seen yet is fetched once for everyone.
 */
class CollectionSync(
    private val db: Database,
    private val bgg: BggGateway,
    private val catalog: Catalog,
    private val clock: Clock,
    private val scope: CoroutineScope,
    /** First wait after BGG says "queued"; doubles each time. */
    private val retryAfter: Duration = Duration.ofSeconds(5),
) {
    private val log = LoggerFactory.getLogger(CollectionSync::class.java)
    private val collections = Collections()
    private class Running(val username: String, val job: Job)

    private val running = ConcurrentHashMap<PlayerId, Running>()

    /**
     * Starts a sync unless one is already running for this player and username. A sync for a
     * different username (the player relinked) replaces the old one: it is cancelled and finished
     * before the new one writes anything.
     */
    fun request(player: PlayerId, username: String, priority: BggPriority): Job = synchronized(running) {
        val current = running[player]
        if (current != null && current.username == username && current.job.isActive) return current.job
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val me = this.coroutineContext[Job]
            try {
                current?.job?.cancelAndJoin()
                sync(player, username, priority)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: BggUnauthorized) {
                log.error("Collection sync for {} stopped: {}", username, e.message)
                runCatching { setStatus(player, username, SyncStatus.FAILED, "We can't reach BGG right now. We'll try again later.") }
            } catch (e: Exception) {
                log.error("Collection sync for {} failed", username, e)
                runCatching { setStatus(player, username, SyncStatus.FAILED, "Something went wrong. We'll try again later.") }
            } finally {
                running.computeIfPresent(player) { _, r -> if (r.job === me) null else r }
            }
        }
        running[player] = Running(username, job)
        job.start()
        job
    }

    /** Stops any sync and deletes the collection and sync rows: the player took their BGG account off. */
    suspend fun forget(player: PlayerId) {
        running.remove(player)?.job?.cancelAndJoin()
        db.transaction { collections.forget(player) }
    }

    suspend fun status(player: PlayerId): CollectionSyncState? = db.transaction { collections.status(player) }

    suspend fun collection(player: PlayerId) = db.transaction { collections.of(player) }

    /** Refreshes collections older than [maxAge] once per [every], and resumes unfinished syncs right away. */
    fun startRefreshing(every: Duration = Duration.ofHours(24), maxAge: Duration = Duration.ofDays(7)) = scope.launch {
        while (isActive) {
            val due = db.transaction { collections.due(before = clock.instant() - maxAge) }
            due.forEach { (player, username) -> request(player, username, BggPriority.ROUTINE) }
            delay(every.toMillis())
        }
    }

    private suspend fun sync(player: PlayerId, username: String, priority: BggPriority) {
        setStatus(player, username, SyncStatus.QUEUED, "Asking BGG for your collection")
        var wait = retryAfter
        repeat(MAX_TRIES) { attempt ->
            val result = try {
                bgg.call(priority) { collection(username) }
            } catch (e: BggUnauthorized) {
                throw e
            } catch (e: BggUnavailable) {
                log.info("BGG unavailable for {} (try {}): {}", username, attempt + 1, e.message)
                CollectionResult.Queued
            }
            when (result) {
                CollectionResult.UnknownUser -> return setStatus(player, username, SyncStatus.FAILED, "BGG doesn't know $username")
                CollectionResult.Queued -> {
                    setStatus(player, username, SyncStatus.QUEUED, "BGG is preparing your collection")
                    delay(wait.toMillis())
                    wait = wait.multipliedBy(2)
                }
                is CollectionResult.Ready -> {
                    val missing = catalog.missing(result.games)
                    missing.chunked(BggApi.THINGS_PER_CALL).forEachIndexed { i, chunk ->
                        setStatus(player, username, SyncStatus.QUEUED, "Fetching game details (${i * BggApi.THINGS_PER_CALL}/${missing.size})")
                        catalog.put(bgg.call(priority) { things(chunk) }, clock.instant())
                    }
                    db.transaction { collections.replace(player, result.games) }
                    return setStatus(player, username, SyncStatus.DONE, null)
                }
            }
        }
        setStatus(player, username, SyncStatus.FAILED, "BGG kept us waiting. We'll try again later.")
    }

    private suspend fun setStatus(player: PlayerId, username: String, status: SyncStatus, message: String?) =
        db.transaction { collections.setStatus(player, username, status, message, clock.instant()) }

    private companion object {
        const val MAX_TRIES = 6
    }
}
