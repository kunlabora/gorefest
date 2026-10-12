package gorefest.core.player

import gorefest.core.BggAccountLinked
import gorefest.core.BggAccountLinkedHashed
import gorefest.core.Rejection
import gorefest.core.eventsourcing.given
import gorefest.core.gameday.anna
import gorefest.core.gameday.bram
import org.junit.jupiter.api.Test

class LinkBggAccountTest {
    private fun link(player: gorefest.core.PlayerId, username: String) = LinkBggAccount(player, username, "key-of-${username.lowercase()}")

    @Test
    fun `a player links a free username, and only its hash is stored`() {
        LinkBggAccountDecider.given().whenever(link(anna, "AnnaPlays")).thenEmits(BggAccountLinkedHashed(anna, "key-of-annaplays"))
    }

    @Test
    fun `a username belongs to one player, whatever the case`() {
        LinkBggAccountDecider
            .given(BggAccountLinkedHashed(bram, "key-of-annaplays"))
            .whenever(link(anna, "AnnaPlays"))
            .thenRejects(Rejection.BggAccountTaken)
    }

    @Test
    fun `a link written before names were hashed still holds its username`() {
        LinkBggAccountDecider
            .given(BggAccountLinked(bram, "annaplays"))
            .whenever(link(anna, "AnnaPlays"))
            .thenRejects(Rejection.BggAccountTaken)
    }

    @Test
    fun `linking the same account again is a no-op, another one is refused`() {
        LinkBggAccountDecider.given(BggAccountLinkedHashed(anna, "key-of-annaplays")).whenever(link(anna, "annaplays")).thenEmitsNothing()
        LinkBggAccountDecider.given(BggAccountLinkedHashed(anna, "key-of-annaplays")).whenever(link(anna, "Other")).thenRejects(Rejection.AlreadyLinked)
        LinkBggAccountDecider.given(BggAccountLinked(anna, "AnnaPlays")).whenever(link(anna, "annaplays")).thenEmitsNothing()
        LinkBggAccountDecider.given(BggAccountLinked(anna, "AnnaPlays")).whenever(link(anna, "Other")).thenRejects(Rejection.AlreadyLinked)
    }

    @Test
    fun `other players' other usernames don't get in the way`() {
        LinkBggAccountDecider
            .given(BggAccountLinkedHashed(bram, "key-of-bramplays"))
            .whenever(link(anna, "AnnaPlays"))
            .thenEmits(BggAccountLinkedHashed(anna, "key-of-annaplays"))
    }
}
