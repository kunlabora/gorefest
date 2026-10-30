package gorefest.shell.web.components

import gorefest.contract.Obligation
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.pow

/**
 * Reads the design system's CSS (`docs/design/css`, served as `/static/css`) and checks what a person sees: contrast in
 * both themes, a visible focus ring, calm motion on request and targets big enough to hit. No browser needed.
 */
class ColourAndMotionTest {
    private val tokens = Tokens(File("docs/design/css/tokens.css").readText())
    private val css = Stylesheet(File("docs/design/css/gorefest.css").readText())

    @Test
    @Obligation("A11Y-11")
    fun `text has 4·5 to 1 on the surface it sits on, in both themes`() {
        val failures = THEMES.flatMap { theme ->
            css.coloured().flatMap { rule ->
                val needed = if (rule.isGraphic) GRAPHIC else TEXT
                css.backgroundsBehind(rule).mapNotNull { background ->
                    val ratio = tokens.contrast(theme, checkNotNull(rule.colour), background)
                    "$theme: ${rule.selector} (${rule.colour} on $background) is ${ratio.fmt()}".takeIf { ratio < needed }
                }
            }.filterNot { failure -> DECORATIVE.any { failure.contains(": $it (") } }
        }
        failures.shouldBeEmpty()
    }

    @Test
    @Obligation("A11Y-11")
    fun `the text tokens read on every surface and soft fill, and every fill on its own ink`() {
        val failures = THEMES.flatMap { theme ->
            TEXT_TOKENS.flatMap { ink -> (SURFACES + SOFT_FILLS).map { Triple(ink, it, tokens.contrast(theme, ink, it)) } } +
                FILLS.map { (ink, fill) -> Triple(ink, fill, tokens.contrast(theme, ink, fill)) }
        }.filter { (_, _, ratio) -> ratio < TEXT }
        failures.shouldBeEmpty()
    }

    @Test
    @Obligation("A11Y-11")
    fun `a device in dark mode gets the same colours as the dark toggle`() {
        tokens.osDark shouldBe tokens.forcedDark
    }

    @Test
    @Obligation("A11Y-12")
    fun `control borders and the focus ring have 3 to 1 on every surface, in both themes`() {
        val failures = THEMES.flatMap { theme ->
            listOf("line-strong", "focus").flatMap { line ->
                (SURFACES + SOFT_FILLS).mapNotNull { surface ->
                    val ratio = tokens.contrast(theme, line, surface)
                    "$theme: $line on $surface is ${ratio.fmt()}".takeIf { ratio < GRAPHIC }
                }
            }
        }
        failures.shouldBeEmpty()
        css.rules.filter { "border" in it.body && "var(--line-strong)" in it.body }.map { it.selector }
            .shouldContainAll(".gf-input", ".gf-chip", ".gf-btn--secondary", ".gf-answer", ".gf-switch")
    }

    @Test
    @Obligation("A11Y-13")
    fun `keyboard focus always shows a ring, also on inputs hidden inside a tile, chip or segment`() {
        css.rules.single { it.selector == ":focus-visible" }.body shouldBe "outline: 3px solid var(--focus); outline-offset: 2px;"
        css.rules.filter { OUTLINE_OFF.containsMatchIn(it.body) }.map { it.selector }.shouldBeEmpty()
        css.rules.filter { "outline:" in it.body }.filterNot { "var(--focus" in it.body }.map { it.selector }.shouldBeEmpty()

        val hidden = css.rules.filter { it.selector.endsWith(" input") && "opacity: 0" in it.body }
        hidden.shouldNotBeEmpty()
        hidden.forEach { rule ->
            val holder = rule.selector.removeSuffix(" input").replace(".gf-seg", ".gf-seg label")
            withClue(rule.selector) {
                css.rules.filter { it.selector == "$holder:has(input:focus-visible)" && "var(--focus)" in it.body }.shouldNotBeEmpty()
            }
        }
    }

