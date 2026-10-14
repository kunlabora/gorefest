package gorefest.core.datepoll

import gorefest.core.BggId
import gorefest.core.DatePollAnswered
import gorefest.core.DatePollBringAnswered
import gorefest.core.DatePollCreated
import gorefest.core.DatePollGameAnswered
import gorefest.core.DatePollGameProposed
import gorefest.core.DatePollGameWithdrawn
import gorefest.core.DatePollId
import gorefest.core.DatePollJoined
import gorefest.core.DatePollPickedForGameNight
import gorefest.core.Event
import gorefest.core.GameDayId
import gorefest.core.GameNightCreated
import gorefest.core.GameNightId
import gorefest.core.PlannedGame
import gorefest.core.PlayerErased
import gorefest.core.PlayerId
import gorefest.core.Rejection
import gorefest.core.eventsourcing.given
import gorefest.core.gameday.anna
import gorefest.core.gameday.bram
import gorefest.core.gamenight.GameNights
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.uuid.Uuid

/** U11: a date poll that asks about games too, and the game night its pick makes. */
class PollGamesTest {
    private val poll = DatePollId(Uuid.random())
    private val ruben = PlayerId(Uuid.random())
    private val kim = PlayerId(Uuid.random())
    private val today = LocalDate.of(2026, 10, 1)
    private val fri = LocalDate.of(2026, 10, 9)
    private val sat = LocalDate.of(2026, 10, 10)
    private val brass = BggId(224517)
    private val humanity = BggId(1001)
    private val azul = BggId(230802)
    private val catan = BggId(13)

    private fun created(games: Boolean = true) = DatePollCreated(poll, anna, "Small night", "Anna's", listOf(fri, sat), "Europe/Brussels", asksAboutGames = games)
    private val open = arrayOf<Event>(created(), DatePollJoined(poll, anna), DatePollJoined(poll, bram), DatePollJoined(poll, ruben))
    private val withGames = arrayOf(*open, DatePollGameProposed(poll, brass), DatePollGameProposed(poll, humanity), DatePollGameProposed(poll, azul))

    // Proposing

    @Test
    fun `the host proposes a game and takes it back`() {
        ProposePollGameDecider.given(*open).whenever(ProposePollGame(poll, anna, humanity)).thenEmits(DatePollGameProposed(poll, humanity))
        ProposePollGameDecider.given(*withGames).whenever(ProposePollGame(poll, anna, humanity, proposed = false)).thenEmits(DatePollGameWithdrawn(poll, humanity))
        ProposePollGameDecider.given(*withGames).whenever(ProposePollGame(poll, anna, humanity)).thenEmitsNothing()
    }

    @Test
    fun `only the host, only in a poll that asks about games, only while it's open, and not too many`() {
        ProposePollGameDecider.given(*open).whenever(ProposePollGame(poll, bram, humanity)).thenRejects(Rejection.OnlyHostProposesGames)
        ProposePollGameDecider.given(created(games = false)).whenever(ProposePollGame(poll, anna, humanity)).thenRejects(Rejection.PollAsksNoGames)
        ProposePollGameDecider.given(*open, DatePollPickedForGameNight(poll, sat, GameNightId(Uuid.random()))).whenever(ProposePollGame(poll, anna, humanity))
            .thenRejects(Rejection.DatePollClosed)
        val full = (1..MAX_POLL_GAMES).map { DatePollGameProposed(poll, BggId(it)) }.toTypedArray()
        ProposePollGameDecider.given(*open, *full).whenever(ProposePollGame(poll, anna, humanity)).thenRejects(Rejection.TooManyPollGames)
    }

    @Test
    fun `games ticked on the form go into the poll with it, but only when it asks about games`() {
        val create = CreateDatePoll(poll, anna, "Small night", "Anna's", listOf(fri, sat), "Europe/Brussels", today, asksAboutGames = true, games = listOf(brass, humanity, brass))
        CreateDatePollDecider.given().whenever(create).thenEmits(created(), DatePollJoined(poll, anna), DatePollGameProposed(poll, brass), DatePollGameProposed(poll, humanity))
        CreateDatePollDecider.given().whenever(create.copy(asksAboutGames = false)).thenEmits(created(games = false), DatePollJoined(poll, anna))
        CreateDatePollDecider.given().whenever(create.copy(games = (1..MAX_POLL_GAMES + 1).map { BggId(it) })).thenRejects(Rejection.TooManyPollGames)
    }

