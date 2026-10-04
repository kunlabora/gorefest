package gorefest.shell.bgg

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FakeBggApiTest {
    @Test
    suspend fun `a dev collection always includes every cover game`() {
        val api = FakeBggApi(DevCatalog.games + DevCatalog.covers)

        val ready = api.collection("anyone") as CollectionResult.Ready

        ready.games shouldContainAll DevCatalog.covers.map { it.id }
        ready.games.size shouldBe DevCatalog.covers.size + 15
    }

    @Test
    suspend fun `the plain catalog keeps its fifteen-game collections`() {
        val ready = FakeBggApi().collection("anyone") as CollectionResult.Ready

        ready.games.size shouldBe 15
    }
}
