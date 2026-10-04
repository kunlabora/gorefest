package gorefest.core.datepoll

import gorefest.core.DatePollAnswered
import gorefest.core.DatePollClosed
import gorefest.core.DatePollCreated
import gorefest.core.DatePollId
import gorefest.core.DatePollJoined
import gorefest.core.GameDayCreated
import gorefest.core.GameDayId
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.Rejection
import gorefest.core.eventsourcing.Decision
import gorefest.core.eventsourcing.given
import gorefest.core.gameday.anna
import gorefest.core.gameday.bram
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.uuid.Uuid

private val poll = DatePollId(Uuid.random())
private val kim = PlayerId(Uuid.random())
private val today = LocalDate.of(2026, 10, 1)
private val fri = LocalDate.of(2026, 10, 9)
private val sat = LocalDate.of(2026, 10, 10)
private val sun = LocalDate.of(2026, 10, 11)

private fun created(host: PlayerId = anna, dates: List<LocalDate> = listOf(fri, sat, sun)) =
    DatePollCreated(poll, host, "Gorefest #8", "Anna's place", dates, "Europe/Brussels")

private val open = arrayOf(created(), DatePollJoined(poll, anna))

class CreateDatePollTest {
    private val create = CreateDatePoll(poll, anna, " Gorefest #8 ", " Anna's place ", listOf(sun, fri, sat, fri), "Europe/Brussels", today)

    @Test
    fun `a poll starts with the host in it, dates tidied up`() {
        CreateDatePollDecider.given().whenever(create).thenEmits(created(), DatePollJoined(poll, anna))
    }

    @Test
    fun `a poll needs a title, two to fourteen distinct dates in the future, and a real time zone`() {
        CreateDatePollDecider.given().whenever(create.copy(title = " ")).thenRejects(Rejection.TitleMissing)
        CreateDatePollDecider.given().whenever(create.copy(dates = listOf(fri, fri))).thenRejects(Rejection.TooFewDates)
        CreateDatePollDecider.given().whenever(create.copy(dates = List(MAX_POLL_DATES + 1) { fri.plusDays(it.toLong()) })).thenRejects(Rejection.TooManyDates)
        CreateDatePollDecider.given().whenever(create.copy(dates = listOf(today.minusDays(1), fri))).thenRejects(Rejection.DateInPast)
        CreateDatePollDecider.given().whenever(create.copy(timeZone = "Mars/Olympus")).thenRejects(Rejection.UnknownTimeZone)
    }

    @Test
    fun `today is a fine date`() {
        CreateDatePollDecider.given().whenever(create.copy(dates = listOf(today, fri))).thenEmits(created(dates = listOf(today, fri)), DatePollJoined(poll, anna))
    }

    @Test
    fun `the same poll id can't be created twice`() {
        CreateDatePollDecider.given(created()).whenever(create).thenRejects(Rejection.DatePollAlreadyExists)
    }
}

class JoinDatePollTest {
    @Test
    fun `a player joins an open poll once`() {
        JoinDatePollDecider.given(*open).whenever(JoinDatePoll(poll, bram)).thenEmits(DatePollJoined(poll, bram))
        JoinDatePollDecider.given(*open, DatePollJoined(poll, bram)).whenever(JoinDatePoll(poll, bram)).thenEmitsNothing()
    }

    @Test
    fun `unknown and closed polls refuse`() {
        JoinDatePollDecider.given().whenever(JoinDatePoll(poll, bram)).thenRejects(Rejection.NoSuchDatePoll)
        JoinDatePollDecider.given(*open, DatePollClosed(poll, fri, GameDayId(Uuid.random()))).whenever(JoinDatePoll(poll, bram)).thenRejects(Rejection.DatePollClosed)
    }
}

class AnswerDatePollTest {
    private val joined = arrayOf(*open, DatePollJoined(poll, bram))

    @Test
    fun `a player says which dates they can make`() {
        AnswerDatePollDecider.given(*joined).whenever(AnswerDatePoll(poll, bram, setOf(sun, fri))).thenEmits(DatePollAnswered(poll, bram, listOf(fri, sun)))
    }

    @Test
    fun `answering nothing is an answer, and the same answer again changes nothing`() {
        AnswerDatePollDecider.given(*joined).whenever(AnswerDatePoll(poll, bram, emptySet())).thenEmits(DatePollAnswered(poll, bram, emptyList()))
        AnswerDatePollDecider.given(*joined, DatePollAnswered(poll, bram, listOf(fri)))
            .whenever(AnswerDatePoll(poll, bram, setOf(fri))).thenEmitsNothing()
        AnswerDatePollDecider.given(*joined, DatePollAnswered(poll, bram, listOf(fri)))
            .whenever(AnswerDatePoll(poll, bram, setOf(sat))).thenEmits(DatePollAnswered(poll, bram, listOf(sat)))
    }

    @Test
    fun `only joined players answer, and only with the poll's dates`() {
        AnswerDatePollDecider.given(*open).whenever(AnswerDatePoll(poll, bram, setOf(fri))).thenRejects(Rejection.NotInDatePoll)
        AnswerDatePollDecider.given(*joined).whenever(AnswerDatePoll(poll, bram, setOf(today))).thenRejects(Rejection.NotAnOption)
        AnswerDatePollDecider.given().whenever(AnswerDatePoll(poll, bram, setOf(fri))).thenRejects(Rejection.NoSuchDatePoll)
        AnswerDatePollDecider.given(*joined, DatePollClosed(poll, fri, GameDayId(Uuid.random()))).whenever(AnswerDatePoll(poll, bram, setOf(fri))).thenRejects(Rejection.DatePollClosed)
    }

