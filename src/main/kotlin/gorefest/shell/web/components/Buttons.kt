package gorefest.shell.web.components

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FlowOrPhrasingContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.span

enum class ButtonStyle(val css: String) { PRIMARY("gf-btn--primary"), SECONDARY("gf-btn--secondary"), GHOST("gf-btn--ghost") }

enum class ButtonSize(val css: String) { SMALL("gf-btn--sm"), NORMAL(""), LARGE("gf-btn--lg") }

fun buttonClasses(style: ButtonStyle, size: ButtonSize = ButtonSize.NORMAL, block: Boolean = false) =
    listOf("gf-btn", style.css, size.css, if (block) "gf-btn--block" else "").filter { it.isNotEmpty() }.joinToString(" ")

/** The busy indicator htmx shows inside a button while its request runs. */
fun FlowOrPhrasingContent.spinner() = span("gf-spin") { attributes["aria-hidden"] = "true" }

/** A link that looks like a button. */
fun FlowContent.linkButton(
    href: String, label: String, style: ButtonStyle = ButtonStyle.PRIMARY, size: ButtonSize = ButtonSize.NORMAL,
    block: Boolean = false, icon: String? = null,
) = a(href, classes = buttonClasses(style, size, block)) {
    if (icon != null) icon(icon)
    +label
}

/** The screen's main action: large, full width, a spinner while busy. [form] submits a form elsewhere on the page (the dock). */
fun FlowContent.primaryButton(label: String, form: String? = null) =
    button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.LARGE, block = true)) {
        if (form != null) attributes["form"] = form
        spinner()
        +label
    }

/** The dock's main action as a link. */
fun FlowContent.primaryLink(href: String, label: String, icon: String? = null) =
    linkButton(href, label, ButtonStyle.PRIMARY, ButtonSize.LARGE, block = true, icon = icon)
