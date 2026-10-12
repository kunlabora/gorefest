package gorefest.core.player

import gorefest.core.BggAccountLinked
import gorefest.core.BggAccountLinkedHashed
import gorefest.core.GameDayCancelled
import gorefest.core.GameDayEnded
import gorefest.core.LoggedIn
import gorefest.core.PlayerErased
import gorefest.core.PlayerJoined
import gorefest.core.PlayerRegistered
import gorefest.core.Rejection
import gorefest.core.eventsourcing.given
import gorefest.core.gameday.GameDays
import gorefest.core.gameday.anna
import gorefest.core.gameday.bram
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ErasePlayerTest {
    @Test
    fun `a registered player is erased`() {
        ErasePlayerDecider.given(PlayerRegistered(anna), LoggedIn(anna)).whenever(ErasePlayer(anna)).thenEmits(PlayerErased(anna))
    }

    @Test
    fun `erasing twice records it once`() {
        ErasePlayerDecider.given(PlayerRegistered(anna), PlayerErased(anna)).whenever(ErasePlayer(anna)).thenEmitsNothing()
    }

    @Test
    fun `somebody who never registered cannot be erased`() {
        ErasePlayerDecider.given(PlayerRegistered(bram)).whenever(ErasePlayer(anna)).thenRejects(Rejection.NoSuchPlayer)
    }

    @Test
    fun `an erased player's BGG username can be linked by someone else`() {
        LinkBggAccountDecider
            .given(BggAccountLinkedHashed(bram, "key-of-bramplays"), PlayerErased(bram))
            .whenever(LinkBggAccount(anna, "bramplays", "key-of-bramplays"))
            .thenEmits(BggAccountLinkedHashed(anna, "key-of-bramplays"))
    }

    @Test
    fun `an erased player's link is gone from the BGG accounts`() {
        val state = listOf(BggAccountLinked(anna, "AnnaPlays"), BggAccountLinkedHashed(bram, "key-of-bramplays"), PlayerErased(anna))
            .fold(BggAccounts.initialState, BggAccounts::evolve)

        state.isLinked(anna) shouldBe false
        state.isLinked(bram) shouldBe true
    }

    @Test
    fun `an erased player leaves the tables still to be played, not the ones that are over`() {
        val upcoming = gorefest.core.GameDayId(kotlin.uuid.Uuid.random())
        val over = gorefest.core.GameDayId(kotlin.uuid.Uuid.random())
        val cancelled = gorefest.core.GameDayId(kotlin.uuid.Uuid.random())
        val state = listOf(
            gameDayCreated(gameDay = upcoming, host = bram), PlayerJoined(upcoming, bram), PlayerJoined(upcoming, anna),
            gameDayCreated(gameDay = over, host = bram), PlayerJoined(over, bram), PlayerJoined(over, anna), GameDayEnded(over),
            gameDayCreated(gameDay = cancelled, host = bram), PlayerJoined(cancelled, anna), GameDayCancelled(cancelled),
            PlayerErased(anna),
        ).fold(GameDays.initialState, GameDays::evolve)

        state.getValue(upcoming).players shouldBe listOf(bram)
        state.getValue(over).players shouldBe listOf(bram, anna)
        state.getValue(cancelled).players shouldBe listOf(anna)
        state.getValue(gorefest7.let { upcoming }).host shouldBe bram
    }
}
