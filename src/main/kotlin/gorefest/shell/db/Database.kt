package gorefest.shell.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.sql.Connection
import javax.sql.DataSource

/**
 * An open database transaction. Functions that must run inside one declare `context(tx: Tx)`,
 * so the compiler refuses to call them outside [Database.transaction].
 */
class Tx internal constructor(val connection: Connection)

class Database(private val dataSource: DataSource) : AutoCloseable {

    /** Closes the connection pool (a no-op for data sources that aren't closeable). */
    override fun close() {
        (dataSource as? AutoCloseable)?.close()
    }

    suspend fun <T> transaction(block: suspend context(Tx) () -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                block(Tx(connection)).also { connection.commit() }
            } catch (e: Throwable) {
                try {
                    connection.rollback()
                } catch (rollbackFailure: Throwable) {
                    e.addSuppressed(rollbackFailure)
                }
                throw e
            }
        }
    }

    fun migrate() {
        Flyway.configure().dataSource(dataSource).load().migrate()
    }

    companion object {
        fun connect(url: String, user: String, password: String) = Database(
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = url
                username = user
                this.password = password
                // Request handling, the BGG worker and the projectors all hold connections at once:
                // 5 starved under load. Fail after 10 s instead of Hikari's 30 s so a stuck pool shows up quickly.
                maximumPoolSize = 10
                connectionTimeout = 10_000
            }),
        )
    }
}
