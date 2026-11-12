package gorefest.tools

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * IMP's kill matrix (Rule 26): which core tests catch a mutant no other test catches.
 *
 * Reads build/reports/pitest/mutations.xml from `./gradlew mutationTest` (run with --fullMutationMatrix)
 * and prints, per test class, the tests that kill nothing on their own: candidates to merge or delete.
 * A test that proves a Contract obligation stays even when it kills nothing unique; say so in its name.
 * Usage: ./gradlew -q killMatrix --args="[mutations.xml] [--survivors]"
 */
object KillMatrix {
    private val METHOD = Regex("""\[class:([^\]]+)]/\[method:([^\]]+)\(\)]""")
    private val UNNOTICED = setOf("SURVIVED", "NO_COVERAGE")

    data class Mutation(
        val killers: Set<String>,
        val detected: Boolean,
        val status: String,
        val sourceFile: String,
        val line: Int,
        val description: String,
    )

    fun run(args: List<String>) {
        val path = args.firstOrNull { !it.startsWith("--") } ?: "build/reports/pitest/mutations.xml"
        print(report(read(File(path)), survivors = "--survivors" in args))
    }

    fun read(file: File): List<Mutation> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val children = doc.documentElement.childNodes
        return (0 until children.length).map { children.item(it) }.filterIsInstance<Element>().map { m ->
            fun text(tag: String) = m.getElementsByTagName(tag).item(0)?.textContent.orEmpty()
            Mutation(
                killers = METHOD.findAll(text("killingTests"))
                    .map { "${it.groupValues[1].substringAfterLast('.')} :: ${it.groupValues[2]}" }.toSet(),
                detected = m.getAttribute("detected") == "true",
                status = m.getAttribute("status"),
                sourceFile = text("sourceFile"),
                line = text("lineNumber").toInt(),
                description = text("description"),
            )
        }
    }

    fun report(mutations: List<Mutation>, survivors: Boolean): String = buildString {
        val kills = mutations.flatMap { it.killers }.groupingBy { it }.eachCount()
        val unique = mutations.mapNotNull { it.killers.singleOrNull() }.groupingBy { it }.eachCount()
        val redundant = kills.keys.filter { it !in unique }.sorted()
        val killed = mutations.count { it.detected }
        appendLine("$killed/${mutations.size} mutants killed by ${kills.size} tests; ${redundant.size} tests kill nothing on their own.\n")
        redundant.groupBy { it.substringBefore(" :: ") }.toSortedMap().forEach { (cls, tests) ->
            appendLine("$cls (${tests.size})")
            for (t in tests) appendLine("  - ${t.substringAfter(" :: ")}  (kills ${kills[t]}, none alone)")
        }

        if (survivors) {
            val unnoticed = mutations.filter { it.status in UNNOTICED }
                .sortedWith(compareBy({ it.sourceFile }, { it.line }, { it.description }, { it.status }))
            appendLine("\n${unnoticed.size} mutants no test notices:")
            for (m in unnoticed) {
                val coverage = if (m.status == "NO_COVERAGE") "  (no coverage)" else ""
                appendLine("  ${m.sourceFile}:${m.line}  ${m.description}$coverage")
            }
        }
    }
}
