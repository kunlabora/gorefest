package gorefest.core.gameday

import gorefest.contract.Obligation
import gorefest.core.Rejection
import gorefest.core.GameDayCreated
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
    @Obligation("GD-1")
    fun `host creates a game day and joins it`() {
        CreateGameDayDecider
            .given()
            .whenever(create)
            .thenEmits(gameDayCreated(), PlayerJoined(gorefest7, anna))
    }

    @Test
    fun `cannot create the same game day twice`() {
        CreateGameDayDecider.given(gameDayCreated()).whenever(create).thenRejects(Rejection.GameDayAlreadyExists)
    }

    @Test
    fun `other game days don't matter`() {
        CreateGameDayDecider
            .given(gameDayCreated(gameDay = gorefest.core.GameDayId(kotlin.uuid.Uuid.random())))
            .whenever(create)
            .thenEmits(gameDayCreated(), PlayerJoined(gorefest7, anna))
    }

    @Test
    @Obligation("GD-2")
    fun `title is required`() {
        CreateGameDayDecider.given().whenever(create.copy(title = " ")).thenRejects(Rejection.TitleMissing)
    }

    @Test
    @Obligation("GD-2")
    fun `title and place have a maximum length`() {
        CreateGameDayDecider.given().whenever(create.copy(title = "A".repeat(MAX_TITLE_LENGTH + 1))).thenRejects(Rejection.TitleTooLong)
        CreateGameDayDecider.given().whenever(create.copy(place = "A".repeat(MAX_PLACE_LENGTH + 1))).thenRejects(Rejection.PlaceTooLong)
        CreateGameDayDecider.given().whenever(create.copy(title = " " + "A".repeat(MAX_TITLE_LENGTH))).thenEmits(
            GameDayCreated(gorefest7, anna, "A".repeat(MAX_TITLE_LENGTH), today.plusDays(3), "Anna's place", "Europe/Brussels"),
            PlayerJoined(gorefest7, anna),
        )
    }

    @Test
    @Obligation("GD-2")
    fun `date cannot be in the past`() {
        CreateGameDayDecider.given().whenever(create.copy(date = today.minusDays(1))).thenRejects(Rejection.DateInPast)
    }

    @Test
    @Obligation("GD-2")
    fun `time zone must exist`() {
        CreateGameDayDecider.given().whenever(create.copy(timeZone = "Mars/Olympus")).thenRejects(Rejection.UnknownTimeZone)
    }
}
