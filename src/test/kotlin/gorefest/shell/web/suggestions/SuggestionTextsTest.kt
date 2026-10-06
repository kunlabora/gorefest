package gorefest.shell.web.suggestions

import gorefest.core.matching.SuggestionReason
import gorefest.core.matching.Suggestions
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SuggestionTextsTest {
    @Test
    fun `every reason has its sentence`() {
        mapOf(
            SuggestionReason.EagerOwner("Kim") to "Kim brought it and has been itching to play.",
            SuggestionReason.WantedBy(listOf("Anna")) to "Anna really wants to play this.",
            SuggestionReason.WantedBy(listOf("Anna", "Bram")) to "Anna and Bram both want to play this.",
            SuggestionReason.WantedBy(listOf("Anna", "Bram", "Kim")) to "Three of you really want to play this.",
            SuggestionReason.NewAndLiked to "Nobody's played it and everyone's likely to enjoy it.",
            SuggestionReason.SafeBet(2) to "Safe bet: both of you rated it highly.",
            SuggestionReason.SafeBet(4) to "Safe bet: all four of you rated it highly.",
            SuggestionReason.BestAtTableSize(3) to "Best at exactly three, which is you.",
            SuggestionReason.NobodyPlayedIt to "Nobody's played it yet: something new for the table.",
            SuggestionReason.FitsWithRoomToSpare(90) to "Fits your 90 minutes with room for snacks.",
            SuggestionReason.PlaysWellWith(2) to "Plays well with two.",
            SuggestionReason.GoodMiddleGround to "A good middle ground for this table.",
            SuggestionReason.NewToSome(listOf("Kim")) to "New to Kim: a chance to try it.",
            SuggestionReason.NewToSome(listOf("Kim", "Anna", "Bram")) to "New to Kim, Anna and Bram: a chance to try it.",
        ).forEach { (reason, text) -> reason.text() shouldBe text }
    }

    @Test
    fun `nothing fits says why`() {
        Suggestions.NothingFits.NoPlayers.title() shouldBe "Who's playing?"
        Suggestions.NothingFits.NoPlayers.text() shouldBe "Tick the players at your table."
        Suggestions.NothingFits.NoGameForTableSize(7).title() shouldBe "Nothing plays well with 7"
        Suggestions.NothingFits.NoGameInTime(4, 45, 120, 1).title() shouldBe "Nothing fits 4 players in 45 minutes"
        Suggestions.NothingFits.NoGameInTime(4, 150, null, 0).title() shouldBe "Nothing fits 4 players in 2 h 30 min"
    }
}
