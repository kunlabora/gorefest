package gorefest.shell.web.releasenotes

import gorefest.shell.web.components.Level
import gorefest.shell.web.components.crumb
import gorefest.shell.web.components.heading
import gorefest.shell.web.components.long
import gorefest.shell.web.components.page
import gorefest.shell.web.components.pageHeading
import gorefest.shell.web.components.wordmark
import gorefest.shell.web.components.wrap
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.time
import kotlinx.html.ul

/** What's new: every release, newest first, with its new, changed and fixed lines (U24). */
fun HTML.releaseNotesPage(notes: ReleaseNotes) = page("What's new", appBar = { wordmark() }, wide = true, mainClasses = "gf-release-notes") {
    wrap {
        crumb("/", "Back")
        pageHeading("What's new", classes = "gf-h1")
        p("gf-lede") { +"What changed in Boardfest, newest first." }
        notes.releases.forEach { release(it) }
    }
}

private fun FlowContent.release(release: Release) = section("gf-release") {
    id = "v${release.version}"
    heading(Level.H2, "gf-h2") {
        +release.version
        +" "
        span("gf-muted gf-release__date") { time { attributes["datetime"] = release.date.toString(); +release.date.long() } }
    }
    kind("New", release.new)
    kind("Changed", release.changed)
    kind("Fixed", release.fixed)
}

private fun FlowContent.kind(name: String, lines: List<String>) {
    if (lines.isEmpty()) return
    heading(Level.H3, name, "gf-kicker")
    ul { lines.forEach { li { +it } } }
}
