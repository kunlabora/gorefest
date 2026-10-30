package gorefest.shell.web.components

import kotlinx.html.CommonAttributeGroupFacade
import kotlinx.html.FlowContent
import kotlinx.html.id
import kotlinx.html.p

/** The id of the page's status line, where an htmx swap's [announce] sentence is read out. */
const val ANNOUNCER = "gf-announce"

/**
 * What a screen reader hears when htmx swaps this element in: a short sentence ("3 games found", "Bringing Brass").
 * The region itself is replaced, so it can't be a live region; the page's status line reads the sentence out instead.
 */
fun CommonAttributeGroupFacade.announce(message: String) {
    attributes["data-announce"] = AccessibleName(message).text
}

/**
 * Marks a region that reloads in the background after a change made elsewhere on the page (the bring page's summary):
 * the change itself was announced where it was made, so this one stays quiet rather than talk over it.
 */
fun CommonAttributeGroupFacade.refreshesQuietly() {
    attributes["data-quiet"] = "true"
}

/** What a search's results say when they arrive: how many were found, or that nothing was. */
fun searchAnnouncement(found: Int, nothingFor: String?): String = when {
    found > 0 -> "${found.of("game")} found"
    nothingFor != null -> "Nothing found for \u201c$nothingFor\u201d"
    else -> "Search cleared"
}

/** An empty line that a script fills with a short message (a limit reached); a screen reader reads what appears. */
fun FlowContent.liveLine(id: String, classes: String) = p(classes) {
    this.id = id
    attributes["aria-live"] = "polite"
}
