package gorefest.shell.web.components

import gorefest.core.PlayerId
import gorefest.shell.web.DEFAULT_TIME_ZONE
import java.time.LocalDate
import kotlinx.html.DIV
import kotlinx.html.FIELDSET
import kotlinx.html.FlowContent
import kotlinx.html.strong
import kotlinx.html.FormMethod
import kotlinx.html.form
import kotlinx.html.p
import kotlinx.html.INPUT
import kotlinx.html.LABEL
import kotlinx.html.TagConsumer
import kotlinx.html.role
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.p
import kotlinx.html.span

private fun control(id: String, hint: String?, error: String?) = FieldControl(id, hint?.let { "$id-hint" }, error?.let { "$id-error" })

/** Label, the control from [control], an optional hint, and the error line when the server rejected it; the hint and error describe the control. */
fun FlowContent.field(id: String, label: String, hint: String? = null, error: String? = null, control: context(FieldControl) FlowContent.() -> Unit) =
    div(if (error != null) "gf-field gf-field--invalid" else "gf-field") {
        val tied = control(id, hint, error)
        label("gf-label") { htmlFor = id; +label }
        control(tied, this)
        if (hint != null) p("gf-hint") { this.id = "$id-hint"; +hint }
        if (error != null) fieldError(error, id = "$id-error")
    }

/**
 * The sign-in layout of a field, inside the caller's form: label, the control with [action] (the button) beside it when given,
 * the error, then the hint. The caller's form carries `gf-signin` and its invalid state.
 */
fun FlowContent.signinField(
    id: String, label: String, hint: String? = null, error: String? = null, action: (FlowContent.() -> Unit)? = null,
    control: context(FieldControl) FlowContent.() -> Unit,
) {
    val tied = control(id, hint, error)
    label("gf-label") { htmlFor = id; +label }
    if (action != null) div("gf-signin__row") { control(tied, this); action() } else control(tied, this)
    if (error != null) fieldError(error, id = "$id-error")
    if (hint != null) p("gf-hint") { this.id = "$id-hint"; +hint }
}

/** A text-like input (text, email, date) for the field around it; [configure] sets its value and constraints. */
context(field: FieldControl)
fun FlowContent.fieldInput(name: String, type: InputType = InputType.text, configure: INPUT.() -> Unit = {}) =
    input(type, name = name, classes = "gf-input") { with(field) { tie() }; configure() }

fun FlowContent.hiddenInput(name: String, value: String) = input(InputType.hidden, name = name) { this.value = value }

/**
 * The 6-digit code field. A pasted code often brings a space or a dash along: the browser keeps only the digits, rather
 * than a `maxlength` cutting the last digit off first. The server ignores anything but digits as well.
 */
context(field: FieldControl)
fun FlowContent.codeInput() = input(InputType.text, name = "code", classes = "gf-input gf-code") {
    with(field) { tie() }
    required = true
    placeholder = "······"
    attributes["inputmode"] = "numeric"
    attributes["autocomplete"] = "one-time-code"
    hyperscript("on input js(me) me.value = me.value.replace(/\\D/g, '').slice(0, 6) end")
}

/**
 * One of a list of like inputs under a heading (the poll's dates): it has no label of its own, so it is named [label],
 * and the list around it is a [labelledGroup] that carries the question.
 */
fun TagConsumer<*>.listedInput(name: String, label: String, type: InputType = InputType.text, configure: INPUT.() -> Unit = {}) =
    input(type, name = name, classes = "gf-input") { attributes["aria-label"] = label; configure() }

/** A group of controls named by the heading with id [headingId], so a screen reader announces the question with them. */
fun FlowContent.labelledGroup(id: String, headingId: String, classes: String = "gf-stack", content: DIV.() -> Unit) = div(classes) {
    this.id = id
    role = "group"
    attributes["aria-labelledby"] = headingId
    content()
}

/** An on/off switch: named [label] (the visible text around it may be longer), sending [name]=true when on. */
private fun FlowContent.switchInput(name: String, label: String, checked: Boolean, disabled: Boolean, describedBy: String?, configure: INPUT.() -> Unit) =
    input(InputType.checkBox, classes = "gf-switch", name = name) {
        role = "switch"
        value = "true"
        this.checked = checked
        this.disabled = disabled
        attributes["aria-label"] = label
        if (describedBy != null) attributes["aria-describedby"] = describedBy
        configure()
    }

/** A settings row: [title] and the line under it ([sub], which describes the switch), with the switch at the end. */
fun FlowContent.switchRow(name: String, title: String, sub: String, checked: Boolean, classes: String = "", configure: INPUT.() -> Unit = {}) =
    label("gf-row $classes".trim()) {
        span("gf-row__main") {
            span("gf-row__title") { +title }
            span("gf-row__sub") { id = "$name-sub"; +sub }
        }
        switchInput(name, title, checked, disabled = false, describedBy = "$name-sub", configure)
    }