    @Test
    @Obligation("A11Y-13")
    fun `everything that moves is calmed when the device asks for less motion`() {
        val calm = css.reducedMotion.map { it.selector }.toSet()
        val moving = css.rules.filter { MOTION.containsMatchIn(it.body) && it !in css.reducedMotion }.map { it.selector }
        moving.shouldNotBeEmpty()
        moving.filterNot { it in calm }.shouldBeEmpty()
    }

    @Test
    @Obligation("A11Y-14")
    fun `buttons, chips, answers, segments and icon buttons are at least 24px both ways`() {
        val failures = TARGETS.flatMap { selector ->
            val sizes = css.everywhere.filter { it.selector == selector }.flatMap { rule ->
                SIZE.findAll(rule.body).map { it.groupValues[1] to it.groupValues[2].toInt() }
            }
            withClue(selector) { sizes.shouldNotBeEmpty() }
            sizes.filter { (_, px) -> px < MIN_TARGET }.map { (property, px) -> "$selector $property: ${px}px" }
        }
        failures.shouldBeEmpty()
        css.rules.single { it.selector == ".gf-iconbtn" }.body.let { SIZE.findAll(it).map { m -> m.groupValues[1] }.toSet() } shouldBe
            setOf("width", "height")
    }

    private fun Double.fmt() = "%.2f:1".format(this)

    private companion object {
        const val TEXT = 4.5
        const val GRAPHIC = 3.0
        const val MIN_TARGET = 24
        val THEMES = listOf(Theme.LIGHT, Theme.DARK)
        val SURFACES = listOf("surface", "surface-raised", "surface-sunk")
        val SOFT_FILLS = listOf("brand-soft", "accent-soft", "olive-soft", "danger-soft")
        val TEXT_TOKENS = listOf("ink", "ink-muted", "brand-ink", "danger")
        val FILLS = listOf("on-brand" to "brand", "on-accent" to "accent", "on-olive" to "olive", "surface" to "ink")

        /** An icon beside its own words (aria-hidden), which WCAG 1.4.11 doesn't ask contrast of. */
        val DECORATIVE = listOf(".gf-answer--great .gf-icon")
        val TARGETS = listOf(".gf-btn", ".gf-btn--sm", ".gf-btn--lg", ".gf-chip", ".gf-iconbtn", ".gf-answer", ".gf-seg label")
        val SIZE = Regex("""(?<![\w-])(?:min-)?(height|width):\s*(\d+)px""")
        val MOTION = Regex("""(?<![\w-])(?:transition|animation):\s*(?!none)""")
        val OUTLINE_OFF = Regex("""outline:\s*(?:none|0)\b""")
    }
}

private enum class Theme { LIGHT, DARK }

/** The colour tokens per theme, from `tokens.css`, with `var()` chains followed to a hex colour. */
private class Tokens(source: String) {
    private val base = declarations(block(source, Regex("""^:root \{""", RegexOption.MULTILINE)))
    private val light = base + declarations(block(source, Regex("""^:root, \[data-theme="light"] \{""", RegexOption.MULTILINE)))
    val osDark = declarations(block(source, Regex(""":root:not\(\[data-theme="light"]\) \{""")))
    val forcedDark = declarations(block(source, Regex("""^\[data-theme="dark"] \{""", RegexOption.MULTILINE)))
    private val dark = light + forcedDark

    fun contrast(theme: Theme, a: String, b: String): Double {
        val (high, low) = listOf(luminance(hex(theme, a)), luminance(hex(theme, b))).sortedDescending()
        return (high + 0.05) / (low + 0.05)
    }

    private fun hex(theme: Theme, token: String): String {
        val value = requireNotNull((if (theme == Theme.LIGHT) light else dark)[token]) { "--$token isn't defined in the $theme theme" }
        return VAR.matchEntire(value)?.let { hex(theme, it.groupValues[1]) }
            ?: value.also { require(HEX.matches(it)) { "--$token is $it, not a colour" } }
    }

