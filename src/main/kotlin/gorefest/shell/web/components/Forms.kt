package gorefest.shell.web.components

import gorefest.core.PlayerId
import gorefest.shell.web.DEFAULT_TIME_ZONE
import java.time.LocalDate
import kotlinx.html.DIV
import kotlinx.html.FlowContent
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
) = fieldSet("gf-chips") {
    attributes["style"] = "border: 0; padding: 0; margin: 0"
    legend("gf-vh") { +legend }
    players.forEach { player ->
        label("gf-chip") {
            input(InputType.checkBox, name = name) { value = player.toString(); this.checked = checked(player) }
            avatar(player, names[player], size = "gf-avatar--sm")
            span("gf-chip__check") { attributes["aria-hidden"] = "true"; icon("check") }
            +(names[player] ?: "Unknown player")
        }
    }
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

/** A search box with its magnifier: as you type it asks [url] and puts the answer at [target]. [extra] adds attributes to the input. */
fun FlowContent.searchField(placeholder: String, label: String, url: String, target: String, extra: INPUT.() -> Unit = {}) = div("gf-search") {
    icon("search")
    input(InputType.search, classes = "gf-input", name = "q") {
        this.placeholder = placeholder
        attributes["aria-label"] = label
        attributes["autocomplete"] = "off"
        hx("get", url)
        hx("trigger", "input changed delay:300ms, search")
        hx("target", target)
        hx("swap", "outerHTML")
        extra()
    }
}

/** The browser's time zone, sent along with a form that creates something dated (the server falls back to its default). */
fun FlowContent.browserTimeZone() = input(InputType.hidden, name = "timeZone") {
    value = DEFAULT_TIME_ZONE
    hyperscript("init set my value to Intl.DateTimeFormat().resolvedOptions().timeZone")
}

/** Dates as checkbox chips, submitted as [name] in ISO form. [disabled] dates can't be ticked (already past). */
fun FlowContent.dateChips(
    dates: List<LocalDate>, checked: (LocalDate) -> Boolean, disabled: (LocalDate) -> Boolean = { false }, name: String = "dates", legend: String = "Dates",
) = fieldSet("gf-chips") {
    attributes["style"] = "border: 0; padding: 0; margin: 0"
    legend("gf-vh") { +legend }
    dates.forEach { date ->
        label("gf-chip gf-chip--text") {
            input(InputType.checkBox, name = name) { value = date.toString(); this.checked = checked(date); this.disabled = disabled(date) }
            +date.short()
        }
    }
}
