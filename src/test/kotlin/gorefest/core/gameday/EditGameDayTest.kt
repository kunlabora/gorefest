package gorefest.core.gameday

import gorefest.core.Rejection
import gorefest.core.GameDayDetailsChanged
import gorefest.core.GameDayEnded
import gorefest.core.eventsourcing.given
import org.junit.jupiter.api.Test

class EditGameDayTest {
    private val edit = EditGameDay(gorefest7, anna, " Gorefest #7½ ", today.plusDays(4), "Bram's attic", today)

    @Test
    fun `the host changes title, date and place`() {
        EditGameDayDecider
            .given(gameDayCreated())
            .whenever(edit)
            .thenEmits(GameDayDetailsChanged(gorefest7, "Gorefest #7½", today.plusDays(4), "Bram's attic"))
    }

    @Test
    fun `only the host can edit`() {
        EditGameDayDecider.given(gameDayCreated()).whenever(edit.copy(editor = bram)).thenRejects(Rejection.OnlyHostCanEdit)
    }

    @Test
    fun `saving without changes emits nothing`() {
        val created = gameDayCreated()
        EditGameDayDecider
            .given(created)
            .whenever(edit.copy(title = created.title, date = created.date, place = created.place))
            .thenEmitsNothing()
    }

    @Test
    fun `earlier edits count as the current details`() {
        val changed = GameDayDetailsChanged(gorefest7, "Gorefest #7½", today.plusDays(4), "Bram's attic")
        EditGameDayDecider.given(gameDayCreated(), changed).whenever(edit).thenEmitsNothing()
    }

    @Test
    fun `same rules as creating`() {
        EditGameDayDecider.given(gameDayCreated()).whenever(edit.copy(title = "")).thenRejects(Rejection.TitleMissing)
        EditGameDayDecider.given(gameDayCreated()).whenever(edit.copy(date = today.minusDays(1))).thenRejects(Rejection.DateInPast)
    }

    @Test
    fun `ended or unknown game days can't be edited`() {
        EditGameDayDecider.given(gameDayCreated(), GameDayEnded(gorefest7)).whenever(edit).thenRejects(Rejection.GameDayOver)
        EditGameDayDecider.given().whenever(edit).thenRejects(Rejection.NoSuchGameDay)
    }
}
