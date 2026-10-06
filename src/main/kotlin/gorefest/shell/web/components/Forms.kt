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
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.p
import kotlinx.html.span

/** Label, the input from [input], an optional hint, and the error line when the server rejected it. */
fun FlowContent.field(id: String, label: String, hint: String? = null, error: String? = null, input: DIV.() -> Unit) =
    div(if (error != null) "gf-field gf-field--invalid" else "gf-field") {
        label("gf-label") { htmlFor = id; +label }
        input()
        if (hint != null) p("gf-hint") { +hint }
        if (error != null) fieldError(error)
    }

fun FlowContent.hiddenInput(name: String, value: String) = input(InputType.hidden, name = name) { this.value = value }

/** Players as checkbox chips (avatar, tick when checked, name), submitted as [name]. */
fun FlowContent.playerChips(
    players: List<PlayerId>, names: Map<PlayerId, String>, checked: (PlayerId) -> Boolean, name: String = "players", legend: String = "Players",
) = chipGroup(legend) {
    players.forEach { player ->
        label("gf-chip") {
            input(InputType.checkBox, name = name) { value = player.toString(); this.checked = checked(player) }
            avatar(player, names[player], size = "gf-avatar--sm")
            span("gf-chip__check") { attributes["aria-hidden"] = "true"; icon("check") }
            +(names[player] ?: "Unknown player")
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
        strong { attributes["style"] = "color: var(--ink)"; +(hostName ?: "Someone") }
        +" $text"
    }
}
