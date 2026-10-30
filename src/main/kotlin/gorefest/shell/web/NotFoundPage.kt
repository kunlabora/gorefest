package gorefest.shell.web

import gorefest.shell.web.components.ButtonStyle
import gorefest.shell.web.components.Level
import gorefest.shell.web.components.pageHeading
import gorefest.shell.web.components.emptyState
import gorefest.shell.web.components.linkButton
import gorefest.shell.web.components.page
import gorefest.shell.web.components.wordmark
import gorefest.shell.web.components.wrap
import kotlinx.html.HTML

/**
 * Every 404 looks the same, whether the page never existed or belongs to a game day you're not in: it says nothing
 * about which, so nothing leaks. A way back to the home page, which sends signed-out people to the login.
 */
fun HTML.notFoundPage() = page("Not found", appBar = { wordmark() }, wide = true) {
    wrap {
        emptyState("That page isn't here", "The link may be old, or the game day may be gone.", Level.H1) {
            linkButton("/", "Back to your game days", ButtonStyle.SECONDARY)
        }
    }
}
