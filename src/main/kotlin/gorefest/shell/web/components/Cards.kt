package gorefest.shell.web.components

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span

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
