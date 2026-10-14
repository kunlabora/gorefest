package gorefest.core

import gorefest.core.eventsourcing.Tag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import java.time.Instant
import java.time.LocalDate

/**
 * Every event in the global log. The [SerialName] is the stored `type`; never rename one
 * once it has been written — add a new event instead.
 */
@Serializable
sealed interface Event {
    val tags: Set<Tag>
}

object Tags {
    fun player(id: PlayerId) = Tag("player:$id")
    fun gameDay(id: GameDayId) = Tag("gameday:$id")
    fun game(id: BggId) = Tag("game:$id")
    fun datePoll(id: DatePollId) = Tag("datepoll:$id")
    fun gameNight(id: GameNightId) = Tag("gamenight:$id")

    /** A BGG username, case-insensitive like BGG itself. */
    fun bggUser(username: String) = Tag("bgg:${username.lowercase()}")

    /** The same, as a keyed hash of the lowercased username: the log never holds the name itself. */
    fun bggUserKey(key: String) = Tag("bggkey:$key")
}

/** The stored type name of an event class, for use in queries. */
inline fun <reified E : Event> typeName(): String = serializer<E>().descriptor.serialName

/** The stored type name of this event. */
val Event.type: String get() = serializer(javaClass).descriptor.serialName

/** First login. The player's email and name live in the `players` table, not in events, so they can be erased. */
@Serializable
@SerialName("PlayerRegistered")
data class PlayerRegistered(val player: PlayerId) : Event {
    override val tags get() = setOf(Tags.player(player))
}

/**
 * The player's account was erased: their email, name, sessions and collection are gone from the plain tables.
 * The ids in older events stay, but point at nobody (the log is never rewritten); this records that it happened.
 */
@Serializable
@SerialName("PlayerErased")
data class PlayerErased(val player: PlayerId) : Event {
    override val tags get() = setOf(Tags.player(player))
}

@Serializable
@SerialName("LoggedIn")
data class LoggedIn(val player: PlayerId) : Event {
    override val tags get() = setOf(Tags.player(player))
}

/** The player proved they own this BGG account by putting a code in its profile. */
@Serializable
@SerialName("BggAccountLinked")
data class BggAccountLinked(val player: PlayerId, val username: String) : Event {
    override val tags get() = setOf(Tags.player(player), Tags.bggUser(username))
}

/**
 * Like [BggAccountLinked], but the log holds only [usernameKey], a keyed hash of the lowercased username: enough to
 * keep one account per player and one player per account, not enough to read the name. The readable name lives in
 * the collection sync table, which goes when the account does. [BggAccountLinked] is no longer written, but old rows still count.
 */
@Serializable
@SerialName("BggAccountLinkedHashed")
data class BggAccountLinkedHashed(val player: PlayerId, val usernameKey: String) : Event {
    override val tags get() = setOf(Tags.player(player), Tags.bggUserKey(usernameKey))
}

/** The player took their BGG account off: its username can be linked by anyone again, and the collection is gone. */
@Serializable
@SerialName("BggAccountUnlinked")
data class BggAccountUnlinked(val player: PlayerId) : Event {
    override val tags get() = setOf(Tags.player(player))
}

@Serializable
@SerialName("GameDayCreated")
data class GameDayCreated(
    val gameDay: GameDayId,
    val host: PlayerId,
    val title: String,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val place: String,
    /** IANA zone id; the game day auto-closes at midnight in this zone. */
    val timeZone: String,
) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay), Tags.player(host))
}

/** The host changed what's on the invite. The time zone stays as created. */
@Serializable
@SerialName("GameDayDetailsChanged")
data class GameDayDetailsChanged(
    val gameDay: GameDayId,
    val title: String,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val place: String,
) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay))
}

/** The host replaced the invite link; links signed for earlier versions stop working. */
@Serializable
@SerialName("InviteLinkRotated")
data class InviteLinkRotated(val gameDay: GameDayId, val version: Int) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay))
}

@Serializable
@SerialName("PlayerJoined")
data class PlayerJoined(val gameDay: GameDayId, val player: PlayerId) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay), Tags.player(player))
}

@Serializable
@SerialName("GameBrought")
data class GameBrought(val gameDay: GameDayId, val player: PlayerId, val game: BggId) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay), Tags.player(player), Tags.game(game))
}

