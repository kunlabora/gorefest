package gorefest.core.gameday

import gorefest.core.GameDayCreated
import gorefest.core.GameDayId
import gorefest.core.PlayerId
import java.time.LocalDate
import kotlin.uuid.Uuid

val anna = PlayerId(Uuid.random())
val bram = PlayerId(Uuid.random())
val gorefest7 = GameDayId(Uuid.random())
val today: LocalDate = LocalDate.of(2026, 9, 24)

fun gameDayCreated(gameDay: GameDayId = gorefest7, host: PlayerId = anna) =
    GameDayCreated(gameDay, host, "Gorefest #7", today.plusDays(3), "Anna's place", "Europe/Brussels")
