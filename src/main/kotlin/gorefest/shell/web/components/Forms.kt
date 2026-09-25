package gorefest.shell.web.components

import gorefest.core.PlayerId
import kotlinx.html.DIV
import kotlinx.html.FlowContent
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

/** A segmented control: radios named [name] with [options] as labels and values. With [idPrefix], each gets id `prefix-option`. */
fun FlowContent.segmented(name: String, legend: String, options: List<String>, checked: String, idPrefix: String? = null) = fieldSet("gf-seg") {
    attributes["style"] = "border: 1px solid var(--line); margin: 0"
    legend("gf-vh") { +legend }
    options.forEach { option ->
        label {
            input(InputType.radio, name = name) {
                if (idPrefix != null) id = "$idPrefix-$option"
                value = option
                this.checked = option == checked
            }
            +option
        }
    }
}