@Serializable
@SerialName("GameUnbrought")
data class GameUnbrought(val gameDay: GameDayId, val player: PlayerId, val game: BggId) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay), Tags.player(player), Tags.game(game))
}

/** A player really wants to play a game on the shelf (or no longer does). A matching bonus. Once only the owner could; the shape is the same. */
@Serializable
@SerialName("PlayWishChanged")
data class PlayWishChanged(val gameDay: GameDayId, val player: PlayerId, val game: BggId, val wantsToPlay: Boolean) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay), Tags.player(player), Tags.game(game))
}

/** [asker] asks [owner], who joined the same game day, to bring [game] from their collection. Nothing displayable: names come from the BGG cache. */
@Serializable
@SerialName("BringRequested")
data class BringRequested(val gameDay: GameDayId, val asker: PlayerId, val owner: PlayerId, val game: BggId) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay), Tags.player(asker), Tags.player(owner), Tags.game(game))
}

/** The asker took the request back. It stops counting against both caps. */
@Serializable
@SerialName("BringRequestWithdrawn")
data class BringRequestWithdrawn(val gameDay: GameDayId, val asker: PlayerId, val owner: PlayerId, val game: BggId) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay), Tags.player(asker), Tags.player(owner), Tags.game(game))
}

/** The owner said "not this time" to one asker's request. A request is answered by [GameBrought], so it has no event of its own. */
@Serializable
@SerialName("BringRequestDeclined")
data class BringRequestDeclined(val gameDay: GameDayId, val owner: PlayerId, val game: BggId, val asker: PlayerId) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay), Tags.player(asker), Tags.player(owner), Tags.game(game))
}

/** Whether people on the player's game days may ask them to bring games. Allowed until the player says otherwise. */
@Serializable
@SerialName("BringRequestsAllowedChanged")
data class BringRequestsAllowedChanged(val player: PlayerId, val allowed: Boolean) : Event {
    override val tags get() = setOf(Tags.player(player))
}

/**
 * VIBE: "which would you rather try?" (unplayed). PLAYED: "which did you enjoy more?".
 * WATCHED: "now that you've seen it, which would you rather play?", after watching a table play the game (U9).
 */
enum class Basis {
    VIBE, PLAYED, WATCHED;

    /** The model an answer trains: watching a table play counts as having played it, for taste only (it doesn't make the game "played"). */
    val model: Basis get() = if (this == WATCHED) PLAYED else this
}

/** Both great / both meh say something absolute about the pair, not just which one wins. */
enum class ComparisonResult { A, B, BOTH_GREAT, BOTH_MEH }

@Serializable
@SerialName("ComparisonRecorded")
data class ComparisonRecorded(
    val player: PlayerId,
    val a: BggId,
    val b: BggId,
    val result: ComparisonResult,
    val basis: Basis,
    @Serializable(with = InstantSerializer::class) val at: Instant,
) : Event {
    override val tags get() = setOf(Tags.player(player), Tags.game(a), Tags.game(b))
}

/** The player told us whether they've played a game, e.g. "haven't played" in the wizard. */
@Serializable
@SerialName("GamePlayStatusDeclared")
data class GamePlayStatusDeclared(val player: PlayerId, val game: BggId, val played: Boolean) : Event {
    override val tags get() = setOf(Tags.player(player), Tags.game(game))
}

/** At a checkpoint: this game was played at the game day, by these players, for about this long. */
@Serializable
@SerialName("PlayLogged")
data class PlayLogged(
    val gameDay: GameDayId,
    val game: BggId,
    val players: List<PlayerId>,
    val minutes: Int,
    val loggedBy: PlayerId,
    @Serializable(with = InstantSerializer::class) val at: Instant,
) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay), Tags.game(game)) + players.map(Tags::player)
}

/** The host called the game day off. It stays visible but read-only, and its invite stops working. */
@Serializable
@SerialName("GameDayCancelled")
data class GameDayCancelled(val gameDay: GameDayId) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay))
}

@Serializable
@SerialName("GameDayEnded")
data class GameDayEnded(val gameDay: GameDayId) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay))
}

/**
 * A host asks a group which of these [dates] suits them. [timeZone] is the zone the dates are in, for the game day made from the winner.
 * With [asksAboutGames] the poll also asks which games people would play, and the pick makes a game night instead of a game day (U11).
 */
