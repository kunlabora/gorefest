package gorefest.shell.web

import gorefest.core.Rejection
import gorefest.core.datepoll.MAX_POLL_DATES
import gorefest.core.datepoll.MAX_POLL_GAMES
import gorefest.core.gameday.MAX_BRING_REQUESTS
import gorefest.core.gameday.MAX_PLACE_LENGTH
import gorefest.core.gameday.MAX_TITLE_LENGTH
import gorefest.core.gameday.MAX_WISHES

/** The sentence a person sees for a decider's rejection: one place for all of them. */
fun Rejection.message(): String = when (this) {
    Rejection.NoSuchGameDay -> "No such game day"
    Rejection.GameDayOver -> "This game day has ended"
    Rejection.GameDayCancelled -> "This game day was cancelled"
    Rejection.OnlyHostCanCancel -> "Only the host can cancel the game day"
    Rejection.GameDayNotStarted -> "The game day hasn't started yet"
    Rejection.GameDayAlreadyExists -> "Game day already exists"
    Rejection.TitleMissing -> "Give the game day a title"
    Rejection.DateInPast -> "That date is in the past"
    Rejection.DateMissing -> "Pick a date"
    Rejection.TitleTooLong -> "That title is a bit long. Keep it under $MAX_TITLE_LENGTH characters"
    Rejection.PlaceTooLong -> "That place is a bit long. Keep it under $MAX_PLACE_LENGTH characters"
    Rejection.UnknownTimeZone -> "Unknown time zone"
    Rejection.OnlyHostCanEdit -> "Only the host can change this game day"
    Rejection.OnlyHostCanRotateInvite -> "Only the host can make a new invite link"
    Rejection.NotJoined -> "Join the game day first"
    Rejection.NotOnShelf -> "Nobody's bringing that game"
    Rejection.TooManyWishes -> "You can pick $MAX_WISHES favourites: take one off first"
    Rejection.OwnerNotJoined -> "They haven't joined this game day"
    Rejection.CantAskYourself -> "That's your own game: just tick it"
    Rejection.AlreadyBringing -> "They're already bringing that one"
    Rejection.TooManyBringRequests -> "You've asked for $MAX_BRING_REQUESTS games already: take one back first"
    Rejection.OwnerAskedEnough -> "They've been asked a lot already: leave it to them"
    Rejection.OwnerNotAcceptingRequests -> "They'd rather not be asked to bring games"
    Rejection.NobodyPlayed -> "Tick who played"
    Rejection.PlayersNotAtGameDay -> "Only players at this game day can be in a log"
    Rejection.ImplausibleDuration -> "That doesn't look like a real duration"
    Rejection.SameGameTwice -> "Compare two different games"
    Rejection.AlreadyLinked -> "You've already linked a BGG account"
    Rejection.BggAccountTaken -> "That BGG account is already linked to another player"
    Rejection.NoSuchPlayer -> "No such player"
    Rejection.NoSuchDatePoll -> "No such date poll"
    Rejection.DatePollAlreadyExists -> "That date poll already exists"
    Rejection.DatePollClosed -> "The date is already picked"
    Rejection.NotInDatePoll -> "Join the poll first"
    Rejection.DatePollHostGone -> "This poll is over: the host is no longer on Boardfest"
    Rejection.OnlyHostCanPickDate -> "Only the host can pick the date"
    Rejection.NotAnOption -> "That date isn't one of the options"
    Rejection.TooFewDates -> "Give at least two dates to choose from"
    Rejection.TooManyDates -> "That's a lot of dates: keep it to ${MAX_POLL_DATES}"
    Rejection.PollAsksNoGames -> "This poll doesn't ask about games"
    Rejection.OnlyHostProposesGames -> "Only the host adds games"
    Rejection.NotInPoll -> "That game isn't in the poll anymore"
    Rejection.TooManyPollGames -> "That's a lot of games: keep it to $MAX_POLL_GAMES"
}
