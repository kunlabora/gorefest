package gorefest.shell.web.components

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.style

/** The one next thing to do on a screen: kicker, title, a line of text and a button. */
fun FlowContent.nextStep(kicker: String, title: String, text: String, href: String, action: String, style: ButtonStyle = ButtonStyle.PRIMARY) =
    section("gf-next") {
        p("gf-kicker") { +kicker }
        h2("gf-next__title") { +title }
        p("gf-next__text") { +text }
        linkButton(href, action, style, block = true)
    }

/** A section heading with an optional link on the right. */
fun FlowContent.sectionHead(title: String, linkHref: String? = null, linkLabel: String? = null) = div("gf-sechead") {
    h2("gf-h2") { +title }
    if (linkHref != null && linkLabel != null) a(linkHref, classes = "gf-link") { +linkLabel }
}

/** A number with a label, in the stats row. */
fun FlowContent.stat(number: String, label: String) = div("gf-stat") {
    span("gf-stat__num") { +number }
    +" " // the spans are blocks; screen readers and text extraction need the space
    span("gf-stat__label") { +label }
}

/** A card linking to a game day or a poll: a date badge ([month] over [day]), the title, a meta line, an optional tag and a chevron. */
fun FlowContent.dayCard(href: String, month: String, day: String, title: String, meta: String, tag: String? = null) = a(href, classes = "gf-daycard") {
    span("gf-date") {
        span("gf-date__month") { +month }
        +" " // flex ignores it; screen readers and text extraction need it
        span("gf-date__day") { +day }
    }
    span {
        style = "min-width: 0"
        span("gf-daycard__title") { +title }
        span("gf-daycard__meta") { +meta }
        if (tag != null) span("gf-tag") { style = "margin-top: 6px"; +tag }
    }
    span {
        style = "display: flex; transform: rotate(180deg); color: var(--ink-muted)"
        icon("back")
    }
}
