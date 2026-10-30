package gorefest.shell.gameday

import gorefest.contract.Obligation
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TitleGeneratorTest {
    @Test
    fun `the same seed gives the same titles`() {
        val a = TitleGenerator(Random(42))
        val b = TitleGenerator(Random(42))

        List(5) { a.next() } shouldBe List(5) { b.next() }
    }

    @Test
    @Obligation("GD-4")
    fun `a title is words from the lists, ending in a party word`() {
        val generator = TitleGenerator(Random(7))
        repeat(50) {
            val words = generator.next().split(" ")
            (words.size in 2..3) shouldBe true
            TitleGenerator.STARTS shouldContain words.first()
            TitleGenerator.ENDS shouldContain words.last()
        }
    }

    @Test
    @Obligation("GD-4")
    fun `another one is never the same title`() {
        val generator = TitleGenerator(Random(1))
        var title = generator.next()
        repeat(100) {
            val other = generator.next(not = title)
            other shouldNotBe title
            title = other
        }
    }
}
