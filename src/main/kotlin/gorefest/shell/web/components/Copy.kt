package gorefest.shell.web.components

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.span

/** A button that copies [text] to the clipboard and says "Copied". */
fun FlowContent.copyButton(text: String, label: String = "Copy", style: ButtonStyle = ButtonStyle.SECONDARY, ariaLabel: String? = null) =
    button(type = ButtonType.button, classes = buttonClasses(style, ButtonSize.SMALL)) {
        attributes["data-copy"] = text
        if (ariaLabel != null) attributes["aria-label"] = ariaLabel
        hyperscript("on click call navigator.clipboard.writeText(@data-copy) then put 'Copied' into me")
        +label
    }

/** The dashed box with a value to copy: an invite link ([small]) or a short code. */
fun FlowContent.copyBox(value: String, small: Boolean = false) = div("gf-copy") {
    span(if (small) "gf-copy__value gf-copy__value--small" else "gf-copy__value") { +value }
    copyButton(value)
}
