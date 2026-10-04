package gorefest.shell.web

import gorefest.core.Rejection

/** The sentence a person sees for a decider's rejection: one place for all of them. */
fun Rejection.message(): String = when (this) {
    Rejection.NoSuchGameDay -> "No such game day"
    Rejection.GameDayOver -> "This game day has ended"
    Rejection.GameDayAlreadyExists -> "Game day already exists"
    Rejection.TitleMissing -> "Give the game day a title"
    Rejection.DateInPast -> "That date is in the past"
    Rejection.UnknownTimeZone -> "Unknown time zone"
    Rejection.OnlyHostCanEdit -> "Only the host can change this game day"
    Rejection.OnlyHostCanRotateInvite -> "Only the host can make a new invite link"
    Rejection.NotJoined -> "Join the game day first"
    Rejection.NotBringing -> "Bring the game first"
    Rejection.NobodyPlayed -> "Tick who played"
    Rejection.PlayersNotAtGameDay -> "Only players at this game day can be in a log"
    Rejection.ImplausibleDuration -> "That doesn't look like a real duration"
    Rejection.SameGameTwice -> "Compare two different games"
    is Rejection.AlreadyLinked -> "You've already linked $username"
    Rejection.BggAccountTaken -> "That BGG account is already linked to another player"
}
