package gorefest.shell.web.components

import gorefest.core.PlayerId
import kotlinx.html.FlowContent
import kotlinx.html.span

/** Initials disc, coloured by player so the same person always gets the same hue. "?" without a name. */
fun FlowContent.avatar(player: PlayerId, name: String?, size: String = "") = span("gf-avatar $size".trim()) {
    attributes["aria-hidden"] = "true"
    attributes["data-hue"] = player.toString().sumOf { it.code }.mod(4).toString()
    +(name?.let(::initials) ?: "?")
}

/** A few small overlapping avatars. */
fun FlowContent.avatarStack(players: List<PlayerId>, names: Map<PlayerId, String>, max: Int = 5) = span("gf-avatars") {
    players.take(max).forEach { avatar(it, names[it], size = "gf-avatar--sm") }
}

/** The first letters of the first two words. Words that start with a symbol (an emoji) only count when there are no others. */
fun initials(name: String): String {
    val words = name.split(' ', '-').filter { it.isNotBlank() }
    val named = words.filter { Character.isLetterOrDigit(it.codePointAt(0)) }.ifEmpty { words }
    // A whole code point, not a Char: an emoji is two of those and half of one shows as "?".
    return named.take(2).joinToString("") { it.substring(0, it.offsetByCodePoints(0, 1)).uppercase() }.ifEmpty { "?" }
}
