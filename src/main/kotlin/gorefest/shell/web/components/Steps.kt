package gorefest.shell.web.components

import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.OL
import kotlinx.html.P
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.div
import kotlinx.html.role

/** How a step's number disc looks: dark ink (the default) or the soft brand tint. */
enum class StepTone(val css: String) { INK(""), SOFT(" gf-stepper__num--soft") }

/**
 * Numbered steps: a disc, a title, a line of text. [modifier] adds `gf-stepper--cols` (three across from 720px)
 * or `gf-stepper--sections` (big titles with a free-form body, for forms). The one place "how it works" lists come from.
 */
fun FlowContent.stepper(modifier: String = "", steps: OL.() -> Unit) = ol(("gf-stepper $modifier").trim()) { steps() }

/** A numbered step with a title and a line of text, and room for [extra] under it (a screenshot, a button). */
fun OL.step(number: Int, title: String, text: P.() -> Unit, tone: StepTone = StepTone.INK, extra: (DIV.() -> Unit)? = null) = li("gf-stepper__item") {
    stepNumber(number, tone)
    heading(Level.H3, title, "gf-stepper__title")
    p("gf-muted gf-stepper__text") { text() }
    if (extra != null) div("gf-stepper__extra") { extra() }
}

/** A numbered step whose body is a form section (game, who, how long), under a display-size title. */
fun OL.formStep(number: Int, title: String, body: FlowContent.() -> Unit) = li("gf-stepper__item") {
    stepNumber(number, StepTone.INK)
    heading(Level.H2, title, "gf-stepper__title")
    div("gf-stepper__body") { body() }
}

/** An illustrated step: a small picture, "Step n", the title and a line of text. No disc; the picture is the marker. */
fun OL.illustratedStep(number: Int, title: String, text: String, illustration: FlowContent.() -> Unit) = li("gf-stepper__item gf-stepper__item--ill") {
    illustration()
    p("gf-kicker") { +"Step $number" }
    heading(Level.H3, title, "gf-stepper__title")
    p("gf-muted gf-stepper__text") { +text }
}

private fun FlowContent.stepNumber(number: Int, tone: StepTone) = span("gf-stepper__num${tone.css}") { attributes["aria-hidden"] = "true"; +"$number" }

/** Progress through a round of [total] steps, [step] of them done: a progress bar named [label], with "3 / 12" beside the pips. */
fun FlowContent.progressSteps(step: Int, total: Int, label: String) = div("gf-progress") {
    role = "progressbar"
    attributes["aria-label"] = AccessibleName(label).text
    attributes["aria-valuemin"] = "0"
    attributes["aria-valuemax"] = "$total"
    attributes["aria-valuenow"] = "$step"
    div("gf-progress__track") { repeat(total) { span(if (it < step) "gf-progress__pip gf-progress__pip--done" else "gf-progress__pip") {} } }
    span("gf-progress__count") { +"${step + 1} / $total" }
}
