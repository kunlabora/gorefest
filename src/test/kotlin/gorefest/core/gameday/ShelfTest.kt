package gorefest.core.gameday

import gorefest.core.Rejection
import gorefest.core.BggId
import gorefest.core.Event
import gorefest.core.GameBrought
import gorefest.core.GameDayCancelled
import gorefest.core.GameDayEnded
import gorefest.core.GameUnbrought
import gorefest.core.PlayWishChanged
import gorefest.core.PlayerErased
import gorefest.core.PlayerJoined
import gorefest.core.eventsourcing.given
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ShelfTest {
    private val brass = BggId(224517)
    private val azul = BggId(230802)
    private val joined = arrayOf<Event>(gameDayCreated(), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, bram))

    @Test
    fun `a joined player brings and unbrings a game`() {
        BringGameDecider.given(*joined).whenever(BringGame(gorefest7, bram, brass, true)).thenEmits(GameBrought(gorefest7, bram, brass))
        BringGameDecider
            .given(*joined, GameBrought(gorefest7, bram, brass))
            .whenever(BringGame(gorefest7, bram, brass, false))
            .thenEmits(GameUnbrought(gorefest7, bram, brass))
    }

    @Test
    fun `ticking what's already ticked changes nothing`() {
        BringGameDecider.given(*joined, GameBrought(gorefest7, bram, brass)).whenever(BringGame(gorefest7, bram, brass, true)).thenEmitsNothing()
        BringGameDecider.given(*joined).whenever(BringGame(gorefest7, bram, brass, false)).thenEmitsNothing()
    }

    @Test
    fun `someone else bringing the same game doesn't count as mine`() {
        BringGameDecider
            .given(*joined, GameBrought(gorefest7, anna, brass))
            .whenever(BringGame(gorefest7, bram, brass, true))
            .thenEmits(GameBrought(gorefest7, bram, brass))
    }

    @Test
    fun `only joined players, only while it's on`() {
        BringGameDecider.given(gameDayCreated()).whenever(BringGame(gorefest7, bram, brass, true)).thenRejects(Rejection.NotJoined)
        BringGameDecider.given(*joined, GameDayEnded(gorefest7)).whenever(BringGame(gorefest7, bram, brass, true)).thenRejects(Rejection.GameDayOver)
    }

    private val catan = BggId(13)
    private val ark = BggId(342942)
    private val onShelf = arrayOf<Event>(*joined, GameBrought(gorefest7, anna, brass))

    @Test
    fun `any joined player can want a game somebody else brought`() {
        SetPlayWishDecider.given(*onShelf).whenever(SetPlayWish(gorefest7, bram, brass, true)).thenEmits(PlayWishChanged(gorefest7, bram, brass, true))
        SetPlayWishDecider.given(*onShelf).whenever(SetPlayWish(gorefest7, anna, brass, true)).thenEmits(PlayWishChanged(gorefest7, anna, brass, true))
    }

    @Test
    fun `wanting needs the game on the shelf, a joined player and a game day that's on`() {
        SetPlayWishDecider.given(*joined).whenever(SetPlayWish(gorefest7, bram, brass, true)).thenRejects(Rejection.NotOnShelf)
        SetPlayWishDecider.given(gameDayCreated(), GameBrought(gorefest7, anna, brass)).whenever(SetPlayWish(gorefest7, bram, brass, true)).thenRejects(Rejection.NotJoined)
        SetPlayWishDecider.given(*onShelf, GameDayEnded(gorefest7)).whenever(SetPlayWish(gorefest7, bram, brass, true)).thenRejects(Rejection.GameDayOver)
    }

    @Test
    fun `wanting what you already want, or not wanting what you don't, changes nothing`() {
        SetPlayWishDecider.given(*onShelf, PlayWishChanged(gorefest7, bram, brass, true)).whenever(SetPlayWish(gorefest7, bram, brass, true)).thenEmitsNothing()
        SetPlayWishDecider.given(*onShelf).whenever(SetPlayWish(gorefest7, bram, brass, false)).thenEmitsNothing()
        SetPlayWishDecider
            .given(*onShelf, PlayWishChanged(gorefest7, bram, brass, true))
            .whenever(SetPlayWish(gorefest7, bram, brass, false))
            .thenEmits(PlayWishChanged(gorefest7, bram, brass, false))
    }

    @Test
    fun `a player gets a few wishes, and giving one back frees it`() {
        val shelf = arrayOf<Event>(*joined, *listOf(brass, azul, catan, ark).map { GameBrought(gorefest7, anna, it) }.toTypedArray())
        val wished = listOf(brass, azul, catan).map { PlayWishChanged(gorefest7, bram, it, true) }.toTypedArray()

        SetPlayWishDecider.given(*shelf, *wished).whenever(SetPlayWish(gorefest7, bram, ark, true)).thenRejects(Rejection.TooManyWishes)
        SetPlayWishDecider.given(*shelf, *wished).whenever(SetPlayWish(gorefest7, bram, catan, false)).thenEmits(PlayWishChanged(gorefest7, bram, catan, false))
        SetPlayWishDecider.given(*shelf, *wished, PlayWishChanged(gorefest7, bram, catan, false)).whenever(SetPlayWish(gorefest7, bram, ark, true)).thenEmits(PlayWishChanged(gorefest7, bram, ark, true))
        // Other people's wishes don't count against mine.
        SetPlayWishDecider.given(*shelf, *wished).whenever(SetPlayWish(gorefest7, anna, ark, true)).thenEmits(PlayWishChanged(gorefest7, anna, ark, true))
    }

    @Test
    fun `a game that left the shelf takes the wishes with it, so bringing it again starts neutral and frees the slot`() {
        val shelf = arrayOf<Event>(*joined, *listOf(brass, azul, catan, ark).map { GameBrought(gorefest7, anna, it) }.toTypedArray())
        val wished = listOf(brass, azul, catan).map { PlayWishChanged(gorefest7, bram, it, true) }.toTypedArray()

        SetPlayWishDecider.given(*shelf, *wished, GameUnbrought(gorefest7, anna, catan)).whenever(SetPlayWish(gorefest7, bram, ark, true)).thenEmits(PlayWishChanged(gorefest7, bram, ark, true))
        SetPlayWishDecider
            .given(*shelf, *wished, GameUnbrought(gorefest7, anna, catan), GameBrought(gorefest7, anna, catan))
            .whenever(SetPlayWish(gorefest7, bram, catan, true))
            .thenEmits(PlayWishChanged(gorefest7, bram, catan, true))
        // Still on the shelf while one owner is left.
        SetPlayWishDecider
            .given(*onShelf, GameBrought(gorefest7, bram, brass), PlayWishChanged(gorefest7, bram, brass, true), GameUnbrought(gorefest7, anna, brass))
            .whenever(SetPlayWish(gorefest7, bram, brass, true))
            .thenEmitsNothing()
    }

    @Test
    fun `the shelf shows each game once, with all owners`() {
        val shelf = listOf(
            GameBrought(gorefest7, anna, brass), GameBrought(gorefest7, bram, azul), GameBrought(gorefest7, bram, brass),
            PlayWishChanged(gorefest7, bram, brass, true), PlayWishChanged(gorefest7, anna, brass, true), GameUnbrought(gorefest7, bram, azul),
        ).fold(Shelves.initialState, Shelves::evolve).of(gorefest7)

        shelf shouldBe listOf(ShelfGame(brass, owners = listOf(anna, bram), eager = setOf(bram, anna)))
    }

    @Test
    fun `an owner stepping back drops their own wish but not other people's`() {
        val events = listOf(
            GameBrought(gorefest7, anna, brass), GameBrought(gorefest7, bram, brass),
            PlayWishChanged(gorefest7, anna, brass, true), PlayWishChanged(gorefest7, bram, brass, true), GameUnbrought(gorefest7, anna, brass),
        )
        events.fold(Shelves.initialState, Shelves::evolve).of(gorefest7) shouldBe listOf(ShelfGame(brass, owners = listOf(bram), eager = setOf(bram)))

        // The decider agrees: Anna's wish is gone (wanting again is news), Bram's stays (wanting again changes nothing).
        SetPlayWishDecider.given(*joined, *events.toTypedArray()).whenever(SetPlayWish(gorefest7, anna, brass, true)).thenEmits(PlayWishChanged(gorefest7, anna, brass, true))
        SetPlayWishDecider.given(*joined, *events.toTypedArray()).whenever(SetPlayWish(gorefest7, bram, brass, true)).thenEmitsNothing()
    }

    @Test
    fun `a non-owner's wish survives the owners changing, but not the game leaving`() {
        val events = listOf(GameBrought(gorefest7, anna, brass), PlayWishChanged(gorefest7, bram, brass, true), GameBrought(gorefest7, anna, azul), GameUnbrought(gorefest7, anna, azul))
        events.fold(Shelves.initialState, Shelves::evolve).of(gorefest7) shouldBe listOf(ShelfGame(brass, owners = listOf(anna), eager = setOf(bram)))

        (events + GameUnbrought(gorefest7, anna, brass) + GameBrought(gorefest7, anna, brass)).fold(Shelves.initialState, Shelves::evolve).of(gorefest7) shouldBe
            listOf(ShelfGame(brass, owners = listOf(anna)))
    }

    // Erasure

    @Test
    fun `an erased player's games and wishes leave a shelf still to be played`() {
        val events = listOf(
            *joined, GameBrought(gorefest7, anna, brass), GameBrought(gorefest7, bram, azul), GameBrought(gorefest7, bram, brass),
            PlayWishChanged(gorefest7, anna, azul, true), PlayWishChanged(gorefest7, anna, brass, true), PlayWishChanged(gorefest7, bram, brass, true),
            PlayerErased(anna),
        )

        events.fold(Shelves.initialState, Shelves::evolve).of(gorefest7) shouldBe
            listOf(ShelfGame(brass, owners = listOf(bram), eager = setOf(bram)), ShelfGame(azul, owners = listOf(bram)))
    }

    @Test
    fun `a game only an erased player brought leaves the shelf`() {
        listOf(*joined, GameBrought(gorefest7, anna, brass), GameBrought(gorefest7, bram, azul), PlayerErased(anna))
            .fold(Shelves.initialState, Shelves::evolve).of(gorefest7).map { it.game } shouldBe listOf(azul)
    }

    @Test
    fun `a closed game day keeps an erased player's games`() {
        for (closed in listOf(GameDayEnded(gorefest7), GameDayCancelled(gorefest7))) {
            listOf(*joined, GameBrought(gorefest7, anna, brass), closed, PlayerErased(anna)).fold(Shelves.initialState, Shelves::evolve).of(gorefest7) shouldBe
                listOf(ShelfGame(brass, owners = listOf(anna)))
        }
    }

    @Test
    fun `a game only an erased player brought can't be wished for`() {
        SetPlayWishDecider.given(*onShelf, PlayerErased(anna)).whenever(SetPlayWish(gorefest7, bram, brass, true)).thenRejects(Rejection.NotOnShelf)
    }

    @Test
    fun `a wish for an erased player's game stops counting against the limit`() {
        SetPlayWishDecider
            .given(
                *joined, GameBrought(gorefest7, anna, brass), GameBrought(gorefest7, anna, azul), GameBrought(gorefest7, anna, catan), GameBrought(gorefest7, bram, ark),
                PlayWishChanged(gorefest7, bram, brass, true), PlayWishChanged(gorefest7, bram, azul, true), PlayWishChanged(gorefest7, bram, catan, true),
                PlayerErased(anna),
            )
            .whenever(SetPlayWish(gorefest7, bram, ark, true))
            .thenEmits(PlayWishChanged(gorefest7, bram, ark, true))
    }
}
