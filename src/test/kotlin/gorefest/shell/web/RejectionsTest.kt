package gorefest.shell.web

import gorefest.core.Rejection
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RejectionsTest {
    @Test
    fun `every rejection has its sentence`() {
        mapOf(
            Rejection.NoSuchGameDay to "No such game day",
            Rejection.GameDayOver to "This game day has ended",
            Rejection.GameDayAlreadyExists to "Game day already exists",
            Rejection.TitleMissing to "Give the game day a title",
            Rejection.DateInPast to "That date is in the past",
            Rejection.UnknownTimeZone to "Unknown time zone",
            Rejection.OnlyHostCanEdit to "Only the host can change this game day",
            Rejection.OnlyHostCanRotateInvite to "Only the host can make a new invite link",
            Rejection.NotJoined to "Join the game day first",
            Rejection.NotBringing to "Bring the game first",
            Rejection.NobodyPlayed to "Tick who played",
            Rejection.PlayersNotAtGameDay to "Only players at this game day can be in a log",
            Rejection.ImplausibleDuration to "That doesn't look like a real duration",
            Rejection.SameGameTwice to "Compare two different games",
            Rejection.AlreadyLinked("AnnaPlays") to "You've already linked AnnaPlays",
            Rejection.BggAccountTaken to "That BGG account is already linked to another player",
        ).forEach { (rejection, text) -> rejection.message() shouldBe text }
    }
}
