package gorefest.shell.web.components

import kotlinx.html.INPUT
import kotlinx.html.id

/**
 * The control a [field] or [signinField] holds: the id its label points at, and the hint and error it is described by.
 * Control builders ([textInput], [codeInput]) need one in context, so a control can't be rendered without its label.
 */
class FieldControl internal constructor(val id: String, private val hintId: String?, private val errorId: String?) {
    internal fun INPUT.tie() {
        id = this@FieldControl.id
        listOfNotNull(hintId, errorId).joinToString(" ").takeIf { it.isNotEmpty() }?.let { attributes["aria-describedby"] = it }
        if (errorId != null) attributes["aria-invalid"] = "true"
    }
}
