package gorefest.core.gameday

import gorefest.core.Event
import gorefest.core.GameDayDetailsChanged
import gorefest.core.GameDayEnded
import gorefest.core.GameDayId
import gorefest.core.PlayerJoined
import gorefest.core.gameday.GameDays.forPlayer
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class GameDaysTest {
    private fun project(vararg events: Event) = events.fold(GameDays.initialState, GameDays::evolve)

    @Test
    fun `details changes update the summary`() {
        val state = project(gameDayCreated(), GameDayDetailsChanged(gorefest7, "Boardfest #8", today, "Kim's"))

        state.getValue(gorefest7).title shouldBe "Boardfest #8"
        state.getValue(gorefest7).place shouldBe "Kim's"
    }

    @Test
    fun `a player sees only joined game days, upcoming soonest first, then past latest first`() {
        fun day(offset: Long, host: gorefest.core.PlayerId = anna): Pair<GameDayId, List<Event>> {
            val id = GameDayId(Uuid.random())
            return id to listOf(gameDayCreated(gameDay = id, host = host).copy(date = today.plusDays(offset)), PlayerJoined(id, host))
        }
        val (inAWeek, a) = day(7)
        val (tomorrow, b) = day(1)
        val (lastMonth, c) = day(-30)
        val (lastWeek, d) = day(-7)
        val (endedToday, e) = day(0)
        val (notMine, f) = day(2, host = bram)
        val state = project(*(a + b + c + d + e + f + GameDayEnded(endedToday)).toTypedArray())

        state.forPlayer(anna) { today }.map { it.id } shouldContainExactly listOf(tomorrow, inAWeek, endedToday, lastWeek, lastMonth)
        state.forPlayer(bram) { today }.map { it.id } shouldContainExactly listOf(notMine)
    }
}
