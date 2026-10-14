package gorefest.shell.account

import gorefest.core.Basis
import gorefest.core.BggId
import gorefest.core.BringRequested
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.GameBrought
import gorefest.core.GameDayCreated
import gorefest.core.GameDayId
import gorefest.core.PlayLogged
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.PlayerRegistered
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid

class PersonalDataTest {
    private val kim = PlayerId(Uuid.random())
    private val anna = PlayerId(Uuid.random())
    private val day = GameDayId(Uuid.random())
    private val azul = BggId(1)
    private val brass = BggId(2)
    private val names = mapOf(azul to "Azul", brass to "Brass")
    private val lookups = Lookups({ names.getValue(it) }, { "Gorefest #7" }, { "Boardfest #8" })
    private val profile = PersonalProfile("Kim", "kim@example.com", Instant.parse("2026-10-01T10:00:00Z"), "KimPlays", listOf("Azul"))

    private val events = listOf(
        PlayerRegistered(kim), PlayerRegistered(anna),
        GameDayCreated(day, anna, "Anna's day", LocalDate.parse("2026-10-20"), "Anna's home address", "Europe/Brussels"),
        PlayerJoined(day, anna), PlayerJoined(day, kim),
        GameBrought(day, kim, azul), GameBrought(day, anna, brass),
        BringRequested(day, asker = anna, owner = kim, game = azul),
        ComparisonRecorded(kim, azul, brass, ComparisonResult.A, Basis.VIBE, Instant.parse("2026-10-02T10:00:00Z")),
        PlayLogged(day, azul, listOf(kim, anna), 60, anna, Instant.parse("2026-10-20T20:00:00Z")),
    )

    private val file get() = PersonalData.of(kim, profile, events, lookups)

    @Test
    fun `it holds the profile, the collection and what they did, in words`() {
        val about = file["about"]!!.jsonObject
        about["name"]!!.jsonPrimitive.content shouldBe "Kim"
        about["email"]!!.jsonPrimitive.content shouldBe "kim@example.com"
        about["bggUsername"]!!.jsonPrimitive.content shouldBe "KimPlays"
        file["collection"]!!.jsonArray.map { it.jsonPrimitive.content } shouldBe listOf("Azul")

        val whats = file["history"]!!.jsonArray.map { it.jsonObject["what"]!!.jsonPrimitive.content }
        whats shouldBe listOf(
            "Signed up", "Joined a game day", "Said you bring a game", "Someone asked you to bring a game", "Compared two games", "Played a game",
        )
        file.toString() shouldContain "Gorefest #7"
        file.toString() shouldContain "Azul"
    }

    @Test
    fun `it never holds another person's id or things only they chose to share`() {
        val text = file.toString()

        text shouldNotContain anna.toString()
        text shouldNotContain "Anna's home address"
        text shouldNotContain "Anna's day"
        file["history"]!!.jsonArray.last().jsonObject["peopleAtTheTable"]!!.jsonPrimitive.content shouldBe "2"
    }

    @Test
    fun `a game night and a poll's games hold only your own part`() {
        val poll = gorefest.core.DatePollId(Uuid.random())
        val night = gorefest.core.GameNightId(Uuid.random())
        val nightEvents = listOf(
            gorefest.core.DatePollGameAnswered(poll, kim, azul, true),
            gorefest.core.DatePollBringAnswered(poll, kim, brass, true),
            gorefest.core.GameNightCreated(
                night, poll, anna, "Small night", LocalDate.parse("2026-10-24"), "Anna's home address", "Europe/Brussels", listOf(anna, kim),
                listOf(gorefest.core.PlannedGame(azul, anna, listOf(kim, anna)), gorefest.core.PlannedGame(brass, kim, listOf(anna))),
            ),
        )

        val text = PersonalData.of(kim, profile, nightEvents, lookups)
        val history = text["history"]!!.jsonArray.map { it.jsonObject }

        history.map { it["what"]!!.jsonPrimitive.content } shouldBe listOf("Said you'd play a game in a date poll", "Offered to bring a game", "Were on a game night")
        history.last()["youBring"]!!.jsonPrimitive.content shouldBe "Brass"
        history.last()["youWanted"]!!.jsonPrimitive.content shouldBe "Azul"
        text.toString() shouldNotContain anna.toString()
        text.toString() shouldNotContain "Anna's home address"
    }
}