/** A tile with a switch first and [content] after it (the bring page's games): the whole tile toggles it. */
fun FlowContent.switchTile(
    name: String, label: String, checked: Boolean, classes: String, disabled: Boolean = false, configure: INPUT.() -> Unit = {}, content: LABEL.() -> Unit,
) = label(classes) {
    switchInput(name, label, checked, disabled, describedBy = null, configure)
    content()
}

/** Players as checkbox chips (avatar, tick when checked, name), submitted as [name]. */
fun FlowContent.playerChips(
    players: List<PlayerId>, names: Map<PlayerId, String>, checked: (PlayerId) -> Boolean, name: String = "players", legend: String = "Players",
) = chipGroup(legend) {
    players.forEach { player ->
        label("gf-chip") {
            input(InputType.checkBox, name = name) { value = player.toString(); this.checked = checked(player) }
            avatar(player, names[player], size = "gf-avatar--sm")
            span("gf-chip__check") { attributes["aria-hidden"] = "true"; icon("check") }
            +(names[player] ?: SOMEONE)
        }
    }
}

/** The fieldset every group of chips sits in: no border, a visually hidden [legend] for screen readers. */
private fun FlowContent.chipGroup(legend: String, chips: FIELDSET.() -> Unit) = fieldSet("gf-chips") {
    attributes["style"] = "border: 0; padding: 0; margin: 0"
    legend("gf-vh") { +legend }
    chips()
}

/** Safe in an id and in a CSS selector: "3h+" becomes "3h-plus". */
fun String.idSlug(): String = lowercase().replace("+", "-plus").replace(Regex("[^a-z0-9]+"), "-").trim('-')

/** A segmented control: radios named [name] with [options] as labels and values. With [idPrefix], each gets id `prefix-option`. */
fun FlowContent.segmented(name: String, legend: String, options: List<String>, checked: String, idPrefix: String? = null) = fieldSet("gf-seg") {
    attributes["style"] = "border: 1px solid var(--line); margin: 0"
    legend("gf-vh") { +legend }
    options.forEach { option ->
        label {
            input(InputType.radio, name = name) {
                if (idPrefix != null) id = "$idPrefix-${option.idSlug()}"
                value = option
                this.checked = option == checked
            }
            +option
        }
    }
}

/**
 * A search box with its magnifier: as you type it asks [url] and puts the answer at [target]. [extra] adds attributes to the input.
 * While the request is in flight (the slow path, where BGG is asked) a small spinner shows at the end of the field.
 */
fun FlowContent.searchField(placeholder: String, label: String, url: String, target: String, extra: INPUT.() -> Unit = {}) = div("gf-search") {
    icon("search")
    span("gf-spin gf-search__spin htmx-indicator") { attributes["aria-hidden"] = "true" }
    input(InputType.search, classes = "gf-input", name = "q") {
        this.placeholder = placeholder
        attributes["aria-label"] = label
        attributes["autocomplete"] = "off"
        hx("get", url)
        hx("trigger", "input changed delay:300ms, search")
        hx("target", target)
        hx("swap", "outerHTML")
        hx("indicator", "closest .gf-search")
        extra()
    }
}

/** The browser's time zone, sent along with a form that creates something dated (the server falls back to its default). */
fun FlowContent.browserTimeZone() = input(InputType.hidden, name = "timeZone") {
    value = DEFAULT_TIME_ZONE
    hyperscript("init set my value to Intl.DateTimeFormat().resolvedOptions().timeZone")
}

/**
 * Dates as checkbox chips, submitted as [name] in ISO form. A [disabled] date can't be changed (already past);
 * one that was ticked is still sent along, because a disabled checkbox wouldn't be and the answer would lose it.
 */
fun FlowContent.dateChips(
    dates: List<LocalDate>, checked: (LocalDate) -> Boolean, disabled: (LocalDate) -> Boolean = { false }, name: String = "dates", legend: String = "Dates",
) = chipGroup(legend) {
    dates.forEach { date ->
        label("gf-chip gf-chip--text") {
            input(InputType.checkBox, name = name) { value = date.toString(); this.checked = checked(date); this.disabled = disabled(date) }
            +date.short()
        }
        if (disabled(date) && checked(date)) hiddenInput(name, date.toString())
    }
}

/** The button that joins through an invite, and what to tell someone who isn't logged in yet. */
fun FlowContent.joinForm(path: String, label: String, loggedIn: Boolean) {
    form(action = "$path/join", method = FormMethod.post) { primaryButton(label) }
    if (!loggedIn) p("gf-hint") { attributes["style"] = "text-align: center"; +"Just your email. No password, no app to install." }
}

/** Who is inviting you, with their avatar: the first line of an invite landing. */
fun FlowContent.inviterLine(host: PlayerId, hostName: String?, text: String) = div("gf-media") {
    attributes["style"] = "padding-top: var(--space-3)"
    avatar(host, hostName, size = "gf-avatar--lg")
    p("gf-muted") {
        attributes["style"] = "margin: 0"
        strong { attributes["style"] = "color: var(--ink)"; +(hostName ?: SOMEONE) }
        +" $text"
    }
}
