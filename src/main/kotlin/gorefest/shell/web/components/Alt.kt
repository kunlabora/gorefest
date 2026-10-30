package gorefest.shell.web.components

import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.IMG
import kotlinx.html.div
import kotlinx.html.img

/** What an image tells someone who can't see it: [Text] for one that informs, [Decorative] for one that repeats what's next to it. */
sealed interface Alt {
    val text: String

    data class Text(override val text: String) : Alt {
        init { require(text.isNotBlank()) { "An image that informs says what it shows; use Alt.Decorative otherwise" } }
    }

    data object Decorative : Alt { override val text = "" }
}

/** An image with its [alt]: a screen reader reads an informing one and skips a decorative one. */
fun FlowContent.image(src: String, alt: Alt, classes: String? = null, configure: IMG.() -> Unit = {}) = img(src = src, alt = alt.text, classes = classes) { configure() }

/** Decoration (sample cards, a fan of box art, a meter that repeats a count): hidden from screen readers as a whole. */
fun FlowContent.decoration(classes: String, content: DIV.() -> Unit) = div(classes) {
    attributes["aria-hidden"] = "true"
    content()
}
