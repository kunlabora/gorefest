package gorefest.core

import gorefest.core.eventsourcing.Tag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
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

/** The owner really wants to play a game they brought (or no longer does). A matching bonus. */
@Serializable
@SerialName("PlayWishChanged")
data class PlayWishChanged(val gameDay: GameDayId, val player: PlayerId, val game: BggId, val wantsToPlay: Boolean) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay), Tags.player(player), Tags.game(game))
}

@Serializable
@SerialName("GameDayEnded")
data class GameDayEnded(val gameDay: GameDayId) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay))
}
