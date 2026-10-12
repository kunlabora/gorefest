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
            Rejection.GameDayCancelled to "This game day was cancelled",
            Rejection.OnlyHostCanCancel to "Only the host can cancel the game day",
            Rejection.GameDayNotStarted to "The game day hasn't started yet",
            Rejection.GameDayAlreadyExists to "Game day already exists",
            Rejection.TitleMissing to "Give the game day a title",
            Rejection.DateInPast to "That date is in the past",
            Rejection.DateMissing to "Pick a date",
            Rejection.TitleTooLong to "That title is a bit long. Keep it under 80 characters",
            Rejection.PlaceTooLong to "That place is a bit long. Keep it under 80 characters",
            Rejection.UnknownTimeZone to "Unknown time zone",
            Rejection.OnlyHostCanEdit to "Only the host can change this game day",
            Rejection.OnlyHostCanRotateInvite to "Only the host can make a new invite link",
            Rejection.NotJoined to "Join the game day first",
            Rejection.NotOnShelf to "Nobody's bringing that game",
            Rejection.TooManyWishes to "You can pick 3 favourites: take one off first",
            Rejection.OwnerNotJoined to "They haven't joined this game day",
            Rejection.CantAskYourself to "That's your own game: just tick it",
            Rejection.AlreadyBringing to "They're already bringing that one",
            Rejection.TooManyBringRequests to "You've asked for 3 games already: take one back first",
            Rejection.OwnerAskedEnough to "They've been asked a lot already: leave it to them",
            Rejection.OwnerNotAcceptingRequests to "They'd rather not be asked to bring games",
            Rejection.NobodyPlayed to "Tick who played",
            Rejection.PlayersNotAtGameDay to "Only players at this game day can be in a log",
            Rejection.ImplausibleDuration to "That doesn't look like a real duration",
            Rejection.SameGameTwice to "Compare two different games",
            Rejection.AlreadyLinked to "You've already linked a BGG account",
            Rejection.BggAccountTaken to "That BGG account is already linked to another player",
            Rejection.NoSuchDatePoll to "No such date poll",
            Rejection.DatePollAlreadyExists to "That date poll already exists",
            Rejection.DatePollClosed to "The date is already picked",
            Rejection.NotInDatePoll to "Join the poll first",
            Rejection.OnlyHostCanPickDate to "Only the host can pick the date",
            Rejection.NotAnOption to "That date isn't one of the options",
            Rejection.TooFewDates to "Give at least two dates to choose from",
            Rejection.TooManyDates to "That's a lot of dates: keep it to 14",
        ).forEach { (rejection, text) -> rejection.message() shouldBe text }
    }
}
