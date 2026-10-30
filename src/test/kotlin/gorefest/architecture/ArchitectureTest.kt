package gorefest.architecture

import com.lemonappdev.konsist.api.Konsist
import gorefest.contract.Obligation
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

    @Test
    @Obligation("A11Y-1", "A11Y-2", "A11Y-3")
    fun `views render form controls only through the components that label them`() {
        val components = shell.filter { it.pkg == "gorefest.shell.web.components" }
        components.filter { RAW_FORM_TAG.containsMatchIn(it.text) }.shouldNotBeEmpty()
        views().flatMap { file ->
            RAW_FORM_TAG.findAll(file.code).map { "${file.name}: ${it.value.trim()}. Use field, signinField, switchRow, switchTile, gameTiles or a chip group from web.components" }
        }.shouldBeEmpty()
    }

    @Test
    @Obligation("A11Y-4", "A11Y-5")
    fun `views render buttons and images only through the components that name them`() {
        val components = shell.filter { it.pkg == "gorefest.shell.web.components" }
        components.filter { RAW_NAMED_TAG.containsMatchIn(it.text) }.shouldNotBeEmpty()
        views().flatMap { file ->
            RAW_NAMED_TAG.findAll(file.code).map { "${file.name}: ${it.value.trim()}. Use actionButton, textButton, iconButton, chipToggle or image from web.components" }
        }.shouldBeEmpty()
    }

    @Test
    @Obligation("A11Y-4", "A11Y-5", "A11Y-6")
    fun `views leave ARIA to the components`() {
        views().flatMap { file ->
            RAW_ARIA.findAll(file.code).map { "${file.name}: ${it.value.trim()}. Pass says, controls or pressed to a component, or use decoration or a live region" }
        }.shouldBeEmpty()
    }

    /** The file's text without comments, so a KDoc that mentions `button(` isn't a call. */
    private val KoFileDeclaration.code get() = COMMENT.replace(text, "")

    @Test
    @Obligation("A11Y-7", "A11Y-8")
    fun `views render headings only through the components that take a level`() {
        views().flatMap { file ->
            RAW_HEADING.findAll(file.code).map { "${file.name}: ${it.value.trim()}. Use pageHeading, sectionHeading or heading(Level.Hn, …) from web.components" }
        }.shouldBeEmpty()
    }

    private fun views() = shell.filter { it.pkg.startsWith("gorefest.shell.web") && it.pkg != "gorefest.shell.web.components" }

    private fun com.lemonappdev.konsist.api.provider.KoParentProvider.isDecider() = parents().any { it.name.substringBefore("<").substringAfterLast(".") == "Decider" }
    private fun com.lemonappdev.konsist.api.provider.KoParentProvider.isProjection() = parents().any { it.name.substringBefore("<").substringAfterLast(".") == "Projection" }
    private fun com.lemonappdev.konsist.api.provider.KoContainingFileProvider.pkg() = containingFile.packagee?.name.orEmpty()

    private companion object {
        /**
         * kotlinx.html's own form tags and typed input builders, called directly (with or without a receiver, with or without
         * arguments) rather than through a component. `fieldInput`, `codeInput` and `hiddenInput` don't match.
         */
        val RAW_FORM_TAG = Regex(
            """(?<!\w)(input|select|textarea|textArea|label|fieldSet|""" +
                """(text|checkBox|radio|email|date|dateTime|dateTimeLocal|time|month|week|search|number|range|password|tel|url|color|file|submit|reset|button|image)Input)""" +
                """\s*[({]""",
        )

        /** kotlinx.html's button and image tags, which leave the name or alt to whoever remembers it. */
        val RAW_NAMED_TAG = Regex("""(?<!\w)(button|img)\s*[({]""")

        /** kotlinx.html's h1 to h6, which leave the level to whoever writes the view. */
        val RAW_HEADING = Regex("""(?<![\w.])h[1-6]\s*[({]""")

        val COMMENT = Regex("""(?s)/\*.*?\*/|//[^\n]*""")

        /** ARIA written by hand: `aria-*` and `role` attributes. */
        val RAW_ARIA = Regex("""attributes\["(aria-[a-z]+|role)"]|\brole\s*=""")
    }
}