    // Answering

    @Test
    fun `a joined player says yes or no per game, and can change it`() {
        AnswerPollGameDecider.given(*withGames).whenever(AnswerPollGame(poll, bram, brass, yes = true)).thenEmits(DatePollGameAnswered(poll, bram, brass, true))
        AnswerPollGameDecider.given(*withGames, DatePollGameAnswered(poll, bram, brass, true)).whenever(AnswerPollGame(poll, bram, brass, yes = false))
            .thenEmits(DatePollGameAnswered(poll, bram, brass, false))
        AnswerPollGameDecider.given(*withGames, DatePollGameAnswered(poll, bram, brass, true)).whenever(AnswerPollGame(poll, bram, brass, yes = true)).thenEmitsNothing()
    }

    @Test
    fun `a yes is a wish, three at most, and a game taken off frees its yes`() {
        val three = arrayOf(*withGames, DatePollGameProposed(poll, catan), DatePollGameAnswered(poll, bram, brass, true), DatePollGameAnswered(poll, bram, humanity, true), DatePollGameAnswered(poll, bram, azul, true))
        AnswerPollGameDecider.given(*three).whenever(AnswerPollGame(poll, bram, catan, yes = true)).thenRejects(Rejection.TooManyPollYeses)
        AnswerPollGameDecider.given(*three).whenever(AnswerPollGame(poll, bram, catan, yes = false)).thenEmits(DatePollGameAnswered(poll, bram, catan, false))
        AnswerPollGameDecider.given(*three, DatePollGameWithdrawn(poll, azul)).whenever(AnswerPollGame(poll, bram, catan, yes = true))
            .thenEmits(DatePollGameAnswered(poll, bram, catan, true))
    }

    @Test
    fun `only joined players answer, only about the poll's games`() {
        AnswerPollGameDecider.given(*withGames).whenever(AnswerPollGame(poll, kim, brass, yes = true)).thenRejects(Rejection.NotInDatePoll)
        AnswerPollGameDecider.given(*withGames).whenever(AnswerPollGame(poll, bram, catan, yes = true)).thenRejects(Rejection.NotInPoll)
        AnswerBringDecider.given(*withGames, DatePollGameWithdrawn(poll, humanity)).whenever(AnswerBring(poll, ruben, humanity, true)).thenRejects(Rejection.NotInPoll)
    }

    @Test
    fun `anyone in the poll offers to bring a game, or says not this time`() {
        AnswerBringDecider.given(*withGames).whenever(AnswerBring(poll, ruben, humanity, true)).thenEmits(DatePollBringAnswered(poll, ruben, humanity, true))
        AnswerBringDecider.given(*withGames).whenever(AnswerBring(poll, ruben, humanity, false)).thenEmits(DatePollBringAnswered(poll, ruben, humanity, false))
        AnswerBringDecider.given(*withGames, DatePollBringAnswered(poll, ruben, humanity, true)).whenever(AnswerBring(poll, ruben, humanity, true)).thenEmitsNothing()
    }

    @Test
    fun `answers about games don't conflict with each other`() {
        val bramsQuery = AnswerPollGameDecider.query(AnswerPollGame(poll, bram, brass, true))
        val rubens = DatePollGameAnswered(poll, ruben, brass, true)
        bramsQuery.matches("DatePollGameAnswered", rubens.tags) shouldBe false
    }

    // The read model: who brings what on which date

    private fun summary(vararg events: Event) = events.fold(DatePolls.initialState, DatePolls::evolve).of(poll)!!

    private val answered = arrayOf(
        *withGames,
        DatePollAnswered(poll, anna, listOf(fri, sat)), DatePollAnswered(poll, bram, listOf(fri, sat)), DatePollAnswered(poll, ruben, listOf(sat)),
        DatePollGameAnswered(poll, bram, brass, true), DatePollGameAnswered(poll, ruben, humanity, true), DatePollGameAnswered(poll, bram, humanity, true),
        DatePollGameAnswered(poll, ruben, azul, false),
        DatePollBringAnswered(poll, ruben, humanity, true), DatePollBringAnswered(poll, anna, brass, true),
    )

