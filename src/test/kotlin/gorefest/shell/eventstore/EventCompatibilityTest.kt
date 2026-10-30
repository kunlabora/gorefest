package gorefest.shell.eventstore

import gorefest.contract.Obligation
import gorefest.core.Basis
import gorefest.core.BggAccountLinked
import gorefest.core.BggAccountLinkedHashed
import gorefest.core.BggAccountUnlinked
import gorefest.core.BggId
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.Event
import gorefest.core.GameBrought
import gorefest.core.GameDayCreated
import gorefest.core.DatePollAnswered
import gorefest.core.DatePollBringAnswered
import gorefest.core.DatePollGameAnswered
import gorefest.core.DatePollGameProposed
import gorefest.core.DatePollGameWithdrawn
import gorefest.core.DatePollPickedForGameNight
import gorefest.core.GameNightCreated
import gorefest.core.GameNightId
import gorefest.core.PlannedGame
import gorefest.core.DatePollClosed
import gorefest.core.DatePollCreated
import gorefest.core.DatePollId
import gorefest.core.DatePollJoined
import gorefest.core.GameDayDetailsChanged
import gorefest.core.GameDayCancelled
import gorefest.core.GameDayEnded
import gorefest.core.GameDayId
import gorefest.core.BringRequestDeclined
import gorefest.core.BringRequestWithdrawn
import gorefest.core.BringRequested
import gorefest.core.BringRequestsAllowedChanged
import gorefest.core.GamePlayStatusDeclared
import gorefest.core.GameUnbrought
import gorefest.core.InviteLinkRotated
import gorefest.core.LoggedIn
import gorefest.core.PlayLogged
import gorefest.core.PlayWishChanged
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.PlayerErased
import gorefest.core.PlayerRegistered
import gorefest.core.type
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.descriptors.elementNames
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Stored events are decoded into the current classes on every replay, so a change to an event must
 * keep old rows decodable. Each entry is an event exactly as first stored.
 *
 * Add an entry when you add an event. Never edit an existing one: if one has to change to make this
 * pass, you're making a breaking change. Add a new event, or an upcaster, instead.
 */
class EventCompatibilityTest {
    private val p1 = "00000000-0000-0000-0000-000000000001"
    private val p2 = "00000000-0000-0000-0000-000000000002"
    private val d1 = "00000000-0000-0000-0000-0000000000d1"
    private fun player(s: String) = PlayerId(Uuid.parse(s))
    private val gameDay = GameDayId.parse(d1)
    private val poll = DatePollId.parse(d1)
    private val gameNight = GameNightId.parse(d1)

