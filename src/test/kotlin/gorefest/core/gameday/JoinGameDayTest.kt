package gorefest.core.gameday

import gorefest.core.GameDayEnded
import gorefest.core.PlayerJoined
import gorefest.core.eventsourcing.given
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class JoinGameDayTest {
    @Test
    fun `player joins an open game day`() {
        JoinGameDayDecider
            .given(gameDayCreated(), PlayerJoined(gorefest7, anna))
            .whenever(JoinGameDay(gorefest7, bram))
            .thenEmits(PlayerJoined(gorefest7, bram))
    }

    @Test
    fun `joining twice is a no-op`() {
        JoinGameDayDecider
            .given(gameDayCreated(), PlayerJoined(gorefest7, bram))
            .whenever(JoinGameDay(gorefest7, bram))
            .thenEmitsNothing()
    }

    @Test
    fun `cannot join an unknown game day`() {
        JoinGameDayDecider.given().whenever(JoinGameDay(gorefest7, bram)).thenRejects("No such game day")
    }

    @Test
    fun `cannot join an ended game day`() {
        JoinGameDayDecider
            .given(gameDayCreated(), GameDayEnded(gorefest7))
            .whenever(JoinGameDay(gorefest7, bram))
            .thenRejects("This game day has ended")
    }

    @Test
    fun `other players' joins are outside the boundary`() {
        val query = JoinGameDayDecider.query(JoinGameDay(gorefest7, bram))
        query.matches(gorefest.core.typeName<PlayerJoined>(), PlayerJoined(gorefest7, anna).tags) shouldBe false
    }
}
