package gorefest.shell.bgg

import gorefest.contract.Obligation
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FakeBggApiTest {
    @Test
    @Obligation("BGG-30")
    suspend fun `a dev collection always includes every cover game`() {
        val api = FakeBggApi(DevCatalog.games + DevCatalog.coverGames)

        val ready = api.collection("anyone") as CollectionResult.Ready

        ready.games shouldContainAll DevCatalog.coverGames.map { it.id }
        ready.games.size shouldBe DevCatalog.coverGames.size + 15
    }

    @Test
    @Obligation("BGG-30")
    suspend fun `the plain catalog keeps its fifteen-game collections`() {
        val ready = FakeBggApi().collection("anyone") as CollectionResult.Ready

        ready.games.size shouldBe 15
    }
}