    private val stored: List<Pair<String, Event>> = listOf(
        """{"type":"PlayerRegistered","player":"$p1"}""" to
            PlayerRegistered(player(p1)),
        """{"type":"LoggedIn","player":"$p1"}""" to
            LoggedIn(player(p1)),
        """{"type":"PlayerErased","player":"$p1"}""" to
            PlayerErased(player(p1)),
        """{"type":"BggAccountLinked","player":"$p1","username":"Anna"}""" to
            BggAccountLinked(player(p1), "Anna"),
        """{"type":"BggAccountLinkedHashed","player":"$p1","usernameKey":"ab12"}""" to
            BggAccountLinkedHashed(player(p1), "ab12"),
        """{"type":"BggAccountUnlinked","player":"$p1"}""" to
            BggAccountUnlinked(player(p1)),
        """{"type":"GameDayCreated","gameDay":"$d1","host":"$p1","title":"Gorefest #7","date":"2026-10-03","place":"Tim's","timeZone":"Europe/Brussels"}""" to
            GameDayCreated(gameDay, player(p1), "Gorefest #7", LocalDate.parse("2026-10-03"), "Tim's", "Europe/Brussels"),
        """{"type":"GameDayDetailsChanged","gameDay":"$d1","title":"Boardfest #8","date":"2026-10-04","place":"Anna's"}""" to
            GameDayDetailsChanged(gameDay, "Boardfest #8", LocalDate.parse("2026-10-04"), "Anna's"),
        """{"type":"InviteLinkRotated","gameDay":"$d1","version":2}""" to
            InviteLinkRotated(gameDay, 2),
        """{"type":"PlayerJoined","gameDay":"$d1","player":"$p2"}""" to
            PlayerJoined(gameDay, player(p2)),
        """{"type":"DatePollCreated","poll":"$d1","host":"$p1","title":"Boardfest #9","place":"Tim's","dates":["2026-10-09","2026-10-10"],"timeZone":"Europe/Brussels"}""" to
            DatePollCreated(poll, player(p1), "Boardfest #9", "Tim's", listOf(LocalDate.parse("2026-10-09"), LocalDate.parse("2026-10-10")), "Europe/Brussels"),
        """{"type":"DatePollJoined","poll":"$d1","player":"$p2"}""" to
            DatePollJoined(poll, player(p2)),
        """{"type":"DatePollAnswered","poll":"$d1","player":"$p2","available":["2026-10-10"]}""" to
            DatePollAnswered(poll, player(p2), listOf(LocalDate.parse("2026-10-10"))),
        """{"type":"DatePollClosed","poll":"$d1","date":"2026-10-10","gameDay":"$d1"}""" to
            DatePollClosed(poll, LocalDate.parse("2026-10-10"), gameDay),
        """{"type":"DatePollCreated","poll":"$d1","host":"$p1","title":"Small night","place":"Tim's","dates":["2026-10-09","2026-10-10"],"timeZone":"Europe/Brussels","asksAboutGames":true}""" to
            DatePollCreated(poll, player(p1), "Small night", "Tim's", listOf(LocalDate.parse("2026-10-09"), LocalDate.parse("2026-10-10")), "Europe/Brussels", asksAboutGames = true),
        """{"type":"DatePollGameProposed","poll":"$d1","game":13}""" to
            DatePollGameProposed(poll, BggId(13)),
        """{"type":"DatePollGameWithdrawn","poll":"$d1","game":13}""" to
            DatePollGameWithdrawn(poll, BggId(13)),
        """{"type":"DatePollGameAnswered","poll":"$d1","player":"$p2","game":13,"yes":true}""" to
            DatePollGameAnswered(poll, player(p2), BggId(13), true),
        """{"type":"DatePollBringAnswered","poll":"$d1","player":"$p2","game":13,"bringing":true}""" to
            DatePollBringAnswered(poll, player(p2), BggId(13), true),
        """{"type":"DatePollPickedForGameNight","poll":"$d1","date":"2026-10-10","gameNight":"$d1"}""" to
            DatePollPickedForGameNight(poll, LocalDate.parse("2026-10-10"), gameNight),
        """{"type":"GameNightCreated","gameNight":"$d1","poll":"$d1","host":"$p1","title":"Small night","date":"2026-10-10","place":"Tim's","timeZone":"Europe/Brussels","players":["$p1","$p2"],"plan":[{"game":13,"bringer":"$p2","wantedBy":["$p1"]}],"missing":[822]}""" to
            GameNightCreated(gameNight, poll, player(p1), "Small night", LocalDate.parse("2026-10-10"), "Tim's", "Europe/Brussels", listOf(player(p1), player(p2)), listOf(PlannedGame(BggId(13), player(p2), listOf(player(p1)))), listOf(BggId(822))),
        """{"type":"GameBrought","gameDay":"$d1","player":"$p1","game":13}""" to
            GameBrought(gameDay, player(p1), BggId(13)),
        """{"type":"GameUnbrought","gameDay":"$d1","player":"$p1","game":13}""" to
            GameUnbrought(gameDay, player(p1), BggId(13)),
        """{"type":"PlayWishChanged","gameDay":"$d1","player":"$p1","game":13,"wantsToPlay":true}""" to
            PlayWishChanged(gameDay, player(p1), BggId(13), true),
        """{"type":"BringRequested","gameDay":"$d1","asker":"$p1","owner":"$p2","game":13}""" to
            BringRequested(gameDay, player(p1), player(p2), BggId(13)),
        """{"type":"BringRequestWithdrawn","gameDay":"$d1","asker":"$p1","owner":"$p2","game":13}""" to
            BringRequestWithdrawn(gameDay, player(p1), player(p2), BggId(13)),
        """{"type":"BringRequestDeclined","gameDay":"$d1","owner":"$p2","game":13,"asker":"$p1"}""" to
            BringRequestDeclined(gameDay, player(p2), BggId(13), player(p1)),
        """{"type":"BringRequestsAllowedChanged","player":"$p1","allowed":false}""" to
            BringRequestsAllowedChanged(player(p1), false),
        """{"type":"ComparisonRecorded","player":"$p1","a":13,"b":822,"result":"BOTH_GREAT","basis":"VIBE","at":"2026-10-03T09:00:00Z"}""" to
            ComparisonRecorded(player(p1), BggId(13), BggId(822), ComparisonResult.BOTH_GREAT, Basis.VIBE, Instant.parse("2026-10-03T09:00:00Z")),
        """{"type":"GamePlayStatusDeclared","player":"$p1","game":13,"played":false}""" to
            GamePlayStatusDeclared(player(p1), BggId(13), false),
        """{"type":"PlayLogged","gameDay":"$d1","game":13,"players":["$p1","$p2"],"minutes":90,"loggedBy":"$p1","at":"2026-10-03T14:00:00Z"}""" to
            PlayLogged(gameDay, BggId(13), listOf(player(p1), player(p2)), 90, player(p1), Instant.parse("2026-10-03T14:00:00Z")),
        """{"type":"GameDayCancelled","gameDay":"$d1"}""" to
            GameDayCancelled(gameDay),
        """{"type":"GameDayEnded","gameDay":"$d1"}""" to
            GameDayEnded(gameDay),
    )

    @Test
    @Obligation("EV-9")
    fun `every stored shape still decodes`() {
        for ((json, expected) in stored) {
            EventStore.json.decodeFromString(Event.serializer(), json) shouldBe expected
        }
    }

    @Test
    @Obligation("EV-10")
    fun `every event type has a stored sample`() {
        val allTypes = Event.serializer().descriptor.getElementDescriptor(1).elementNames.toSet()
        (allTypes - stored.map { (_, event) -> event.type }.toSet()).shouldBeEmpty()
    }
}
