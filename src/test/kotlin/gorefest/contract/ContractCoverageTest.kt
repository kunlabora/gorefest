package gorefest.contract

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The Contract (`docs/contract/`) is met only when every obligation has a test that proves it.
 * Tests name what they prove with `@Obligation("GD-3")`; this reads both from source, so the
 * proof counts only because the rest of the suite runs green in the same build.
 * It also writes the coverage per area to `build/reports/contract/coverage.md`.
 */
class ContractCoverageTest {
    private val obligations = Contract.read(File("docs/contract"))
    private val proofs = Proofs.read(File("src/test/kotlin"))

    @Test
    fun `the contract has obligations`() {
        obligations.shouldNotBeEmpty()
    }

    @Test
    fun `every obligation is proven by a test`() {
        Report.write(obligations, proofs, File("build/reports/contract/coverage.md"))
        obligations.filter { it.id !in proofs }.map { "${it.id} ${it.text}" }.shouldBeEmpty()
    }

    @Test
    fun `tests only name obligations that exist`() {
        val known = obligations.map { it.id }.toSet()
        proofs.filterKeys { it !in known }.map { (id, tests) -> "$id in ${tests.joinToString()}" }.shouldBeEmpty()
    }

    @Test
    fun `obligation ids are unique`() {
        obligations.groupBy { it.id }.filterValues { it.size > 1 }.keys.shouldBeEmpty()
    }

    @Test
    fun `ids carry their area's prefix`() {
        obligations.filter { !it.id.startsWith(it.area.prefix + "-") }.map { "${it.id} in ${it.area.file}" }.shouldBeEmpty()
    }
}

private data class Area(val file: String, val title: String, val prefix: String)

private data class Clause(val id: String, val text: String, val area: Area)

private object Contract {
    private val heading = Regex("""^# (.+)$""")
    private val prefixLine = Regex("""^Prefix: `([A-Z]+)`""")
    private val obligation = Regex("""^- \*\*([A-Z]+-\d+)\*\* (.+)$""")

    fun read(dir: File): List<Clause> =
        dir.listFiles { f -> f.extension == "md" && f.name != "README.md" }.orEmpty().sortedBy { it.name }.flatMap { file ->
            val lines = file.readLines()
            val area = Area(
                file = file.name,
                title = lines.firstNotNullOfOrNull { heading.find(it)?.groupValues?.get(1) } ?: file.nameWithoutExtension,
                prefix = lines.firstNotNullOfOrNull { prefixLine.find(it)?.groupValues?.get(1) } ?: "",
            )
            lines.mapNotNull { obligation.find(it) }.map { Clause(it.groupValues[1], it.groupValues[2], area) }
        }
}

private object Proofs {
    private val annotation = Regex("""@Obligation\(([^)]*)\)""")
    private val id = Regex(""""([A-Z]+-\d+)"""")

    /** Obligation id to the test files that prove it. */
    fun read(dir: File): Map<String, Set<String>> =
        dir.walk().filter { it.extension == "kt" && it.name != "Obligation.kt" }
            .flatMap { file ->
                annotation.findAll(file.readText()).flatMap { a -> id.findAll(a.groupValues[1]).map { it.groupValues[1] to file.name } }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }
}

private object Report {
    fun write(obligations: List<Clause>, proofs: Map<String, Set<String>>, to: File) {
        val total = obligations.size
        val proven = obligations.count { it.id in proofs }
        to.parentFile.mkdirs()
        to.writeText(buildString {
            appendLine("# Contract coverage")
            appendLine()
            appendLine("$proven of $total obligations proven.")
            appendLine()
            appendLine("| Area | Proven | Unproven |")
            appendLine("|---|---|---|")
            obligations.groupBy { it.area }.forEach { (area, clauses) ->
                val missing = clauses.filter { it.id !in proofs }.joinToString { it.id }
                appendLine("| ${area.title} | ${clauses.count { it.id in proofs }}/${clauses.size} | $missing |")
            }
        })
    }
}
