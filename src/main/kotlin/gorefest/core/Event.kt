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

@Serializable
@SerialName("PlayerJoined")
data class PlayerJoined(val gameDay: GameDayId, val player: PlayerId) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay), Tags.player(player))
}

@Serializable
@SerialName("GameDayEnded")
data class GameDayEnded(val gameDay: GameDayId) : Event {
    override val tags get() = setOf(Tags.gameDay(gameDay))
}