    @Test
    fun `someone else's answer is outside the boundary`() {
        // Anna's answer must not stop Bram answering the same dates (nor count as his previous one).
        AnswerDatePollDecider.given(*joined, DatePollAnswered(poll, anna, listOf(fri)))
            .whenever(AnswerDatePoll(poll, bram, setOf(fri))).thenEmits(DatePollAnswered(poll, bram, listOf(fri)))
    }
}

class PickDateTest {
    private val gameDay = GameDayId(Uuid.random())
    private val pick = PickDate(poll, anna, sat, gameDay, today)
    private val answered = arrayOf(
        *open, DatePollJoined(poll, bram), DatePollJoined(poll, kim),
        DatePollAnswered(poll, anna, listOf(sat)), DatePollAnswered(poll, bram, listOf(fri, sat)), DatePollAnswered(poll, kim, listOf(fri)),
    )

    @Test
    fun `picking a date closes the poll and creates the game day with everyone who can make it`() {
        PickDateDecider.given(*answered).whenever(pick).thenEmits(
            DatePollClosed(poll, sat, gameDay),
            GameDayCreated(gameDay, anna, "Gorefest #8", sat, "Anna's place", "Europe/Brussels"),
            PlayerJoined(gameDay, anna),
            PlayerJoined(gameDay, bram),
        )
    }

    @Test
    fun `a later answer replaces an earlier one when deciding who comes`() {
        PickDateDecider.given(*answered, DatePollAnswered(poll, bram, listOf(sun))).whenever(pick).thenEmits(
            DatePollClosed(poll, sat, gameDay),
            GameDayCreated(gameDay, anna, "Gorefest #8", sat, "Anna's place", "Europe/Brussels"),
            PlayerJoined(gameDay, anna),
        )
    }

    @Test
    fun `only the host picks, from the options, not from the past, and only once`() {
        PickDateDecider.given(*answered).whenever(pick.copy(host = bram)).thenRejects(Rejection.OnlyHostCanPickDate)
        PickDateDecider.given(*answered).whenever(pick.copy(date = LocalDate.of(2026, 11, 1))).thenRejects(Rejection.NotAnOption)
        PickDateDecider.given(*answered).whenever(pick.copy(today = sun)).thenRejects(Rejection.DateInPast)
        PickDateDecider.given(*answered, DatePollClosed(poll, fri, GameDayId(Uuid.random()))).whenever(pick).thenRejects(Rejection.DatePollClosed)
        PickDateDecider.given().whenever(pick).thenRejects(Rejection.NoSuchDatePoll)
    }

    @Test
    fun `an answer after the pick was read conflicts, because it is inside the boundary`() {
        val query = PickDateDecider.query(pick)
        query.matches("DatePollAnswered", DatePollAnswered(poll, kim, listOf(sat)).tags) shouldBe true
        // ...but who merely joined is not.
        query.matches("DatePollJoined", DatePollJoined(poll, kim).tags) shouldBe false
    }
}

class DatePollBoardTest {
    private fun board(vararg events: gorefest.core.Event) = events.fold(DatePolls.initialState, DatePolls::evolve)

    @Test
    fun `the best date is the one most people can make, ties to the earliest, none while nobody can`() {
        val joins = arrayOf(created(), DatePollJoined(poll, anna), DatePollJoined(poll, bram), DatePollJoined(poll, kim))
        board(*joins).of(poll)!!.best() shouldBe null

        val summary = board(*joins, DatePollAnswered(poll, anna, listOf(sat, sun)), DatePollAnswered(poll, bram, listOf(fri, sun)), DatePollAnswered(poll, kim, listOf(sun, sat))).of(poll)!!
        summary.best() shouldBe sun
        summary.tallies().map { it.date to it.available } shouldBe listOf(fri to listOf(bram), sat to listOf(anna, kim), sun to listOf(anna, bram, kim))

        val tie = board(*joins, DatePollAnswered(poll, anna, listOf(sat)), DatePollAnswered(poll, bram, listOf(fri))).of(poll)!!
        tie.best() shouldBe fri
        tie.waitingOn shouldBe listOf(kim)
    }

    @Test
    fun `a closed poll remembers its pick, and lists put open polls first`() {
        val other = DatePollId(Uuid.random())
        val gameDay = GameDayId(Uuid.random())
        val all = board(
            *open, created().copy(poll = other), DatePollJoined(other, anna), DatePollClosed(poll, sat, gameDay),
        )
        all.of(poll)!!.picked shouldBe PickedDate(sat, gameDay)
        all.forPlayer(anna).map { it.id } shouldBe listOf(other, poll)
        all.forPlayer(bram) shouldBe emptyList()
    }

    @Test
    fun `answers and joins for an unknown poll are ignored`() {
        board(DatePollJoined(poll, anna), DatePollAnswered(poll, anna, listOf(fri))).of(poll) shouldBe null
    }
}
