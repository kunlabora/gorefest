package gorefest.core.gameday

import gorefest.core.BggId
import gorefest.core.GameBrought
import gorefest.core.GameDayCancelled
import gorefest.core.GameDayEnded
import gorefest.core.PlayLogged
import gorefest.core.PlayerJoined
import gorefest.core.Rejection
import gorefest.core.eventsourcing.given
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class CancelGameDayTest {
    private val cancelled = GameDayCancelled(gorefest7)

    @Test
    fun `the host cancels a running game day`() {
        CancelGameDayDecider.given(gameDayCreated()).whenever(CancelGameDay(gorefest7, anna)).thenEmits(cancelled)
    }

    @Test
    fun `only the host, not twice, not after it ended`() {
        CancelGameDayDecider.given(gameDayCreated()).whenever(CancelGameDay(gorefest7, bram)).thenRejects(Rejection.OnlyHostCanCancel)
        CancelGameDayDecider.given(gameDayCreated(), cancelled).whenever(CancelGameDay(gorefest7, anna)).thenRejects(Rejection.GameDayCancelled)
        CancelGameDayDecider.given(gameDayCreated(), GameDayEnded(gorefest7)).whenever(CancelGameDay(gorefest7, anna)).thenRejects(Rejection.GameDayOver)
        CancelGameDayDecider.given().whenever(CancelGameDay(gorefest7, anna)).thenRejects(Rejection.NoSuchGameDay)
    }

    @Test
    fun `a cancelled game day is read-only`() {
        val given = arrayOf(gameDayCreated(), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, bram), cancelled)
        JoinGameDayDecider.given(*given).whenever(JoinGameDay(gorefest7, bram)).thenRejects(Rejection.GameDayCancelled)
        EditGameDayDecider.given(*given).whenever(EditGameDay(gorefest7, anna, "New", today.plusDays(5), "x", today)).thenRejects(Rejection.GameDayCancelled)
        RotateInviteLinkDecider.given(*given).whenever(RotateInviteLink(gorefest7, anna)).thenRejects(Rejection.GameDayCancelled)
        BringGameDecider.given(*given).whenever(BringGame(gorefest7, anna, BggId(13), true)).thenRejects(Rejection.GameDayCancelled)
        SetPlayWishDecider.given(*given, GameBrought(gorefest7, anna, BggId(13))).whenever(SetPlayWish(gorefest7, anna, BggId(13), true))
            .thenRejects(Rejection.GameDayCancelled)
        LogPlayDecider.given(*given).whenever(PlayLogged(gorefest7, BggId(13), listOf(anna), 45, anna, Instant.parse("2026-09-27T12:00:00Z")))
            .thenRejects(Rejection.GameDayCancelled)
    }

    @Test
    fun `ending a cancelled game day does nothing, and it is never due`() {
        EndGameDayDecider.given(gameDayCreated(), cancelled).whenever(EndGameDay(gorefest7)).thenEmitsNothing()

        val state = listOf(gameDayCreated(), cancelled).fold(GameDays.initialState, GameDays::evolve)
        state.dueToEnd(Instant.parse("2026-10-30T00:00:00Z")) shouldBe emptyList()
    }

    @Test
    fun `the read model carries the cancelled state`() {
        val state = listOf(gameDayCreated(), PlayerJoined(gorefest7, anna), cancelled).fold(GameDays.initialState, GameDays::evolve)

        state.getValue(gorefest7).cancelled shouldBe true
        state.getValue(gorefest7).ended shouldBe false
        // A cancelled day sits with the past ones, even though its date is ahead.
        with(GameDays) { state.forPlayer(anna) { today }.map { it.id } } shouldBe listOf(gorefest7)
    }
}
