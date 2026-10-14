package gorefest.core

/**
 * Why a decider said no. Facts, not sentences: `shell.web` words them (and decides how loudly).
 * A case that several deciders share is the same case everywhere.
 */
sealed interface Rejection {
    data object NoSuchGameDay : Rejection

    /** The game day has ended: its shelf, plays and invite are final. */
    data object GameDayOver : Rejection

    /** The host cancelled the game day: it is read-only. */
    data object GameDayCancelled : Rejection
    data object OnlyHostCanCancel : Rejection
    /** Plays can only be logged from the game day's date. */
    data object GameDayNotStarted : Rejection
    data object GameDayAlreadyExists : Rejection
    data object TitleMissing : Rejection
    data object DateInPast : Rejection

    /** No date came with the request, or one that isn't a date. */
    data object DateMissing : Rejection
    data object TitleTooLong : Rejection
    data object PlaceTooLong : Rejection
    data object UnknownTimeZone : Rejection
    data object OnlyHostCanEdit : Rejection
    data object OnlyHostCanRotateInvite : Rejection

    /** The player hasn't joined the game day. */
    data object NotJoined : Rejection

    /** The wish is about a game nobody brought. */
    data object NotOnShelf : Rejection

    /** The player already wishes for the most games they may. */
    data object TooManyWishes : Rejection

    // Bring requests
    /** The person asked to bring a game hasn't joined the game day. */
    data object OwnerNotJoined : Rejection
    data object CantAskYourself : Rejection

    /** The owner already brings that game. */
    data object AlreadyBringing : Rejection

    /** The asker has as many requests standing as they may have. */
    data object TooManyBringRequests : Rejection

    /** The owner has been asked as often as is fair for one game day. */
    data object OwnerAskedEnough : Rejection

    /** The owner turned bring requests off. */
    data object OwnerNotAcceptingRequests : Rejection

    data object NobodyPlayed : Rejection
    data object PlayersNotAtGameDay : Rejection
    data object ImplausibleDuration : Rejection
    data object SameGameTwice : Rejection

    /** The player linked another BGG account already. */
    data object AlreadyLinked : Rejection

    /** Another player linked this BGG account. */
    data object BggAccountTaken : Rejection

    /** No such player, or one that was never registered. */
    data object NoSuchPlayer : Rejection

    // Date polls
    data object NoSuchDatePoll : Rejection
    data object DatePollAlreadyExists : Rejection

    /** The host already picked a date. */
    data object DatePollClosed : Rejection
    data object NotInDatePoll : Rejection

    /** The host's account was erased, so nobody can pick a date: the poll is over. */
    data object DatePollHostGone : Rejection
    data object OnlyHostCanPickDate : Rejection

    /** The date isn't one of the poll's options. */
    data object NotAnOption : Rejection
    data object TooFewDates : Rejection
    data object TooManyDates : Rejection

    // Games on a date poll (U11)
    /** The poll doesn't ask about games. */
    data object PollAsksNoGames : Rejection
    data object OnlyHostProposesGames : Rejection

    /** The game isn't (or no longer) one of the poll's games. */
    data object NotInPoll : Rejection
    data object TooManyPollGames : Rejection

    /** A yes in a poll is a wish, so the same cap; said in the poll's own words. */
    data object TooManyPollYeses : Rejection
}
