package gorefest.shell.web.components

import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.P
import kotlinx.html.SECTION
import kotlinx.html.section
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.unsafe

enum class NoticeKind(val css: String) { INFO("gf-notice"), GOOD("gf-notice gf-notice--good"), ERROR("gf-notice gf-notice--error") }

private fun noticeBody(icon: String, kind: NoticeKind, id: String?, content: P.() -> Unit): DIV.() -> Unit = {
    if (id != null) this.id = id
    if (kind == NoticeKind.ERROR) role = "alert"
    icon(icon)
    p { content() }
}

/** A notice with an icon and one paragraph. Errors are announced (`role="alert"`). */
fun FlowContent.notice(icon: String, kind: NoticeKind = NoticeKind.INFO, id: String? = null, content: P.() -> Unit) =
    div(kind.css, noticeBody(icon, kind, id, content))

/** The same notice as the root of an htmx fragment. */
fun TagConsumer<*>.notice(icon: String, kind: NoticeKind = NoticeKind.INFO, id: String? = null, content: P.() -> Unit) =
    div(kind.css, noticeBody(icon, kind, id, content))

fun FlowContent.errorNotice(message: String, id: String? = null) = notice("alert", NoticeKind.ERROR, id) { +message }

fun TagConsumer<*>.errorNotice(message: String, id: String? = null) = notice("alert", NoticeKind.ERROR, id) { +message }

/** The error line under a form field. */
fun FlowContent.fieldError(message: String, id: String? = null) = p("gf-error") {
    if (id != null) this.id = id
    role = "alert"
    icon("alert")
    +message
}

/** The dice-box illustration, a title, a line of text and an optional action. */
fun FlowContent.emptyState(title: String, text: String, action: FlowContent.() -> Unit = {}) = div("gf-empty") {
    unsafe { +EMPTY_ART }
    h3("gf-empty__title") { +title }
    p("gf-empty__text") { +text }
    action()
}

private const val EMPTY_ART = """<svg class="gf-empty__art" viewBox="0 0 96 72" aria-hidden="true"><rect class="box" x="6" y="30" width="56" height="36" rx="6"/><rect class="box" x="30" y="16" width="56" height="36" rx="6"/><rect class="lid" x="18" y="4" width="40" height="40" rx="8" transform="rotate(-8 38 24)"/><circle class="pip" cx="30" cy="16" r="3.5"/><circle class="pip" cx="38" cy="24" r="3.5"/><circle class="pip" cx="46" cy="32" r="3.5"/></svg>"""

/** A notice that waits on something (BGG fetching a collection): a spinner and [message], read out when it changes. */
fun FlowContent.busyNotice(message: String) = div("gf-notice") {
    attributes["aria-live"] = "polite"
    spinner(always = true)
    p { +message }
}

/** An empty line that a script fills with a short message (a limit reached); a screen reader reads what appears. */
fun FlowContent.liveLine(id: String, classes: String) = p(classes) {
    this.id = id
    attributes["aria-live"] = "polite"
}

/** A section htmx swaps whenever its inputs change (the suggestions): a screen reader hears the new content. */
fun TagConsumer<*>.liveSection(id: String, classes: String, content: SECTION.() -> Unit) = section(classes) {
    this.id = id
    attributes["aria-live"] = "polite"
    content()
}
