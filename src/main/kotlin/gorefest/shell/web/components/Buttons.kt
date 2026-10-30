package gorefest.shell.web.components

import kotlinx.html.BUTTON
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.title
import kotlinx.html.FlowOrPhrasingContent
import kotlinx.html.SPAN
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.span

enum class ButtonStyle(val css: String) { PRIMARY("gf-btn--primary"), SECONDARY("gf-btn--secondary"), GHOST("gf-btn--ghost") }

enum class ButtonSize(val css: String) { SMALL("gf-btn--sm"), NORMAL(""), LARGE("gf-btn--lg") }

fun buttonClasses(style: ButtonStyle, size: ButtonSize = ButtonSize.NORMAL, block: Boolean = false) =
    listOf("gf-btn", style.css, size.css, if (block) "gf-btn--block" else "").filter { it.isNotEmpty() }.joinToString(" ")

/** The busy indicator htmx shows inside a button while its request runs; [always] shows it regardless (a notice that waits). */
fun FlowOrPhrasingContent.spinner(always: Boolean = false) = span("gf-spin") {
    attributes["aria-hidden"] = "true"
    if (always) attributes["style"] = "display: inline-block"
}

/** What a screen reader says for a control. Never blank: a control without a name can't be found or used by ear. */
@JvmInline
value class AccessibleName(val text: String) {
    init { require(text.isNotBlank()) { "A control needs a name a screen reader can say" } }
}

/** Names this control [label], or [says] when the visible label alone isn't enough (which game, which person). */
private fun BUTTON.named(label: String, says: String?) {
    AccessibleName(label)
    if (says != null) attributes["aria-label"] = AccessibleName(says).text
}

/**
 * A button that says what it does. [label] is its visible text, [says] what a screen reader says instead when the label alone
 * doesn't say what it acts on ("Take Brass off the poll" for "Take off"). [submit] submits its form; [spinner] shows htmx's
 * busy indicator; [controls] names the element it opens. [configure] adds the htmx wiring, `name`, `value` or `disabled`;
 * [labelSpan] puts the label in a span of its own, for a script that rewrites it.
 */
fun FlowContent.actionButton(
    label: String, style: ButtonStyle = ButtonStyle.SECONDARY, size: ButtonSize = ButtonSize.NORMAL, block: Boolean = false,
    submit: Boolean = false, icon: String? = null, spinner: Boolean = false, says: String? = null, controls: String? = null,
    labelSpan: (SPAN.() -> Unit)? = null, configure: BUTTON.() -> Unit = {},
) = button(type = if (submit) ButtonType.submit else ButtonType.button, classes = buttonClasses(style, size, block)) {
    named(label, says)
    if (controls != null) attributes["aria-controls"] = controls
    configure()
    if (spinner) spinner()
    if (icon != null) icon(icon)
    if (labelSpan != null) span { labelSpan(); +label } else +label
}

/** A button that looks like a link, for a quiet action inside a sentence ("Send it again"). */
fun FlowContent.textButton(label: String, submit: Boolean = false, configure: BUTTON.() -> Unit = {}) =
    button(type = if (submit) ButtonType.submit else ButtonType.button, classes = "gf-link") {
        named(label, null)
        configure()
        +label
    }

/** A button that shows only [icon]: [says] is its name for screen readers and its tooltip. */
fun FlowContent.iconButton(says: String, icon: String, style: ButtonStyle = ButtonStyle.SECONDARY, configure: BUTTON.() -> Unit = {}) =
    button(type = ButtonType.button, classes = buttonClasses(style)) {
        attributes["aria-label"] = AccessibleName(says).text
        title = says
        configure()
        icon(icon)
    }

/**
 * A chip that is on or off ([pressed]), like the heart on the bring page: a screen reader hears its name and whether it's pressed.
 * [says] names it when every row has the same chips (the game is in it, BUG-035). [outlineIcon] draws [icon] as an outline.
 */
fun FlowContent.chipToggle(
    label: String, pressed: Boolean, icon: String? = null, says: String? = null, submit: Boolean = false, outlineIcon: Boolean = false,
    configure: BUTTON.() -> Unit = {},
) = button(type = if (submit) ButtonType.submit else ButtonType.button, classes = "gf-chip gf-chip--text" + if (outlineIcon) " gf-chip--outline-icon" else "") {
    named(label, says)
    attributes["aria-pressed"] = pressed.toString()
    configure()
    if (icon != null) icon(icon)
    span { +label }
}

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
    actionButton(label, ButtonStyle.PRIMARY, ButtonSize.LARGE, block = true, submit = true, spinner = true) {
        if (form != null) attributes["form"] = form
    }

/** The dock's main action as a link. */
fun FlowContent.primaryLink(href: String, label: String, icon: String? = null) =
    linkButton(href, label, ButtonStyle.PRIMARY, ButtonSize.LARGE, block = true, icon = icon)
