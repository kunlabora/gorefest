package gorefest.shell.web.components

import kotlinx.html.FlowContent
import kotlinx.html.HtmlBlockInlineTag
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.h4

/**
 * A heading's level in the page's outline. A page has one [H1] (its [pageHeading]); what sits under it is [H2], and under
 * that [H3]. A component that renders a heading takes its level from the page, so a heading list never skips a step.
 */
enum class Level {
    H1, H2, H3, H4;

    /** The level for headings inside a section with this heading. */
    val below: Level get() = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]
}

/** A heading at [level], styled by [classes] (the look is independent of the level). */
fun FlowContent.heading(level: Level, classes: String? = null, content: HtmlBlockInlineTag.() -> Unit) = when (level) {
    Level.H1 -> h1(classes) { content() }
    Level.H2 -> h2(classes) { content() }
    Level.H3 -> h3(classes) { content() }
    Level.H4 -> h4(classes) { content() }
}

/** A heading at [level] that reads [text]. */
fun FlowContent.heading(level: Level, text: String, classes: String? = null) = heading(level, classes) { +text }

/** The page's one main heading: the landing style unless [classes] says otherwise. */
fun FlowContent.pageHeading(text: String, classes: String = "gf-h1 gf-h1--landing") = heading(Level.H1, text, classes)

/** A section's heading, under the page heading. */
fun FlowContent.sectionHeading(text: String, classes: String = "gf-h2") = heading(Level.H2, text, classes)
