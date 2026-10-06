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

    /** A BGG username, case-insensitive like BGG itself. */
    fun bggUser(username: String) = Tag("bgg:${username.lowercase()}")
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

/** VIBE: "which would you rather try?" (unplayed). PLAYED: "which did you enjoy more?". */
enum class Basis { VIBE, PLAYED }

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

/** A host asks a group which of these [dates] suits them. [timeZone] is the zone the dates are in, for the game day made from the winner. */
@Serializable
@SerialName("DatePollCreated")
data class DatePollCreated(
    val poll: DatePollId,
    val host: PlayerId,
    val title: String,
    val place: String,
    val dates: List<@Serializable(with = LocalDateSerializer::class) LocalDate>,
    val timeZone: String,
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
