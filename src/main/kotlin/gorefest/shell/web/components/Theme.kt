package gorefest.shell.web.components

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.span

/**
 * The light/dark switch at the end of every app bar. It shows the theme you'd switch to (a moon
 * while light, a sun while dark; CSS picks which). The switching and remembering happen in the
 * browser (`gorefestToggleTheme` in `static/js/theme.js`), in a `gf_theme` cookie.
 */
fun FlowContent.themeToggle() = button(type = ButtonType.button, classes = "gf-iconbtn gf-theme-toggle") {
    attributes["aria-label"] = "Dark mode"
    attributes["aria-pressed"] = "false"
    hyperscript("on click call gorefestToggleTheme()")
    span("gf-theme-toggle__moon") { icon("moon") }
    span("gf-theme-toggle__sun") { icon("sun") }
}
