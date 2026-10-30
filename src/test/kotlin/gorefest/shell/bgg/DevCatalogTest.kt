package gorefest.shell.bgg

import gorefest.contract.Obligation
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DevCatalogTest {
    @Test
    @Obligation("BGG-31")
    fun `every cover the dev catalog points at is a file we ship`() {
        val missing = (DevCatalog.games + DevCatalog.coverGames)
            .flatMap { listOfNotNull(it.thumbnail, it.image) }
            .distinct()
            .filter { DevCatalog::class.java.getResource(it) == null }

        missing shouldBe emptyList()
    }

    @Test
    @Obligation("BGG-31")
    fun `every dev game has cover art, and every cover file belongs to a game`() {
        val games = DevCatalog.games + DevCatalog.coverGames

        games.filter { it.thumbnail == null }.map { it.name } shouldBe emptyList()

        val used = games.mapNotNull { it.thumbnail }.map { it.substringAfterLast('/') }.toSet()
        val shipped = java.io.File("src/main/resources/static/covers").list().orEmpty().toSet()
        (shipped - used) shouldBe emptySet()
    }
}
