package gorefest.shell

import gorefest.shell.db.Database
import org.testcontainers.postgresql.PostgreSQLContainer

/** One Postgres container per test JVM, migrated once. Call [reset] between tests. */
object TestDatabase {
    private val container by lazy {
        PostgreSQLContainer("postgres:18-alpine").apply { start() }
    }

    val db by lazy {
        Database.connect(container.jdbcUrl, container.username, container.password).apply { migrate() }
    }

    suspend fun reset() {
        db.transaction { contextOf<gorefest.shell.db.Tx>().connection.createStatement().use { it.execute("truncate events, sessions, login_attempts, account_requests, collections, collection_syncs, games, players restart identity") } }
    }
}
