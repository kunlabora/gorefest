package gorefest.tools

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.name
import kotlin.io.path.readLines

/** A row of a docs/milestone-*.md table, with the flow dates (docs/backlog.md, "Flow metrics"). */
data class Story(
    val id: String,
    val milestone: String,
    val status: String,
    val arrived: LocalDate?,
    val started: LocalDate?,
    val done: LocalDate?,
)

/** Reads the stories from every milestone table that has a Done column. */
object Milestones {
    private val STORY_ID = Regex("[A-Z]+\\d+")

    fun stories(docs: Path): List<Story> =
        Files.list(docs).use { files ->
            files.filter { it.name.startsWith("milestone-") && it.name.endsWith(".md") }.sorted().toList()
        }.flatMap { storiesIn(it.name.removeSuffix(".md"), it.readLines()) }

    fun storiesIn(milestone: String, lines: List<String>): List<Story> {
        val found = mutableListOf<Story>()
        var header: List<String>? = null
        for (line in lines) {
            val cells = line.trim().trim('|').split("|").map { it.trim() }
            when {
                line.startsWith("| #") -> header = cells
                header != null && "Done" in header && STORY_ID.matches(cells[0]) -> {
                    val row = header.zip(cells).toMap()
                    fun date(column: String) = row[column]?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
                    found += Story(row.getValue("#"), milestone, row.getValue("Status"), date("Arrived"), date("Started"), date("Done"))
                }
                !line.startsWith("|") -> header = null
            }
        }
        return found
    }
}