    @Test
    fun `a game is missing on a date when nobody who offered to bring it can come`() {
        val poll = summary(*answered)

        poll.planFor(sat) shouldBe NightPlan(
            plan = listOf(PlannedGame(brass, anna, listOf(bram)), PlannedGame(humanity, ruben, listOf(bram, ruben))),
            missing = listOf(azul),
        )
        // Ruben can't come on Friday, so Humanity has nobody to bring it.
        poll.planFor(fri).missing shouldBe listOf(humanity, azul)
        poll.noFor(azul) shouldBe listOf(ruben)
    }

    @Test
    fun `the first one who offered and can come brings it, and taking it back hands it on`() {
        val both = summary(*answered, DatePollBringAnswered(poll, bram, humanity, true))
        both.bringers(humanity) shouldBe listOf(ruben, bram)
        both.planFor(fri).plan.single { it.game == humanity }.bringer shouldBe bram

        val rubenOut = summary(*answered, DatePollBringAnswered(poll, bram, humanity, true), DatePollBringAnswered(poll, ruben, humanity, false))
        rubenOut.bringers(humanity) shouldBe listOf(bram)
        rubenOut.bringUnanswered(ruben, humanity) shouldBe false
        rubenOut.bringUnanswered(bram, azul) shouldBe true
    }

    @Test
    fun `a game taken off the poll takes its answers and offers with it`() {
        val poll = summary(*answered, DatePollGameWithdrawn(poll, humanity))
        poll.proposals shouldBe listOf(brass, azul)
        poll.yesFor(humanity) shouldBe emptyList()
        poll.bringers(humanity) shouldBe emptyList()
        poll.yesCount(bram) shouldBe 1
    }

    // The pick

    private val night = GameNightId(Uuid.random())
    private val pick = PickDate(poll, anna, sat, GameDayId(Uuid.random()), today, night)

    @Test
    fun `picking a date in a poll with games makes a game night with its plan`() {
        PickDateDecider.given(*answered).whenever(pick).thenEmits(
            DatePollPickedForGameNight(poll, sat, night),
            GameNightCreated(
                night, poll, anna, "Small night", sat, "Anna's", "Europe/Brussels",
                players = listOf(anna, bram, ruben),
                plan = listOf(PlannedGame(brass, anna, listOf(bram)), PlannedGame(humanity, ruben, listOf(bram, ruben))),
                missing = listOf(azul),
            ),
        )
    }

    @Test
    fun `a late offer is inside the pick's boundary`() {
        PickDateDecider.query(pick).matches("DatePollBringAnswered", DatePollBringAnswered(poll, kim, azul, true).tags) shouldBe true
        PickDateDecider.query(pick).matches("DatePollGameAnswered", DatePollGameAnswered(poll, kim, azul, true).tags) shouldBe true
    }

    @Test
    fun `a picked poll takes no more answers about games`() {
        val picked = arrayOf(*answered, DatePollPickedForGameNight(poll, sat, night))
        AnswerPollGameDecider.given(*picked).whenever(AnswerPollGame(poll, bram, azul, true)).thenRejects(Rejection.DatePollClosed)
        AnswerBringDecider.given(*picked).whenever(AnswerBring(poll, bram, azul, true)).thenRejects(Rejection.DatePollClosed)
        AnswerDatePollDecider.given(*picked).whenever(AnswerDatePoll(poll, bram, setOf(fri))).thenRejects(Rejection.DatePollClosed)
        JoinDatePollDecider.given(*picked).whenever(JoinDatePoll(poll, kim)).thenRejects(Rejection.DatePollClosed)
        summary(*picked).picked shouldBe PickedDate(sat, gameNight = night)
    }

    // The game night

