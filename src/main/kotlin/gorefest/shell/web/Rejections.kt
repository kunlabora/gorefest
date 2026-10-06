package gorefest.shell.web

import gorefest.core.Rejection
import gorefest.core.datepoll.MAX_POLL_DATES
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
    Rejection.UnknownTimeZone -> "Unknown time zone"
    Rejection.OnlyHostCanEdit -> "Only the host can change this game day"
    Rejection.OnlyHostCanRotateInvite -> "Only the host can make a new invite link"
    Rejection.NotJoined -> "Join the game day first"
    Rejection.NotOnShelf -> "Nobody's bringing that game"
    Rejection.TooManyWishes -> "You can pick $MAX_WISHES favourites: take one off first"
    Rejection.NobodyPlayed -> "Tick who played"
    Rejection.PlayersNotAtGameDay -> "Only players at this game day can be in a log"
    Rejection.ImplausibleDuration -> "That doesn't look like a real duration"
    Rejection.SameGameTwice -> "Compare two different games"
    is Rejection.AlreadyLinked -> "You've already linked $username"
    Rejection.BggAccountTaken -> "That BGG account is already linked to another player"
    Rejection.NoSuchDatePoll -> "No such date poll"
    Rejection.DatePollAlreadyExists -> "That date poll already exists"
    Rejection.DatePollClosed -> "The date is already picked"
    Rejection.NotInDatePoll -> "Join the poll first"
    Rejection.OnlyHostCanPickDate -> "Only the host can pick the date"
    Rejection.NotAnOption -> "That date isn't one of the options"
    Rejection.TooFewDates -> "Give at least two dates to choose from"
    Rejection.TooManyDates -> "That's a lot of dates: keep it to ${MAX_POLL_DATES}"
}
