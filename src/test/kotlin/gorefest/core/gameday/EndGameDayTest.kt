package gorefest.core.gameday

import gorefest.core.GameDayEnded
import gorefest.core.GameDayId
import gorefest.core.eventsourcing.given
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.uuid.Uuid

class EndGameDayTest {
    @Test
    fun `ending is idempotent`() {
        EndGameDayDecider.given(gameDayCreated()).whenever(EndGameDay(gorefest7)).thenEmits(GameDayEnded(gorefest7))
        EndGameDayDecider.given(gameDayCreated(), GameDayEnded(gorefest7)).whenever(EndGameDay(gorefest7)).thenEmitsNothing()
        EndGameDayDecider.given().whenever(EndGameDay(gorefest7)).thenRejects("No such game day")
    }

    @Test
    fun `a game day is due at midnight after its date, in its own time zone`() {
        val tokyo = GameDayId(Uuid.random())
        val state = listOf(
            gameDayCreated(),                                            // 27 Sep, Europe/Brussels (UTC+2)
            gameDayCreated(gameDay = tokyo).copy(timeZone = "Asia/Tokyo"), // 27 Sep, UTC+9
        ).fold(GameDays.initialState, GameDays::evolve)

        state.dueToEnd(Instant.parse("2026-09-27T14:59:59Z")) shouldBe emptyList()
        state.dueToEnd(Instant.parse("2026-09-27T15:00:00Z")) shouldBe listOf(tokyo)      // midnight in Tokyo
        state.dueToEnd(Instant.parse("2026-09-27T22:00:00Z")).toSet() shouldBe setOf(gorefest7, tokyo) // midnight in Brussels
    }
}
