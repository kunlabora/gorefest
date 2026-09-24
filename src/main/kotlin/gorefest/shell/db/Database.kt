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

class Database(private val dataSource: DataSource) {

    suspend fun <T> transaction(block: suspend context(Tx) () -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                block(Tx(connection)).also { connection.commit() }
            } catch (e: Throwable) {
                connection.rollback()
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
                maximumPoolSize = 5
            }),
        )
    }
}
