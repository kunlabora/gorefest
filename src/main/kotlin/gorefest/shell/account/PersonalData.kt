package gorefest.shell.account

import gorefest.core.BggAccountLinked
import gorefest.core.BggAccountLinkedHashed
import gorefest.core.BggId
import gorefest.core.BringRequestDeclined
import gorefest.core.BringRequestWithdrawn
import gorefest.core.BringRequested
import gorefest.core.BringRequestsAllowedChanged
import gorefest.core.ComparisonRecorded
import gorefest.core.DatePollAnswered
import gorefest.core.DatePollCreated
import gorefest.core.DatePollId
import gorefest.core.DatePollJoined
import gorefest.core.Event
import gorefest.core.GameBrought
import gorefest.core.GameDayCreated
import gorefest.core.GameDayId
import gorefest.core.GamePlayStatusDeclared
import gorefest.core.GameUnbrought
import gorefest.core.LoggedIn
import gorefest.core.PlayLogged
import gorefest.core.PlayWishChanged
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.PlayerRegistered
import gorefest.core.Tags
import gorefest.core.type
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant

/** What the person's own account says about them, for the file the admin sends. Pure: the shell hands in what it looked up. */
data class PersonalProfile(
    val name: String?,
    val email: String,
    val signedUp: Instant?,
    val bggUsername: String?,
    val collection: List<String>,
)

/** Names for the ids in events, so the file reads in words. */
data class Lookups(
    val game: (BggId) -> String,
    val gameDay: (GameDayId) -> String?,
    val poll: (DatePollId) -> String?,
)

/**
 * Everything kept about one player, with game and game day names instead of ids. Other people never appear: not their
 * names, not their ids. A bring request says "someone", a played game says how many people were at the table.
 */
object PersonalData {
    fun of(me: PlayerId, profile: PersonalProfile, events: List<Event>, lookups: Lookups): JsonObject {
        val mine = events.filter { Tags.player(me) in it.tags }
        return buildJsonObject {
            put("about", buildJsonObject {
                put("name", profile.name)
                put("email", profile.email)
                put("signedUp", profile.signedUp?.toString())
                put("bggUsername", profile.bggUsername)
            })
            putJsonArray("collection") { profile.collection.forEach { add(JsonPrimitive(it)) } }
            put("signIns", mine.count { it is LoggedIn })
            put("history", JsonArray(mine.mapNotNull { describe(me, it, lookups) }))
        }
    }

    private fun describe(me: PlayerId, event: Event, l: Lookups): JsonElement? = when (event) {
        is LoggedIn -> null
        is PlayerRegistered -> entry("Signed up")
        is BggAccountLinked -> entry("Linked a BGG account", "username" to event.username)
        is BggAccountLinkedHashed -> entry("Linked a BGG account")
        is GameDayCreated -> entry("Hosted a game day", "title" to event.title, "date" to event.date.toString(), "place" to event.place)
        is PlayerJoined -> entry("Joined a game day", "gameDay" to l.gameDay(event.gameDay))
        is GameBrought -> entry("Said you bring a game", "game" to l.game(event.game), "gameDay" to l.gameDay(event.gameDay))
        is GameUnbrought -> entry("Took a game off the shelf", "game" to l.game(event.game), "gameDay" to l.gameDay(event.gameDay))
        is PlayWishChanged -> entry(if (event.wantsToPlay) "Hearted a game" else "Un-hearted a game", "game" to l.game(event.game), "gameDay" to l.gameDay(event.gameDay))
        is BringRequested -> entry(if (event.asker == me) "Asked someone to bring a game" else "Someone asked you to bring a game", "game" to l.game(event.game), "gameDay" to l.gameDay(event.gameDay))
        is BringRequestWithdrawn -> entry(if (event.asker == me) "Took back a request to bring a game" else "Someone took back a request to you", "game" to l.game(event.game), "gameDay" to l.gameDay(event.gameDay))
        is BringRequestDeclined -> entry(if (event.owner == me) "Said not this time to a request" else "Someone said not this time to your request", "game" to l.game(event.game), "gameDay" to l.gameDay(event.gameDay))
        is BringRequestsAllowedChanged -> entry(if (event.allowed) "Allowed bring requests" else "Switched bring requests off")
        is ComparisonRecorded -> entry("Compared two games", "a" to l.game(event.a), "b" to l.game(event.b), "answer" to event.result.name, "basis" to event.basis.name, "at" to event.at.toString())
        is GamePlayStatusDeclared -> entry(if (event.played) "Said you've played a game" else "Said you haven't played a game", "game" to l.game(event.game))
        is PlayLogged -> entry("Played a game", "game" to l.game(event.game), "gameDay" to l.gameDay(event.gameDay), "minutes" to event.minutes.toString(), "peopleAtTheTable" to event.players.size.toString(), "at" to event.at.toString())
        is DatePollCreated -> entry("Started a date poll", "title" to event.title, "place" to event.place, "dates" to event.dates.joinToString(", "))
        is DatePollJoined -> entry("Joined a date poll", "poll" to l.poll(event.poll))
        is DatePollAnswered -> entry("Answered a date poll", "poll" to l.poll(event.poll), "canMake" to event.available.joinToString(", "))
        else -> entry(event.type)
    }

    private fun entry(what: String, vararg details: Pair<String, String?>): JsonElement = buildJsonObject {
        put("what", what)
        details.forEach { (key, value) -> put(key, value?.let(::JsonPrimitive) ?: JsonNull) }
    }
}