@Serializable
@SerialName("DatePollCreated")
data class DatePollCreated(
    val poll: DatePollId,
    val host: PlayerId,
    val title: String,
    val place: String,
    val dates: List<@Serializable(with = LocalDateSerializer::class) LocalDate>,
    val timeZone: String,
    val asksAboutGames: Boolean = false,
) : Event {
    override val tags get() = setOf(Tags.datePoll(poll), Tags.player(host))
}

/** A player came in through the poll's invite link. */
@Serializable
@SerialName("DatePollJoined")
data class DatePollJoined(val poll: DatePollId, val player: PlayerId) : Event {
    override val tags get() = setOf(Tags.datePoll(poll), Tags.player(player))
}

/** The dates this player can make; it replaces their earlier answer. Dates left out mean "can't". */
@Serializable
@SerialName("DatePollAnswered")
data class DatePollAnswered(
    val poll: DatePollId,
    val player: PlayerId,
    val available: List<@Serializable(with = LocalDateSerializer::class) LocalDate>,
) : Event {
    override val tags get() = setOf(Tags.datePoll(poll), Tags.player(player))
}

/** The host picked [date]; the poll is over and [gameDay] was created for it. */
@Serializable
@SerialName("DatePollClosed")
data class DatePollClosed(
    val poll: DatePollId,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val gameDay: GameDayId,
) : Event {
    override val tags get() = setOf(Tags.datePoll(poll), Tags.gameDay(gameDay))
}

/** The host puts [game] to the poll (U11): found in someone's collection or by search. */
@Serializable
@SerialName("DatePollGameProposed")
data class DatePollGameProposed(val poll: DatePollId, val game: BggId) : Event {
    override val tags get() = setOf(Tags.datePoll(poll), Tags.game(game))
}

/** The host takes [game] off the poll; its answers and offers stop counting. */
@Serializable
@SerialName("DatePollGameWithdrawn")
data class DatePollGameWithdrawn(val poll: DatePollId, val game: BggId) : Event {
    override val tags get() = setOf(Tags.datePoll(poll), Tags.game(game))
}

/** [player] would ([yes]) or wouldn't play [game]; it replaces their earlier answer for it. A yes becomes a wish on the game night. */
@Serializable
@SerialName("DatePollGameAnswered")
data class DatePollGameAnswered(val poll: DatePollId, val player: PlayerId, val game: BggId, val yes: Boolean) : Event {
    override val tags get() = setOf(Tags.datePoll(poll), Tags.player(player), Tags.game(game))
}

/** [player] will bring [game] if it's played ([bringing]), or says "not this time" or takes it back; it replaces their earlier answer for it. */
@Serializable
@SerialName("DatePollBringAnswered")
data class DatePollBringAnswered(val poll: DatePollId, val player: PlayerId, val game: BggId, val bringing: Boolean) : Event {
    override val tags get() = setOf(Tags.datePoll(poll), Tags.player(player), Tags.game(game))
}

/** The host picked [date] in a poll that asked about games: the poll is over and [gameNight] was made for it. */
@Serializable
@SerialName("DatePollPickedForGameNight")
data class DatePollPickedForGameNight(
    val poll: DatePollId,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val gameNight: GameNightId,
) : Event {
    override val tags get() = setOf(Tags.datePoll(poll), Tags.gameNight(gameNight))
}

/** One game on a game night's plan: who brings it and who said they'd play it. */
@Serializable
data class PlannedGame(val game: BggId, val bringer: PlayerId, val wantedBy: List<PlayerId> = emptyList())

/**
 * A game night (U11), made whole when its poll's date is picked: the [players] who can come (host first), the [plan] of
 * games someone coming will bring, and the [missing] games nobody coming can bring. Nothing changes it afterwards yet.
 */
@Serializable
@SerialName("GameNightCreated")
data class GameNightCreated(
    val gameNight: GameNightId,
    val poll: DatePollId,
    val host: PlayerId,
    val title: String,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val place: String,
    val timeZone: String,
    val players: List<PlayerId>,
    val plan: List<PlannedGame>,
    val missing: List<BggId> = emptyList(),
) : Event {
    override val tags get() = setOf(Tags.gameNight(gameNight), Tags.datePoll(poll)) + players.map(Tags::player) + plan.map { Tags.game(it.game) }
}
