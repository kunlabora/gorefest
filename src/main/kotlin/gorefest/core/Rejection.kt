package gorefest.core

/**
 * Why a decider said no. Facts, not sentences: `shell.web` words them (and decides how loudly).
 * A case that several deciders share is the same case everywhere.
 */
sealed interface Rejection {
    data object NoSuchGameDay : Rejection

    /** The game day has ended: its shelf, plays and invite are final. */
    data object GameDayOver : Rejection
    data object GameDayAlreadyExists : Rejection
    data object TitleMissing : Rejection
    data object DateInPast : Rejection
    data object UnknownTimeZone : Rejection
    data object OnlyHostCanEdit : Rejection
    data object OnlyHostCanRotateInvite : Rejection

    /** The player hasn't joined the game day. */
    data object NotJoined : Rejection

    /** The wish is about a game nobody brought. */
    data object NotOnShelf : Rejection

    /** The player already wishes for the most games they may. */
    data object TooManyWishes : Rejection
    data object NobodyPlayed : Rejection
    data object PlayersNotAtGameDay : Rejection
    data object ImplausibleDuration : Rejection
    data object SameGameTwice : Rejection

    /** The player linked another BGG account already. */
    data class AlreadyLinked(val username: String) : Rejection

    /** Another player linked this BGG account. */
    data object BggAccountTaken : Rejection

    // Date polls
    data object NoSuchDatePoll : Rejection
    data object DatePollAlreadyExists : Rejection

    /** The host already picked a date. */
    data object DatePollClosed : Rejection
    data object NotInDatePoll : Rejection
    data object OnlyHostCanPickDate : Rejection

    /** The date isn't one of the poll's options. */
    data object NotAnOption : Rejection
    data object TooFewDates : Rejection
    data object TooManyDates : Rejection
}
