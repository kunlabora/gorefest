package gorefest.shell.web.suggestions

import gorefest.core.matching.SuggestionReason
import gorefest.core.matching.Suggestions
import gorefest.shell.web.components.asDuration
import gorefest.shell.web.components.inWords

private fun Int.inWordsLower() = inWords().lowercase()

/** The one-line reason under a suggestion. */
fun SuggestionReason.text(): String = when (this) {
    is SuggestionReason.EagerOwner -> "$name brought it and has been itching to play."
    is SuggestionReason.WantedBy -> when (names.size) {
        1 -> "${names.single()} really wants to play this."
        2 -> "${names.joinAnd()} both want to play this."
        else -> "${names.size.inWords()} of you really want to play this."
    }
    SuggestionReason.NewAndLiked -> "Nobody's played it and everyone's likely to enjoy it."
    is SuggestionReason.SafeBet -> when (tableSize) {
        1 -> "Safe bet: you rated it highly."
        2 -> "Safe bet: both of you rated it highly."
        else -> "Safe bet: all ${tableSize.inWordsLower()} of you rated it highly."
    }
    is SuggestionReason.BestAtTableSize -> "Best at exactly ${tableSize.inWordsLower()}, which is you."
    SuggestionReason.NobodyPlayedIt -> "Nobody's played it yet: something new for the table."
    is SuggestionReason.FitsWithRoomToSpare -> "Fits your ${budget.asDuration()} with room for snacks."
    is SuggestionReason.PlaysWellWith -> "Plays well with ${tableSize.inWordsLower()}."
    SuggestionReason.GoodMiddleGround -> "A good middle ground for this table."
    is SuggestionReason.NewToSome -> "New to ${names.joinAnd()}: a chance to try it."
}

/** What to say when nothing fits: a title and an explanation. */
fun Suggestions.NothingFits.title(): String = when (this) {
    Suggestions.NothingFits.NoPlayers -> "Who's playing?"
    is Suggestions.NothingFits.NoGameForTableSize -> "Nothing plays well with $tableSize"
    is Suggestions.NothingFits.NoGameInTime -> "Nothing fits $tableSize players in ${budget.asDuration()}"
}

fun Suggestions.NothingFits.text(): String = when (this) {
    Suggestions.NothingFits.NoPlayers -> "Tick the players at your table."
    is Suggestions.NothingFits.NoGameForTableSize -> "The shelf's short on games for $tableSize players. Try splitting into two tables."
    is Suggestions.NothingFits.NoGameInTime -> "Everything on the shelf takes longer. Loosen the time:"
}

private fun List<String>.joinAnd(): String = if (size < 2) joinToString() else dropLast(1).joinToString(", ") + " and " + last()
