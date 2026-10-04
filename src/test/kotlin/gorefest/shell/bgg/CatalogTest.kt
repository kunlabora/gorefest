package gorefest.shell.bgg

import gorefest.shell.TestDatabase
import gorefest.shell.db.Tx
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class CatalogTest {
    private val brass = DevCatalog.games.first { it.name == "Brass: Birmingham" }

    @BeforeEach
    fun reset() = kotlinx.coroutines.runBlocking { TestDatabase.reset() }

    @Test
    suspend fun `cached games survive a restart, with the stored format unchanged`() {
        Catalog(TestDatabase.db).put(listOf(brass), Instant.EPOCH)

        val reloaded = Catalog(TestDatabase.db).also { it.load() }
        reloaded[brass.id] shouldBe brass

        val json = TestDatabase.db.transaction {
            contextOf<Tx>().connection.createStatement().use { st -> st.executeQuery("select data from games").use { it.next(); it.getString(1) } }
        }
        json shouldContain "\"rank\""
    }

    @Test
    suspend fun `refreshing replaces the games we hold and adds none`() {
        val catalog = Catalog(TestDatabase.db)
        val other = DevCatalog.games.first { it.name == "Azul" }
        catalog.put(listOf(brass), Instant.EPOCH)

        catalog.refreshCached(listOf(brass.copy(thumbnail = "/new.webp"), other), Instant.EPOCH)

        catalog[brass.id]?.thumbnail shouldBe "/new.webp"
        catalog[other.id] shouldBe null
        Catalog(TestDatabase.db).also { it.load() }[brass.id]?.thumbnail shouldBe "/new.webp"
    }
}
