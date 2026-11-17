package gorefest.shell.web.releasenotes

import gorefest.contract.Obligation
import gorefest.shell.web.components.APP_VERSION
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeSortedDescendingBy
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate
import org.junit.jupiter.api.Test

class ReleaseNotesTest {
    @Test
    fun `parses versions, dates and the three kinds, skipping the title, comments and blank lines`() {
        val notes = ReleaseNotes.parse(
            """
            # What's new

            <!--
            ## not a version
            -->

            ## 2.1.0 · 2026-10-12

            ### New

            - Polls.
            - Game nights.

            ### Fixed

            - Long titles wrap.

            ## 2.0.0 · 2026-10-08
            <!-- one-line comment -->
            ### Changed

            - Every screen redrawn.
            """.trimIndent(),
        )

        notes.releases shouldBe listOf(
            Release("2.1.0", LocalDate.of(2026, 10, 12), new = listOf("Polls.", "Game nights."), fixed = listOf("Long titles wrap.")),
            Release("2.0.0", LocalDate.of(2026, 10, 8), changed = listOf("Every screen redrawn.")),
        )
        notes.of("2.0.0").shouldNotBeNull().version shouldBe "2.0.0"
        notes.of("9.9.9") shouldBe null
    }

    @Test
    fun `a file that strays from the shape names the line`() {
        fun failure(text: String) = shouldThrow<ReleaseNotesFormatException> { ReleaseNotes.parse(text) }

        failure("## 2.0.0 · 2026-10-08\n\n- outside a kind").line shouldBe 3
        failure("## 2.0.0 · 2026-10-08\n### Improved").message!! shouldContain "New, Changed or Fixed"
        failure("## 2.0.0 · 8 Oct 2026").line shouldBe 1
        failure("## 2.0.0 · 2026-13-01").message!! shouldContain "yyyy-mm-dd"
        failure("## version two · 2026-10-08").line shouldBe 1
        failure("### New\n- too early").line shouldBe 1
        failure("## 2.0.0 · 2026-10-08\nJust some text").line shouldBe 2
        failure("## 2.0.0 · 2026-10-08\n## 2.0.0 · 2026-10-09").message!! shouldContain "twice"
    }

    @Test
    @Obligation("WEB-18")
    fun `the bundled notes parse and run newest first`() {
        val releases = ReleaseNotes.bundled.releases
        releases.shouldBeSortedDescendingBy { it.date }
        releases.map { it.version.split('.').map(String::toInt) } shouldBe
            releases.map { it.version.split('.').map(String::toInt) }.sortedWith(compareByDescending<List<Int>> { it[0] }.thenByDescending { it[1] }.thenByDescending { it[2] })
        releases.forEach { (it.new + it.changed + it.fixed).size shouldNotBe 0 }
    }

    @Test
    @Obligation("WEB-19")
    fun `the version the footer shows has its notes`() {
        APP_VERSION shouldNotBe "dev"
        ReleaseNotes.bundled.of(APP_VERSION).shouldNotBeNull()
    }
}
