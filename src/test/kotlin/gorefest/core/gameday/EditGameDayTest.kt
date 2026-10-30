package gorefest.core.gameday

import gorefest.contract.Obligation
import gorefest.core.Rejection
import gorefest.core.GameDayDetailsChanged
import gorefest.core.GameDayEnded
import gorefest.core.eventsourcing.given
import org.junit.jupiter.api.Test

class EditGameDayTest {
    private val edit = EditGameDay(gorefest7, anna, " Gorefest #7½ ", today.plusDays(4), "Bram's attic", today)

    @Test
    @Obligation("GD-6")
    fun `the host changes title, date and place`() {
        EditGameDayDecider
            .given(gameDayCreated())
            .whenever(edit)
            .thenEmits(GameDayDetailsChanged(gorefest7, "Gorefest #7½", today.plusDays(4), "Bram's attic"))
    }

    @Test
    @Obligation("GD-6")
    fun `only the host can edit`() {
        EditGameDayDecider.given(gameDayCreated()).whenever(edit.copy(editor = bram)).thenRejects(Rejection.OnlyHostCanEdit)
    }

    @Test
    @Obligation("GD-7")
    fun `saving without changes emits nothing`() {
        val created = gameDayCreated()
        EditGameDayDecider
            .given(created)
            .whenever(edit.copy(title = created.title, date = created.date, place = created.place))
            .thenEmitsNothing()
    }

    @Test
    @Obligation("GD-7")
    fun `earlier edits count as the current details`() {
        val changed = GameDayDetailsChanged(gorefest7, "Gorefest #7½", today.plusDays(4), "Bram's attic")
        EditGameDayDecider.given(gameDayCreated(), changed).whenever(edit).thenEmitsNothing()
    }

    @Test
    @Obligation("GD-7")
    fun `same rules as creating`() {
        EditGameDayDecider.given(gameDayCreated()).whenever(edit.copy(title = "")).thenRejects(Rejection.TitleMissing)
        EditGameDayDecider.given(gameDayCreated()).whenever(edit.copy(date = today.minusDays(1))).thenRejects(Rejection.DateInPast)
    }

    @Test
    @Obligation("GD-8")
    fun `ended or unknown game days can't be edited`() {
        EditGameDayDecider.given(gameDayCreated(), GameDayEnded(gorefest7)).whenever(edit).thenRejects(Rejection.GameDayOver)
        EditGameDayDecider.given().whenever(edit).thenRejects(Rejection.NoSuchGameDay)
    }


    @Test
    @Obligation("GD-7")
    fun `a game day whose date has passed can still be edited, as long as the date stays`() {
        val created = gameDayCreated().copy(date = today.minusDays(2))
        val retitled = edit.copy(date = created.date, place = created.place)

        EditGameDayDecider.given(created).whenever(retitled).thenEmits(GameDayDetailsChanged(gorefest7, "Gorefest #7½", created.date, created.place))
        EditGameDayDecider.given(created).whenever(retitled.copy(date = today.minusDays(1))).thenRejects(Rejection.DateInPast)
    }
}
