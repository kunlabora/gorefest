package gorefest.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test

/**
 * CLAUDE.md's conventions that a machine can check (IMP Rule 19: standards are enforced by machines).
 * A rule that moves here can leave CLAUDE.md; a failure names the file and what to do instead.
 */
class ArchitectureTest {
    private val production = Konsist.scopeFromProduction()
    private val files = production.files

    private val KoFileDeclaration.pkg get() = packagee?.name.orEmpty()
    private val core = files.filter { it.pkg.startsWith("gorefest.core") }
    private val shell = files.filter { it.pkg.startsWith("gorefest.shell") }

    @Test
    fun `core imports only core, the Kotlin library, serialization and java time values`() {
        core.shouldNotBeEmpty()
        val allowed = listOf("gorefest.core.", "kotlin.", "kotlinx.serialization.", "java.time.")
        core.flatMap { file ->
            file.imports.map { it.name }
                .filter { name -> allowed.none { name.startsWith(it) } || name == "java.time.Clock" }
                .map { "${file.name}: $it" }
        }.shouldBeEmpty()
    }

    @Test
    fun `core has no clock, randomness or IO, even fully qualified`() {
        val forbidden = Regex("""\bjava\.(io|net|nio|sql)\.|\bjava\.time\.Clock\b|\bClock\b|\.now\(|\bRandom\b|Uuid\.random|\bgorefest\.shell\b|\bprintln\(""")
        core.flatMap { file -> forbidden.findAll(file.text).map { "${file.name}: ${it.value}" } }.shouldBeEmpty()
    }

    @Test
    fun `ids come from IdGenerator and time from the injected clock`() {
        val rules = mapOf(
            Regex("""Uuid\.random\(|UUID\.randomUUID\(""") to setOf("IdGenerator"),
            Regex("""\b(Instant|LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime)\.now\(\s*\)|System\.currentTimeMillis\(""") to emptySet(),
            Regex("""Clock\.system(UTC|DefaultZone)?\(""") to setOf("Main"),
            Regex("""\bSecureRandom\b""") to setOf("Secrets"),
        )
        rules.flatMap { (pattern, allowedIn) ->
            files.filter { it.name !in allowedIn }.flatMap { file -> pattern.findAll(file.text).map { "${file.name}: ${it.value}" } }
        }.shouldBeEmpty()
    }

    @Test
    fun `deciders are objects`() {
        production.objects().filter { it.isDecider() }.shouldNotBeEmpty()
        (production.classes().filter { it.isDecider() }.map { it.name } +
            production.interfaces().filter { it.isDecider() }.map { it.name }).shouldBeEmpty()
    }

    @Test
    fun `deciders and projections give their when over events an else branch`() {
        val folds = production.objects().filter { it.isDecider() || it.isProjection() }
        folds.shouldNotBeEmpty()
        folds.mapNotNull { fold ->
            val evolve = fold.functions().firstOrNull { it.name == "evolve" } ?: return@mapNotNull "${fold.name}: no evolve"
            val text = evolve.text
            if ("when" in text && !Regex("""\belse\s*->""").containsMatchIn(text)) fold.name else null
        }.shouldBeEmpty()
    }

    @Test
    fun `every event has a stored type name and tags`() {
        val events = production.classesAndObjects().filter { decl -> decl.pkg() == "gorefest.core" && decl.parents().any { it.name == "Event" } }
        events.shouldNotBeEmpty()
        events.filter { !it.hasAnnotationWithName("SerialName") }.map { "${it.name}: no @SerialName" }.shouldBeEmpty()
        events.filter { it.properties().none { p -> p.name == "tags" } && "tags" !in it.text }.map { "${it.name}: no tags" }.shouldBeEmpty()
    }

    @Test
    fun `shell services take what they use, not App`() {
        shell.filter { !it.pkg.startsWith("gorefest.shell.web") && it.name != "App" }
            .flatMap { file ->
                file.classes().filter { c -> c.primaryConstructor?.parameters?.any { it.type.name == "App" } == true }.map { "${file.name}: ${it.name}" }
            }.shouldBeEmpty()
    }

    @Test
    fun `views render, they don't route, read the database or run commands`() {
        val forbidden = listOf("io.ktor.server.routing", "io.ktor.server.application", "gorefest.shell.App", "gorefest.shell.db", "gorefest.shell.eventstore")
        files.filter { it.pkg.startsWith("gorefest.shell.web.") && it.name.endsWith("Views") }
            .flatMap { file -> file.imports.map { it.name }.filter { name -> forbidden.any { name.startsWith(it) } }.map { "${file.name}: $it" } }
            .shouldBeEmpty()
    }

    @Test
    fun `nothing outside App reads a projector's state`() {
        files.filter { it.name != "App" && it.name != "Projector" && Regex("""\bprojector\w*\.state\b""", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
            .map { it.name }.shouldBeEmpty()
    }

    private fun com.lemonappdev.konsist.api.provider.KoParentProvider.isDecider() = parents().any { it.name.substringBefore("<").substringAfterLast(".") == "Decider" }
    private fun com.lemonappdev.konsist.api.provider.KoParentProvider.isProjection() = parents().any { it.name.substringBefore("<").substringAfterLast(".") == "Projection" }
    private fun com.lemonappdev.konsist.api.provider.KoContainingFileProvider.pkg() = containingFile.packagee?.name.orEmpty()
}
