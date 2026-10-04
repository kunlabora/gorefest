package gorefest.shell.bgg

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DevCatalogTest {
    @Test
    fun `every cover the dev catalog points at is a file we ship`() {
        val missing = (DevCatalog.games + DevCatalog.coverGames)
            .flatMap { listOfNotNull(it.thumbnail, it.image) }
            .distinct()
            .filter { DevCatalog::class.java.getResource(it) == null }

        missing shouldBe emptyList()
    }
}