    private fun luminance(hex: String): Double {
        val (r, g, b) = (1..5 step 2).map { hex.substring(it, it + 2).toInt(16) / 255.0 }
            .map { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun block(source: String, opening: Regex): String {
        val start = requireNotNull(opening.find(source)) { "no block $opening in tokens.css" }.range.last + 1
        return source.substring(start, source.indexOf('}', start))
    }

    private fun declarations(block: String) =
        Regex("""--([\w-]+):\s*([^;]+);""").findAll(block).associate { it.groupValues[1] to it.groupValues[2].trim() }

    companion object {
        val VAR = Regex("""var\(--([\w-]+)\)""")
        val HEX = Regex("""#[0-9a-fA-F]{6}""")
    }
}

/** One selector of a rule (a list `a, b { … }` becomes two) and its declarations. */
private data class Rule(val selector: String, val body: String) {
    val colour = COLOUR.find(body)?.groupValues?.get(1)
    val background = BACKGROUND.find(body)?.groupValues?.get(1)
    val isGraphic = selector.substringAfterLast(' ').let { "gf-icon" in it || "gf-spin" in it }

    companion object {
        val COLOUR = Regex("""(?<![\w-])color:\s*var\(--([\w-]+)\)""")
        val BACKGROUND = Regex("""(?<![\w-])background(?:-color)?:\s*var\(--([\w-]+)\)""")
    }
}

/** `gorefest.css` as rules: top-level ones, the reduced-motion block's, and everything including other `@media` blocks. */
private class Stylesheet(source: String) {
    private val text = source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
    private val reducedMotionBlock = mediaBlock("@media (prefers-reduced-motion: reduce)")
    val everywhere = rules(text)
    val reducedMotion = rules(reducedMotionBlock)
    val rules = rules(topLevel())

    fun coloured() = rules.filter { it.colour != null }

    /** The rule's own background, else the nearest one set on the element or an ancestor in its selector, else the page's surfaces. */
    fun backgroundsBehind(rule: Rule): List<String> {
        var selector = rule.selector
        while (true) {
            ownBackground(selector)?.let { return listOf(it) }
            val shorter = selector.replace(PSEUDO_ELEMENT, "").replace(PSEUDO_CLASS, "")
                .takeIf { it != selector } ?: selector.replace(LAST_COMPOUND, "")
            if (shorter.isBlank() || shorter == selector) return SURFACES
            selector = shorter
        }
    }

    private fun ownBackground(selector: String) = rules.lastOrNull { it.selector == selector && it.background != null }?.background

    private fun topLevel(): String {
        val out = StringBuilder()
        var depth = 0
        var index = 0
        while (index < text.length) {
            val atRule = depth == 0 && text.startsWith("@", index)
            if (atRule) {
                index = closing(text.indexOf('{', index)) + 1
                continue
            }
            when (text[index]) {
                '{' -> depth++
                '}' -> depth--
            }
            out.append(text[index++])
        }
        return out.toString()
    }

    private fun mediaBlock(header: String): String {
        val open = text.indexOf("$header {").also { require(it >= 0) { "no $header block" } } + header.length + 1
        return text.substring(open + 1, closing(open))
    }

    private fun closing(open: Int): Int {
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return index
            }
        }
        error("unclosed block at $open")
    }

    private fun rules(source: String) = RULE.findAll(source)
        .filterNot { it.groupValues[1].trim().startsWith("@") || it.groupValues[1].trim().matches(KEYFRAME) }
        .flatMap { match ->
            match.groupValues[1].split(',').map { Rule(it.trim().replace(Regex("""\s+"""), " "), match.groupValues[2].trim()) }
        }.filter { it.selector.isNotEmpty() }.toList()

    private companion object {
        val SURFACES = listOf("surface", "surface-raised", "surface-sunk")
        val RULE = Regex("""([^{}]+)\{([^{}]*)}""")
        val KEYFRAME = Regex("""from|to|\d+%""")
        val PSEUDO_ELEMENT = Regex("""::[\w-]+$""")
        val PSEUDO_CLASS = Regex(""":[\w-]+(\([^)]*\))?$""")
        val LAST_COMPOUND = Regex("""\s*[>+~]?\s*[^\s>+~]+$""")
    }
}
