package gorefest.core.player

import gorefest.core.BggAccountLinked
import gorefest.core.eventsourcing.given
import gorefest.core.gameday.anna
import gorefest.core.gameday.bram
import org.junit.jupiter.api.Test

class LinkBggAccountTest {
    @Test
    fun `a player links a free username`() {
        LinkBggAccountDecider.given().whenever(LinkBggAccount(anna, "AnnaPlays")).thenEmits(BggAccountLinked(anna, "AnnaPlays"))
    }

    @Test
    fun `a username belongs to one player, whatever the case`() {
        LinkBggAccountDecider
            .given(BggAccountLinked(bram, "annaplays"))
            .whenever(LinkBggAccount(anna, "AnnaPlays"))
            .thenRejects("That BGG account is already linked to another player")
    }

    @Test
    fun `linking the same account again is a no-op, another one is refused`() {
        LinkBggAccountDecider.given(BggAccountLinked(anna, "AnnaPlays")).whenever(LinkBggAccount(anna, "annaplays")).thenEmitsNothing()
        LinkBggAccountDecider.given(BggAccountLinked(anna, "AnnaPlays")).whenever(LinkBggAccount(anna, "Other")).thenRejects("You've already linked AnnaPlays")
    }

    @Test
    fun `other players' links of other usernames are outside the boundary`() {
        LinkBggAccountDecider
            .given(BggAccountLinked(bram, "BramPlays"))
            .whenever(LinkBggAccount(anna, "AnnaPlays"))
            .thenEmits(BggAccountLinked(anna, "AnnaPlays"))
    }
}
