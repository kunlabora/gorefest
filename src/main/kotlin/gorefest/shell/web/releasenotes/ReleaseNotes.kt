package gorefest.shell.web.releasenotes

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** One released version and what it brought, in players' words (U24). */
data class Release(
    val version: String,
    val date: LocalDate,
    val new: List<String> = emptyList(),
    val changed: List<String> = emptyList(),
    val fixed: List<String> = emptyList(),
)

/** A line of `release-notes.md` that strays from its shape; [line] counts from 1. */
class ReleaseNotesFormatException(val line: Int, reason: String) : IllegalArgumentException("release-notes.md line $line: $reason")

/** The release notes, newest first, as written in `src/main/resources/release-notes.md`. */
class ReleaseNotes(val releases: List<Release>) {
    fun of(version: String): Release? = releases.firstOrNull { it.version == version }

    companion object {
        private val VERSION = Regex("""^## (\d+\.\d+\.\d+) · (\S+)$""")
        private val KINDS = setOf("New", "Changed", "Fixed")

        /** Read once from the classpath; a broken file fails at startup and in the tests, not on a request. */
        val bundled: ReleaseNotes by lazy {
            val text = ReleaseNotes::class.java.getResourceAsStream("/release-notes.md")?.use { it.reader().readText() }
                ?: error("release-notes.md is missing from the classpath")
            parse(text)
        }

        /**
         * Parses the strict shape: `## <version> · <yyyy-mm-dd>`, then `### New`, `### Changed` or `### Fixed`, each
         * followed by `- ` lines. Blank lines, the `# ` title and `<!-- -->` comments are skipped.
         */
        fun parse(text: String): ReleaseNotes {
            val lines = text.lines()
            return Parser().apply { lines.forEachIndexed { i, line -> read(i + 1, line.trimEnd()) } }.notes(lines.size)
        }

        private fun fail(line: Int, reason: String): Nothing = throw ReleaseNotesFormatException(line, reason)

        /** Reads the file line by line, remembering the release and kind it is in. */
        private class Parser {
            private val releases = mutableListOf<Release>()
            private var kind: String? = null
            private var inComment = false

            fun notes(lastLine: Int): ReleaseNotes {
                checkLastHasLines(lastLine)
                return ReleaseNotes(releases.toList())
            }

            /** A version with no lines would show a bare heading: refuse it where the next one starts, or at the end. */
            private fun checkLastHasLines(n: Int) {
                val last = releases.lastOrNull() ?: return
                if ((last.new + last.changed + last.fixed).isEmpty()) fail(n, "${last.version} has no lines")
            }

            fun read(n: Int, line: String) {
                when {
                    inComment -> inComment = !line.contains("-->")
                    line.startsWith("<!--") -> inComment = !line.contains("-->")
                    line.isBlank() || (line.startsWith("# ") && releases.isEmpty()) -> Unit
                    line.startsWith("## ") -> version(n, line)
                    line.startsWith("### ") -> kind(n, line.removePrefix("### "))
                    line.startsWith("- ") -> item(n, line.removePrefix("- ").trim())
                    else -> fail(n, "expected a heading or a \"- \" line")
                }
            }

            private fun version(n: Int, line: String) {
                val (version, date) = VERSION.matchEntire(line)?.destructured ?: fail(n, "expected \"## <version> · <yyyy-mm-dd>\"")
                checkLastHasLines(n)
                if (releases.any { it.version == version }) fail(n, "$version is there twice")
                releases += Release(version, parseDate(date, n))
                kind = null
            }

            private fun kind(n: Int, name: String) {
                if (name !in KINDS) fail(n, "expected ### New, Changed or Fixed")
                if (releases.isEmpty()) fail(n, "$name before any version")
                kind = name
            }

            private fun item(n: Int, item: String) {
                val current = kind ?: fail(n, "a line outside New, Changed or Fixed")
                releases[releases.lastIndex] = releases.last().with(current, item)
            }
        }

        private fun parseDate(text: String, line: Int): LocalDate =
            try { LocalDate.parse(text) } catch (_: DateTimeParseException) { fail(line, "\"$text\" is not a yyyy-mm-dd date") }

        private fun Release.with(kind: String, item: String) = when (kind) {
            "New" -> copy(new = new + item)
            "Changed" -> copy(changed = changed + item)
            else -> copy(fixed = fixed + item)
        }
    }
}
