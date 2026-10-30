package gorefest.shell.web.gameday

import gorefest.core.gameday.MAX_PLACE_LENGTH
import gorefest.core.gameday.MAX_TITLE_LENGTH
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import io.ktor.http.Parameters
import kotlinx.html.*
import java.time.LocalDate

/** Creating and editing a game day: same form, different target. */
data class GameDayFormView(val heading: String, val back: Pair<String, String>, val appBarTitle: String, val action: String, val submitLabel: String, val editing: Boolean)

/**
 * Host a game day: the form on one side, and on desktop a tilted card of the invite on the other.
 * The card follows the fields in the browser (hyperscript on the form, no server round trip).
 */
context(me: CurrentPlayer)
fun HTML.gameDayFormPage(view: GameDayFormView, values: Parameters = Parameters.Empty) =
    page(view.heading, site = SiteBar(me, Nav.GAME_DAYS), mainClasses = "gf-landing") {
        wrap {
            split("gf-new-hero") {
                div("gf-split__copy") {
                    crumb(view.back.first, view.back.second)
                    p("gf-kicker") { +view.appBarTitle }
                    h1("gf-h1 gf-h1--landing") { +view.heading }
                    p("gf-lede gf-muted") {
                        +if (view.editing) "Fix the title, date or place. The invite link stays the same." else "Pick a date, share the link, and your friends bring the games."
                    }
                    consumer.gameDayForm(view, values = values)
                }
                invitePreview(me.name, values)
            }
        }
    }

private val PREVIEW_SYNC = """
    on input or htmx:afterSettle
      set t to #title's value
      if t is empty then set t to 'Your game day' end
      put t into #pv-title
      set d to #date's value
      if d is empty
        put 'Pick a date' into #pv-date
      else
        js(d) return new Date(d + 'T12:00:00').toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' }) end
        put it into #pv-date
      end
""".trimIndent()

/** What the group chat will show, as a tilted card. Decoration that mirrors the fields; desktop only. */
private fun FlowContent.invitePreview(host: String, values: Parameters) = decoration("gf-desk-only gf-pvwrap") {
    val title = values["title"].orEmpty().ifBlank { "Your game day" }
    val date = values["date"]?.let { runCatching { LocalDate.parse(it).full() }.getOrNull() } ?: "Pick a date"
    p("gf-kicker") { +"What your friends see" }
    article("gf-card gf-pv") {
        id = "invite-preview"
        div("gf-pv__art") { listOf(0, 1, 2).forEach { span("gf-pv__tile gf-pv__tile--$it") } }
        p("gf-kicker") { +"$host invites you to a game day" }
        h3("gf-pv__title") { id = "pv-title"; +title }
        ul("gf-pv__facts") {
            li { icon("calendar"); span { id = "pv-date"; +date } }
            li { icon("users"); +"Hosted by $host" }
        }
    }
}

/** Create and edit share this form; editing leaves the time zone as it was created. */
fun TagConsumer<*>.gameDayForm(view: GameDayFormView, error: String? = null, values: Parameters = Parameters.Empty) =
    form(action = view.action, method = FormMethod.post, classes = "gf-newform") {
        hx("post", view.action)
        hx("swap", "outerHTML")
        hyperscript(PREVIEW_SYNC)
        if (error != null) errorNotice(error)
        titleField(values["title"].orEmpty(), reroll = !view.editing)
        div("gf-newform__two") {
            field("date", "Date") {
                fieldInput("date", InputType.date) { required = true; value = values["date"].orEmpty() }
            }
            field("place", "Where") {
                fieldInput("place") { maxLength = MAX_PLACE_LENGTH.toString(); value = values["place"].orEmpty() }
            }
        }
        p("gf-hint gf-newform__hint") { +"Only people who have joined see it, not the invite or its preview. Leave out the house number if you like." }
        if (!view.editing) browserTimeZone()
        actionButton(view.submitLabel, ButtonStyle.PRIMARY, ButtonSize.LARGE, submit = true, spinner = true)
    }

/** The title input; when creating, an icon button beside it swaps in another generated title. */
fun FlowContent.titleField(value: String, reroll: Boolean) = div { id = "title-field"; titleFieldContent(value, reroll) }

fun TagConsumer<*>.titleField(value: String, reroll: Boolean) = div { id = "title-field"; titleFieldContent(value, reroll) }

private fun FlowContent.titleFieldContent(value: String, reroll: Boolean) = div("gf-titlerow") {
    field("title", "Title") {
        fieldInput("title") { placeholder = "Gorefest #7"; required = true; maxLength = MAX_TITLE_LENGTH.toString(); this.value = value }
    }
    if (reroll) iconButton("Another title", "refresh") {
        hx("get", "/gamedays/title")
        hx("include", "#title")
        hx("target", "#title-field")
        hx("swap", "outerHTML")
    }
}