    @Test
    fun `an erased player leaves the game night, and a game they were to bring goes missing`() {
        val created = GameNightCreated(
            night, poll, anna, "Small night", sat, "Anna's", "Europe/Brussels", listOf(anna, bram, ruben),
            listOf(PlannedGame(brass, anna, listOf(bram)), PlannedGame(humanity, ruben, listOf(bram, ruben))), listOf(azul),
        )
        val board = listOf(created, PlayerErased(ruben)).fold(GameNights.initialState, GameNights::evolve)

        board.of(night)!!.players shouldBe listOf(anna, bram)
        board.of(night)!!.plan shouldBe listOf(PlannedGame(brass, anna, listOf(bram)))
        board.of(night)!!.missing shouldBe listOf(azul, humanity)
        board.forPlayer(ruben) shouldBe emptyList()
    }

    // Erasure before the pick (BUG-027, BUG-028)

    @Test
    fun `someone erased before the pick isn't on the game night, and their yeses and offers go with them`() {
        PickDateDecider.given(*answered, PlayerErased(ruben)).whenever(pick).thenEmits(
            DatePollPickedForGameNight(poll, sat, night),
            GameNightCreated(
                night, poll, anna, "Small night", sat, "Anna's", "Europe/Brussels",
                players = listOf(anna, bram),
                plan = listOf(PlannedGame(brass, anna, listOf(bram))),
                missing = listOf(humanity, azul),
            ),
        )
        summary(*answered, PlayerErased(ruben)).let {
            it.participants shouldBe listOf(anna, bram)
            it.yesFor(humanity) shouldBe listOf(bram)
            it.noFor(azul) shouldBe emptyList()
        }
    }

    @Test
    fun `someone erased before the pick of a poll without games isn't on the game day`() {
        val plain = arrayOf<Event>(created(games = false), DatePollJoined(poll, anna), DatePollJoined(poll, bram), DatePollAnswered(poll, bram, listOf(sat)))
        val day = GameDayId(Uuid.random())
        PickDateDecider.given(*plain, PlayerErased(bram)).whenever(PickDate(poll, anna, sat, day, today)).thenEmits(
            gorefest.core.DatePollClosed(poll, sat, day),
            gorefest.core.GameDayCreated(day, anna, "Small night", sat, "Anna's", "Europe/Brussels"),
            gorefest.core.PlayerJoined(day, anna),
        )
    }

    @Test
    fun `a poll whose host was erased is over, with no joining, answering, offering or picking`() {
        val gone = arrayOf(*answered, PlayerErased(anna))
        JoinDatePollDecider.given(*gone).whenever(JoinDatePoll(poll, kim)).thenRejects(Rejection.DatePollHostGone)
        AnswerPollGameDecider.given(*gone).whenever(AnswerPollGame(poll, bram, azul, true)).thenRejects(Rejection.DatePollHostGone)
        AnswerBringDecider.given(*gone).whenever(AnswerBring(poll, bram, azul, true)).thenRejects(Rejection.DatePollHostGone)
        PickDateDecider.given(*gone).whenever(pick).thenRejects(Rejection.DatePollHostGone)
        summary(*gone).open shouldBe false
        DatePolls.initialState.let { gone.fold(it, DatePolls::evolve) }.forPlayer(bram).single().hostGone shouldBe true
    }

    @Test
    fun `the plan remembers who else coming offered, and an erased bringer hands the game on`() {
        val both = arrayOf(*answered, DatePollBringAnswered(poll, bram, humanity, true))
        PickDateDecider.given(*both).whenever(pick).thenEmits(
            DatePollPickedForGameNight(poll, sat, night),
            GameNightCreated(
                night, poll, anna, "Small night", sat, "Anna's", "Europe/Brussels",
                players = listOf(anna, bram, ruben),
                plan = listOf(PlannedGame(brass, anna, listOf(bram)), PlannedGame(humanity, ruben, listOf(bram, ruben), others = listOf(bram))),
                missing = listOf(azul),
            ),
        )
        val created = GameNightCreated(
            night, poll, anna, "Small night", sat, "Anna's", "Europe/Brussels", listOf(anna, bram, ruben),
            listOf(PlannedGame(humanity, ruben, listOf(bram, ruben), others = listOf(bram))),
        )
        val board = listOf(created, PlayerErased(ruben)).fold(GameNights.initialState, GameNights::evolve)
        board.of(night)!!.plan shouldBe listOf(PlannedGame(humanity, bram, listOf(bram)))
        board.of(night)!!.missing shouldBe emptyList()
    }
}

