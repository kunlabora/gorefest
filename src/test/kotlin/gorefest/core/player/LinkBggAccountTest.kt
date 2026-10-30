package gorefest.core.player

import gorefest.contract.Obligation
import gorefest.core.BggAccountLinked
import gorefest.core.BggAccountLinkedHashed
import gorefest.core.BggAccountUnlinked
import gorefest.core.Rejection
import gorefest.core.eventsourcing.given
import gorefest.core.gameday.anna
import gorefest.core.gameday.bram
import org.junit.jupiter.api.Test

class LinkBggAccountTest {
    private fun link(player: gorefest.core.PlayerId, username: String) = LinkBggAccount(player, username, "key-of-${username.lowercase()}")

    @Test
    @Obligation("BGG-6")
    fun `a player links a free username, and only its hash is stored`() {
        LinkBggAccountDecider.given().whenever(link(anna, "AnnaPlays")).thenEmits(BggAccountLinkedHashed(anna, "key-of-annaplays"))
    }

    @Test
    @Obligation("BGG-4")
    fun `a username belongs to one player, whatever the case`() {
        LinkBggAccountDecider
            .given(BggAccountLinkedHashed(bram, "key-of-annaplays"))
            .whenever(link(anna, "AnnaPlays"))
            .thenRejects(Rejection.BggAccountTaken)
    }

    @Test
    @Obligation("BGG-4")
    fun `a link written before names were hashed still holds its username`() {
        LinkBggAccountDecider
            .given(BggAccountLinked(bram, "annaplays"))
            .whenever(link(anna, "AnnaPlays"))
            .thenRejects(Rejection.BggAccountTaken)
    }

    @Test
    @Obligation("BGG-5")
    fun `linking the same account again is a no-op, another one is refused`() {
        LinkBggAccountDecider.given(BggAccountLinkedHashed(anna, "key-of-annaplays")).whenever(link(anna, "annaplays")).thenEmitsNothing()
        LinkBggAccountDecider.given(BggAccountLinkedHashed(anna, "key-of-annaplays")).whenever(link(anna, "Other")).thenRejects(Rejection.AlreadyLinked)
        LinkBggAccountDecider.given(BggAccountLinked(anna, "AnnaPlays")).whenever(link(anna, "annaplays")).thenEmitsNothing()
        LinkBggAccountDecider.given(BggAccountLinked(anna, "AnnaPlays")).whenever(link(anna, "Other")).thenRejects(Rejection.AlreadyLinked)
    }

    @Test
    @Obligation("BGG-4")
    fun `other players' other usernames don't get in the way`() {
        LinkBggAccountDecider
            .given(BggAccountLinkedHashed(bram, "key-of-bramplays"))
            .whenever(link(anna, "AnnaPlays"))
            .thenEmits(BggAccountLinkedHashed(anna, "key-of-annaplays"))
    }

    @Test
    @Obligation("BGG-7")
    fun `unlinking is recorded once, and does nothing without a link`() {
        UnlinkBggAccountDecider.given(BggAccountLinkedHashed(anna, "key-of-annaplays")).whenever(UnlinkBggAccount(anna)).thenEmits(BggAccountUnlinked(anna))
        UnlinkBggAccountDecider.given(BggAccountLinked(anna, "AnnaPlays")).whenever(UnlinkBggAccount(anna)).thenEmits(BggAccountUnlinked(anna))
        UnlinkBggAccountDecider.given(BggAccountLinkedHashed(anna, "k"), BggAccountUnlinked(anna)).whenever(UnlinkBggAccount(anna)).thenEmitsNothing()
        UnlinkBggAccountDecider.given().whenever(UnlinkBggAccount(anna)).thenEmitsNothing()
    }

    @Test
    @Obligation("BGG-7")
    fun `after unlinking, the username is free and the player can link another`() {
        LinkBggAccountDecider
            .given(BggAccountLinkedHashed(bram, "key-of-bramplays"), BggAccountUnlinked(bram))
            .whenever(link(anna, "BramPlays"))
            .thenEmits(BggAccountLinkedHashed(anna, "key-of-bramplays"))
        LinkBggAccountDecider
            .given(BggAccountLinkedHashed(anna, "key-of-annaplays"), BggAccountUnlinked(anna))
            .whenever(link(anna, "Other"))
            .thenEmits(BggAccountLinkedHashed(anna, "key-of-other"))
    }

    @Test
    @Obligation("BGG-5")
    fun `linking again after an unlink counts the new link, not the old one`() {
        LinkBggAccountDecider
            .given(BggAccountLinkedHashed(anna, "key-of-annaplays"), BggAccountUnlinked(anna), BggAccountLinkedHashed(anna, "key-of-other"))
            .whenever(link(anna, "AnnaPlays"))
            .thenRejects(Rejection.AlreadyLinked)
    }
}
