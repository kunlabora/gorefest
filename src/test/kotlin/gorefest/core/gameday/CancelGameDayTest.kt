package gorefest.core.gameday

import gorefest.contract.Obligation
import gorefest.core.BggId
import gorefest.core.Event
import gorefest.core.GameDayId
import kotlin.uuid.Uuid
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
    @Obligation("GD-25")
    fun `the host cancels a running game day`() {
        CancelGameDayDecider.given(gameDayCreated()).whenever(CancelGameDay(gorefest7, anna)).thenEmits(cancelled)
    }

    @Test
    @Obligation("GD-25")
    fun `only the host, not twice, not after it ended`() {
        CancelGameDayDecider.given(gameDayCreated()).whenever(CancelGameDay(gorefest7, bram)).thenRejects(Rejection.OnlyHostCanCancel)
        CancelGameDayDecider.given(gameDayCreated(), cancelled).whenever(CancelGameDay(gorefest7, anna)).thenRejects(Rejection.GameDayCancelled)
        CancelGameDayDecider.given(gameDayCreated(), GameDayEnded(gorefest7)).whenever(CancelGameDay(gorefest7, anna)).thenRejects(Rejection.GameDayOver)
        CancelGameDayDecider.given().whenever(CancelGameDay(gorefest7, anna)).thenRejects(Rejection.NoSuchGameDay)
    }

    @Test
    @Obligation("GD-27", "SH-15")
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
    @Obligation("GD-27", "SH-15")
    fun `unbringing is refused on a cancelled game day too`() {
        BringGameDecider.given(gameDayCreated(), PlayerJoined(gorefest7, anna), GameBrought(gorefest7, anna, BggId(13)), cancelled)
            .whenever(BringGame(gorefest7, anna, BggId(13), false)).thenRejects(Rejection.GameDayCancelled)
    }

    @Test
    @Obligation("GD-26")
    fun `a cancelled game day lists with the past ones, even with a date ahead`() {
        fun day(offset: Long): Pair<GameDayId, List<Event>> {
            val id = GameDayId(Uuid.random())
            return id to listOf(gameDayCreated(gameDay = id).copy(date = today.plusDays(offset)), PlayerJoined(id, anna))
        }
        val (upcoming, a) = day(7)
        val (calledOff, b) = day(1)
        val (past, c) = day(-7)
        val state = (a + b + c + GameDayCancelled(calledOff)).fold(GameDays.initialState, GameDays::evolve)

        with(GameDays) { state.forPlayer(anna) { today }.map { it.id } } shouldBe listOf(upcoming, calledOff, past)
    }

    @Test
    @Obligation("GD-29")
    fun `a stray end after the cancel changes nothing`() {
        val state = listOf(gameDayCreated(), cancelled, GameDayEnded(gorefest7)).fold(GameDays.initialState, GameDays::evolve)
        state.getValue(gorefest7).cancelled shouldBe true
        // Cancelling again still says it was cancelled, not that it ended.
        CancelGameDayDecider.given(gameDayCreated(), cancelled, GameDayEnded(gorefest7)).whenever(CancelGameDay(gorefest7, anna))
            .thenRejects(Rejection.GameDayCancelled)
    }

    @Test
    @Obligation("GD-29")
    fun `ending a cancelled game day does nothing, and it is never due`() {
        EndGameDayDecider.given(gameDayCreated(), cancelled).whenever(EndGameDay(gorefest7)).thenEmitsNothing()

        val state = listOf(gameDayCreated(), cancelled).fold(GameDays.initialState, GameDays::evolve)
        state.dueToEnd(Instant.parse("2026-10-30T00:00:00Z")) shouldBe emptyList()
    }

    @Test
    @Obligation("GD-26", "GD-29")
    fun `the read model carries the cancelled state`() {
        val state = listOf(gameDayCreated(), PlayerJoined(gorefest7, anna), cancelled).fold(GameDays.initialState, GameDays::evolve)

        state.getValue(gorefest7).cancelled shouldBe true
        state.getValue(gorefest7).ended shouldBe false
        // A cancelled day sits with the past ones, even though its date is ahead.
        with(GameDays) { state.forPlayer(anna) { today }.map { it.id } } shouldBe listOf(gorefest7)
    }
}
