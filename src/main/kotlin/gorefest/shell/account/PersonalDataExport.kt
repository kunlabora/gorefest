package gorefest.shell.account

import gorefest.core.GameDayId
import gorefest.core.PlayerId
import gorefest.core.datepoll.DatePollBoard
import gorefest.core.gameday.GameDaySummary
import gorefest.shell.auth.Players
import gorefest.shell.bgg.Catalog
import gorefest.shell.bgg.CollectionSync
import gorefest.shell.db.Database
import gorefest.shell.eventstore.EventStore
import kotlinx.serialization.json.Json

/** Gathers one player's data from where it lives and turns it into the file the admin sends ([PersonalData]). */
class PersonalDataExport(
    private val db: Database,
    private val store: EventStore,
    private val players: Players,
    private val collectionSync: CollectionSync,
    private val catalog: Catalog,
    private val gameDays: () -> Map<GameDayId, GameDaySummary>,
    private val datePolls: () -> DatePollBoard,
) {
    private val json = Json { prettyPrint = true }

    /** The file's text, or null if there is no such account (it may already be erased). */
    suspend fun of(id: PlayerId): String? {
        val (player, signedUp, events) = db.transaction { Triple(players.find(id), players.createdAt(id), store.readAll(0).map { it.event }) }
        if (player == null) return null
        val profile = PersonalProfile(
            name = player.name,
            email = player.email,
            signedUp = signedUp,
            bggUsername = collectionSync.status(id)?.username,
            collection = collectionSync.collection(id).map { catalog[it]?.name ?: "BGG game $it" }.sorted(),
        )
        val lookups = Lookups(
            game = { catalog[it]?.name ?: "BGG game $it" },
            gameDay = { gameDays()[it]?.title },
            poll = { datePolls().of(it)?.title },
        )
        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), PersonalData.of(id, profile, events, lookups))
    }
}
