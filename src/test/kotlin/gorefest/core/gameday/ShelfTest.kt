package gorefest.core.gameday

import gorefest.core.BggId
import gorefest.core.Event
import gorefest.core.GameBrought
import gorefest.core.GameDayEnded
import gorefest.core.GameUnbrought
import gorefest.core.PlayWishChanged
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
        BringGameDecider.given(gameDayCreated()).whenever(BringGame(gorefest7, bram, brass, true)).thenRejects("Join the game day first")
        BringGameDecider.given(*joined, GameDayEnded(gorefest7)).whenever(BringGame(gorefest7, bram, brass, true)).thenRejects("This game day has ended")
    }

    @Test
    fun `wanting to play needs the game brought`() {
        SetPlayWishDecider.given(*joined).whenever(SetPlayWish(gorefest7, bram, brass, true)).thenRejects("Bring the game first")
        SetPlayWishDecider
            .given(*joined, GameBrought(gorefest7, bram, brass))
            .whenever(SetPlayWish(gorefest7, bram, brass, true))
            .thenEmits(PlayWishChanged(gorefest7, bram, brass, true))
    }

    @Test
    fun `unbringing drops the wish, so bringing again starts neutral`() {
        SetPlayWishDecider
            .given(*joined, GameBrought(gorefest7, bram, brass), PlayWishChanged(gorefest7, bram, brass, true), GameUnbrought(gorefest7, bram, brass), GameBrought(gorefest7, bram, brass))
            .whenever(SetPlayWish(gorefest7, bram, brass, false))
            .thenEmitsNothing()
    }

    @Test
    fun `the shelf shows each game once, with all owners`() {
        val shelf = listOf(
            GameBrought(gorefest7, anna, brass), GameBrought(gorefest7, bram, azul), GameBrought(gorefest7, bram, brass),
            PlayWishChanged(gorefest7, bram, brass, true), GameUnbrought(gorefest7, bram, azul),
        ).fold(Shelves.initialState, Shelves::evolve).getValue(gorefest7)

        shelf shouldBe listOf(ShelfGame(brass, owners = listOf(anna, bram), eager = setOf(bram)))
    }
}
