package gorefest.core.gameday

import gorefest.contract.Obligation
import gorefest.core.Rejection
import gorefest.core.GameDayEnded
import gorefest.core.PlayerJoined
import gorefest.core.eventsourcing.given
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class JoinGameDayTest {
    @Test
    @Obligation("GD-16")
    fun `player joins an open game day`() {
        JoinGameDayDecider
            .given(gameDayCreated(), PlayerJoined(gorefest7, anna))
            .whenever(JoinGameDay(gorefest7, bram))
            .thenEmits(PlayerJoined(gorefest7, bram))
    }

    @Test
    @Obligation("GD-16")
    fun `joining twice is a no-op`() {
        JoinGameDayDecider
            .given(gameDayCreated(), PlayerJoined(gorefest7, bram))
            .whenever(JoinGameDay(gorefest7, bram))
            .thenEmitsNothing()
    }

    @Test
    fun `cannot join an unknown game day`() {
        JoinGameDayDecider.given().whenever(JoinGameDay(gorefest7, bram)).thenRejects(Rejection.NoSuchGameDay)
    }

    @Test
    @Obligation("GD-8")
    fun `cannot join an ended game day`() {
        JoinGameDayDecider
            .given(gameDayCreated(), GameDayEnded(gorefest7))
            .whenever(JoinGameDay(gorefest7, bram))
            .thenRejects(Rejection.GameDayOver)
    }

    @Test
    @Obligation("GD-18")
    fun `other players' joins are outside the boundary`() {
        val query = JoinGameDayDecider.query(JoinGameDay(gorefest7, bram))
        query.matches(gorefest.core.typeName<PlayerJoined>(), PlayerJoined(gorefest7, anna).tags) shouldBe false
    }
}
