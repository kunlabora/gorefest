package gorefest.core.gameday

import gorefest.contract.Obligation
import gorefest.core.BggId
import gorefest.core.BringRequestDeclined
import gorefest.core.BringRequestWithdrawn
import gorefest.core.BringRequested
import gorefest.core.BringRequestsAllowedChanged
import gorefest.core.Event
import gorefest.core.GameBrought
import gorefest.core.GameDayCancelled
import gorefest.core.GameDayEnded
import gorefest.core.GameUnbrought
import gorefest.core.PlayerErased
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.Rejection
import gorefest.core.eventsourcing.given
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class BringRequestsTest {
    private val caro = PlayerId(Uuid.random())
    private val dirk = PlayerId(Uuid.random())
    private val brass = BggId(224517)
    private val azul = BggId(230802)
    private val catan = BggId(13)
    private val ark = BggId(342942)

    private val joined = arrayOf<Event>(
        gameDayCreated(), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, bram), PlayerJoined(gorefest7, caro), PlayerJoined(gorefest7, dirk),
    )

    private fun ask(asker: PlayerId, owner: PlayerId, game: BggId) = BringRequested(gorefest7, asker, owner, game)

    @Test
    @Obligation("SH-21")
    fun `a joined player asks another joined player to bring a game`() {
        RequestBringDecider.given(*joined).whenever(RequestBring(gorefest7, bram, anna, brass)).thenEmits(ask(bram, anna, brass))
    }

    @Test
    @Obligation("SH-23")
    fun `asking while the same request is open changes nothing`() {
        RequestBringDecider.given(*joined, ask(bram, anna, brass)).whenever(RequestBring(gorefest7, bram, anna, brass)).thenEmitsNothing()
    }

    @Test
    @Obligation("SH-23")
    fun `two askers can ask for the same game`() {
        RequestBringDecider.given(*joined, ask(bram, anna, brass)).whenever(RequestBring(gorefest7, caro, anna, brass)).thenEmits(ask(caro, anna, brass))
    }

    @Test
    @Obligation("SH-23")
    fun `after a decline the same ask is a new request`() {
        RequestBringDecider
            .given(*joined, ask(bram, anna, brass), BringRequestDeclined(gorefest7, anna, brass, bram))
            .whenever(RequestBring(gorefest7, bram, anna, brass))
            .thenEmits(ask(bram, anna, brass))
    }

    @Test
    @Obligation("SH-24")
    fun `the asker and the owner both have to have joined`() {
        RequestBringDecider.given(gameDayCreated(), PlayerJoined(gorefest7, anna)).whenever(RequestBring(gorefest7, bram, anna, brass)).thenRejects(Rejection.NotJoined)
        RequestBringDecider.given(gameDayCreated(), PlayerJoined(gorefest7, bram)).whenever(RequestBring(gorefest7, bram, anna, brass)).thenRejects(Rejection.OwnerNotJoined)
    }

    @Test
    @Obligation("SH-24")
    fun `you can't ask yourself`() {
        RequestBringDecider.given(*joined).whenever(RequestBring(gorefest7, bram, bram, brass)).thenRejects(Rejection.CantAskYourself)
    }

    @Test
    @Obligation("SH-24")
    fun `a game the owner already brings can't be asked for, but one somebody else brings can`() {
        RequestBringDecider.given(*joined, GameBrought(gorefest7, anna, brass)).whenever(RequestBring(gorefest7, bram, anna, brass)).thenRejects(Rejection.AlreadyBringing)
        RequestBringDecider.given(*joined, GameBrought(gorefest7, caro, brass)).whenever(RequestBring(gorefest7, bram, anna, brass)).thenEmits(ask(bram, anna, brass))
        RequestBringDecider
            .given(*joined, GameBrought(gorefest7, anna, brass), GameUnbrought(gorefest7, anna, brass))
            .whenever(RequestBring(gorefest7, bram, anna, brass))
            .thenEmits(ask(bram, anna, brass))
    }

    @Test
    @Obligation("SH-32")
    fun `an owner who turned requests off can't be asked, until they turn them on again`() {
        RequestBringDecider.given(*joined, BringRequestsAllowedChanged(anna, false)).whenever(RequestBring(gorefest7, bram, anna, brass)).thenRejects(Rejection.OwnerNotAcceptingRequests)
        RequestBringDecider
            .given(*joined, BringRequestsAllowedChanged(anna, false), BringRequestsAllowedChanged(anna, true))
            .whenever(RequestBring(gorefest7, bram, anna, brass))
            .thenEmits(ask(bram, anna, brass))
        // Someone else's switch says nothing about the owner.
        RequestBringDecider.given(*joined, BringRequestsAllowedChanged(caro, false)).whenever(RequestBring(gorefest7, bram, anna, brass)).thenEmits(ask(bram, anna, brass))
    }

    @Test
    @Obligation("SH-33")
    fun `only while the game day is on`() {
        RequestBringDecider.given().whenever(RequestBring(gorefest7, bram, anna, brass)).thenRejects(Rejection.NoSuchGameDay)
        RequestBringDecider.given(*joined, GameDayEnded(gorefest7)).whenever(RequestBring(gorefest7, bram, anna, brass)).thenRejects(Rejection.GameDayOver)
        RequestBringDecider.given(*joined, GameDayCancelled(gorefest7)).whenever(RequestBring(gorefest7, bram, anna, brass)).thenRejects(Rejection.GameDayCancelled)
    }

    private val threeAsked = arrayOf(ask(bram, anna, brass), ask(bram, caro, azul), ask(bram, dirk, catan))

    @Test
    @Obligation("SH-25")
    fun `an asker can have three requests standing, answered or declined ones included`() {
        RequestBringDecider.given(*joined, *threeAsked).whenever(RequestBring(gorefest7, bram, anna, ark)).thenRejects(Rejection.TooManyBringRequests)
        RequestBringDecider
            .given(*joined, *threeAsked, GameBrought(gorefest7, anna, brass), BringRequestDeclined(gorefest7, caro, azul, bram))
            .whenever(RequestBring(gorefest7, bram, anna, ark))
            .thenRejects(Rejection.TooManyBringRequests)
    }

    @Test
    @Obligation("SH-25")
    fun `taking a request back gives the asker the slot again`() {
        RequestBringDecider
            .given(*joined, *threeAsked, BringRequestWithdrawn(gorefest7, bram, anna, brass))
            .whenever(RequestBring(gorefest7, bram, anna, ark))
            .thenEmits(ask(bram, anna, ark))
    }

    @Test
    @Obligation("SH-25")
    fun `being asked doesn't use up my own asks`() {
        RequestBringDecider
            .given(*joined, ask(anna, bram, brass), ask(caro, bram, azul), ask(dirk, bram, catan))
            .whenever(RequestBring(gorefest7, bram, anna, ark))
            .thenEmits(ask(bram, anna, ark))
    }

    private val fiveAskedAnna = listOf(brass, azul, catan, ark, BggId(1)).mapIndexed { i, game -> ask(listOf(bram, bram, caro, caro, dirk)[i], anna, game) }.toTypedArray()

    @Test
    @Obligation("SH-26")
    fun `one owner can be asked five times a day`() {
        RequestBringDecider.given(*joined, *fiveAskedAnna).whenever(RequestBring(gorefest7, dirk, anna, BggId(2))).thenRejects(Rejection.OwnerAskedEnough)
    }

    @Test
    @Obligation("SH-26")
    fun `taking a request back lowers the owner's count too`() {
        RequestBringDecider
            .given(*joined, *fiveAskedAnna, BringRequestWithdrawn(gorefest7, dirk, anna, BggId(1)))
            .whenever(RequestBring(gorefest7, dirk, anna, BggId(2)))
            .thenEmits(ask(dirk, anna, BggId(2)))
    }

    @Test
    @Obligation("SH-26")
    fun `the owner's own asks don't count towards how often they were asked`() {
        RequestBringDecider
            .given(*joined, *fiveAskedAnna.take(4).toTypedArray(), ask(anna, bram, BggId(3)))
            .whenever(RequestBring(gorefest7, dirk, anna, BggId(2)))
            .thenEmits(ask(dirk, anna, BggId(2)))
    }

    @Test
    @Obligation("SH-30")
    fun `the asker takes a request back while it is open`() {
        WithdrawBringRequestDecider
            .given(*joined, ask(bram, anna, brass))
            .whenever(WithdrawBringRequest(gorefest7, bram, anna, brass))
            .thenEmits(BringRequestWithdrawn(gorefest7, bram, anna, brass))
    }

    @Test
    @Obligation("SH-30")
    fun `taking back what isn't open changes nothing`() {
        WithdrawBringRequestDecider.given(*joined).whenever(WithdrawBringRequest(gorefest7, bram, anna, brass)).thenEmitsNothing()
        WithdrawBringRequestDecider
            .given(*joined, ask(bram, anna, brass), BringRequestWithdrawn(gorefest7, bram, anna, brass))
            .whenever(WithdrawBringRequest(gorefest7, bram, anna, brass)).thenEmitsNothing()
        WithdrawBringRequestDecider
            .given(*joined, ask(bram, anna, brass), GameBrought(gorefest7, anna, brass))
            .whenever(WithdrawBringRequest(gorefest7, bram, anna, brass)).thenEmitsNothing()
        WithdrawBringRequestDecider
            .given(*joined, ask(bram, anna, brass), BringRequestDeclined(gorefest7, anna, brass, bram))
            .whenever(WithdrawBringRequest(gorefest7, bram, anna, brass)).thenEmitsNothing()
    }

    @Test
    @Obligation("SH-30")
    fun `nobody else can take my request back`() {
        // A request the other way round, or another asker's, isn't mine to withdraw.
        WithdrawBringRequestDecider.given(*joined, ask(bram, anna, brass)).whenever(WithdrawBringRequest(gorefest7, caro, anna, brass)).thenEmitsNothing()
        WithdrawBringRequestDecider.given(*joined, ask(anna, bram, brass)).whenever(WithdrawBringRequest(gorefest7, bram, anna, brass)).thenEmitsNothing()
    }

    @Test
    @Obligation("SH-33")
    fun `withdrawing and declining are over with the game day`() {
        WithdrawBringRequestDecider.given(*joined, ask(bram, anna, brass), GameDayEnded(gorefest7)).whenever(WithdrawBringRequest(gorefest7, bram, anna, brass)).thenRejects(Rejection.GameDayOver)
        DeclineBringRequestDecider.given(*joined, ask(bram, anna, brass), GameDayCancelled(gorefest7)).whenever(DeclineBringRequest(gorefest7, anna, brass, bram)).thenRejects(Rejection.GameDayCancelled)
        DeclineBringRequestDecider.given().whenever(DeclineBringRequest(gorefest7, anna, brass, bram)).thenRejects(Rejection.NoSuchGameDay)
    }

    @Test
    @Obligation("SH-29")
    fun `the owner declines one asker's open request`() {
        DeclineBringRequestDecider
            .given(*joined, ask(bram, anna, brass), ask(caro, anna, brass))
            .whenever(DeclineBringRequest(gorefest7, anna, brass, bram))
            .thenEmits(BringRequestDeclined(gorefest7, anna, brass, bram))
    }

    @Test
    @Obligation("SH-29")
    fun `declining what isn't open, or was never asked of me, changes nothing`() {
        DeclineBringRequestDecider.given(*joined).whenever(DeclineBringRequest(gorefest7, anna, brass, bram)).thenEmitsNothing()
        DeclineBringRequestDecider
            .given(*joined, ask(bram, anna, brass), BringRequestDeclined(gorefest7, anna, brass, bram))
            .whenever(DeclineBringRequest(gorefest7, anna, brass, bram)).thenEmitsNothing()
        DeclineBringRequestDecider
            .given(*joined, ask(bram, anna, brass), GameBrought(gorefest7, anna, brass))
            .whenever(DeclineBringRequest(gorefest7, anna, brass, bram)).thenEmitsNothing()
        DeclineBringRequestDecider
            .given(*joined, ask(bram, caro, brass))
            .whenever(DeclineBringRequest(gorefest7, anna, brass, bram)).thenEmitsNothing()
    }

    @Test
    @Obligation("SH-32")
    fun `the switch only emits when it changes something`() {
        SetBringRequestsAllowedDecider.given().whenever(SetBringRequestsAllowed(anna, true)).thenEmitsNothing()
        SetBringRequestsAllowedDecider.given().whenever(SetBringRequestsAllowed(anna, false)).thenEmits(BringRequestsAllowedChanged(anna, false))
        SetBringRequestsAllowedDecider.given(BringRequestsAllowedChanged(anna, false)).whenever(SetBringRequestsAllowed(anna, false)).thenEmitsNothing()
        SetBringRequestsAllowedDecider.given(BringRequestsAllowedChanged(anna, false)).whenever(SetBringRequestsAllowed(anna, true)).thenEmits(BringRequestsAllowedChanged(anna, true))
        // Another player's switch is outside the boundary.
        SetBringRequestsAllowedDecider.given(BringRequestsAllowedChanged(bram, false)).whenever(SetBringRequestsAllowed(anna, false)).thenEmits(BringRequestsAllowedChanged(anna, false))
    }

    // The read model

    private fun project(vararg events: Event) = events.fold(BringRequests.initialState, BringRequests::evolve)

    @Test
    @Obligation("SH-28", "SH-30")
    fun `asked requests are open for the owner and the asker`() {
        val requests = project(ask(bram, anna, brass), ask(caro, anna, azul), ask(bram, caro, catan))

        requests.openFor(anna, gorefest7).map { it.game } shouldBe listOf(brass, azul)
        requests.openBy(bram, gorefest7).map { it.game } shouldBe listOf(brass, catan)
        requests.count(gorefest7) shouldBe 3
        requests.isOpen(gorefest7, bram, anna, brass) shouldBe true
        requests.isOpen(gorefest7, anna, bram, brass) shouldBe false
        requests.count(gorefest7.let { gameDayOther }) shouldBe 0
    }

    private val gameDayOther = gorefest7.let { gorefest.core.GameDayId(Uuid.random()) }

    @Test
    @Obligation("SH-28")
    fun `ticking the game anywhere answers every open request for it, and only for that owner`() {
        val requests = project(ask(bram, anna, brass), ask(caro, anna, brass), ask(dirk, caro, brass), GameBrought(gorefest7, anna, brass))

        requests.on(gorefest7).map { it.asker to it.status } shouldBe listOf(
            bram to RequestStatus.ANSWERED, caro to RequestStatus.ANSWERED, dirk to RequestStatus.OPEN,
        )
        requests.openFor(anna, gorefest7) shouldBe emptyList()
        requests.count(gorefest7) shouldBe 1
    }

    @Test
    @Obligation("SH-30")
    fun `a declined request stays visible to the asker, a withdrawn one is gone`() {
        val requests = project(
            ask(bram, anna, brass), ask(bram, caro, azul), BringRequestDeclined(gorefest7, anna, brass, bram), BringRequestWithdrawn(gorefest7, bram, caro, azul),
        )

        requests.by(bram, gorefest7).map { it.game to it.status } shouldBe listOf(brass to RequestStatus.DECLINED)
        requests.count(gorefest7) shouldBe 0
    }

    @Test
    @Obligation("SH-33")
    fun `requests stay in place when the game day ends`() {
        project(ask(bram, anna, brass), GameDayEnded(gorefest7)).openFor(anna, gorefest7).size shouldBe 1
    }

    @Test
    @Obligation("SH-32")
    fun `players accept requests until they say otherwise`() {
        val off = project(BringRequestsAllowedChanged(anna, false))

        off.accepts(anna) shouldBe false
        off.accepts(bram) shouldBe true
        BringRequests.evolve(off, BringRequestsAllowedChanged(anna, true)).accepts(anna) shouldBe true
    }

    // Erasure

    @Test
    @Obligation("SH-34")
    fun `an erased asker's requests stop counting against the owner`() {
        RequestBringDecider
            .given(*joined, *fiveAskedAnna, PlayerErased(caro))
            .whenever(RequestBring(gorefest7, dirk, anna, BggId(2)))
            .thenEmits(ask(dirk, anna, BggId(2)))
    }

    @Test
    @Obligation("SH-34")
    fun `an erased owner can't be asked`() {
        RequestBringDecider.given(*joined, PlayerErased(anna)).whenever(RequestBring(gorefest7, bram, anna, brass)).thenRejects(Rejection.OwnerNotJoined)
    }

    @Test
    @Obligation("SH-34", "PR-7")
    fun `an erased player's requests, both ways, leave a game day still to be played`() {
        val requests = project(ask(bram, anna, brass), ask(caro, bram, azul), ask(caro, anna, catan), PlayerErased(bram))

        requests.on(gorefest7).map { Triple(it.asker, it.owner, it.game) } shouldBe listOf(Triple(caro, anna, catan))
        requests.count(gorefest7) shouldBe 1
    }

    @Test
    @Obligation("SH-34", "PR-7")
    fun `a closed game day keeps an erased player's requests as history`() {
        project(ask(bram, anna, brass), GameDayEnded(gorefest7), PlayerErased(bram)).on(gorefest7).map { it.game } shouldBe listOf(brass)
        project(ask(bram, anna, brass), GameDayCancelled(gorefest7), PlayerErased(bram)).on(gorefest7).map { it.game } shouldBe listOf(brass)
    }
}
