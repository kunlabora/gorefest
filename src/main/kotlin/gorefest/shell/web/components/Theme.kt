package gorefest.shell.web.components

import io.ktor.server.request.ApplicationRequest
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.h2

/** The appearance a player picked. SYSTEM follows the OS; the others force a theme. */
enum class Theme(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark");

    val value get() = name.lowercase()

    companion object {
        const val COOKIE = "gf_theme"

        /** From the preference cookie that `static/js/theme.js` writes. */
        fun of(request: ApplicationRequest): Theme = entries.firstOrNull { it.value == request.cookies[COOKIE] } ?: SYSTEM
    }
}

/**
 * System · Light · Dark. Changing it takes effect at once and is remembered in a cookie,
 * all in the browser (`gorefestSetTheme` in `theme.js`); the server only reads it to tick the right option.
 */
fun FlowContent.themePicker(current: Theme) = div("gf-section") {
    h2("gf-h2") { +"Appearance" }
    div {
        hyperscript("on change call gorefestSetTheme(event.target.value)")
        segmented("theme", "Appearance", Theme.entries.map { it.value }, checked = current.value, idPrefix = "theme") { value ->
            Theme.entries.first { it.value == value }.label
        }
    }
}
