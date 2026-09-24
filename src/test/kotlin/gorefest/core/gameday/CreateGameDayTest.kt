package gorefest.core.gameday

import gorefest.core.PlayerJoined
import gorefest.core.eventsourcing.given
import org.junit.jupiter.api.Test

class CreateGameDayTest {
    private val create = CreateGameDay(
        gameDay = gorefest7,
        host = anna,
        title = "  Gorefest #7 ",
        date = today.plusDays(3),
        place = "Anna's place",
        timeZone = "Europe/Brussels",
        today = today,
    )

    @Test
    fun `host creates a game day and joins it`() {
        CreateGameDayDecider
            .given()
            .whenever(create)
            .thenEmits(gameDayCreated(), PlayerJoined(gorefest7, anna))
    }

    @Test
    fun `cannot create the same game day twice`() {
        CreateGameDayDecider.given(gameDayCreated()).whenever(create).thenRejects("Game day already exists")
    }

    @Test
    fun `other game days don't matter`() {
        CreateGameDayDecider
            .given(gameDayCreated(gameDay = gorefest.core.GameDayId(kotlin.uuid.Uuid.random())))
            .whenever(create)
            .thenEmits(gameDayCreated(), PlayerJoined(gorefest7, anna))
    }

    @Test
    fun `title is required`() {
        CreateGameDayDecider.given().whenever(create.copy(title = " ")).thenRejects("Give the game day a title")
    }

    @Test
    fun `date cannot be in the past`() {
        CreateGameDayDecider.given().whenever(create.copy(date = today.minusDays(1))).thenRejects("That date is in the past")
    }

    @Test
    fun `time zone must exist`() {
        CreateGameDayDecider.given().whenever(create.copy(timeZone = "Mars/Olympus")).thenRejects("Unknown time zone")
    }
}
