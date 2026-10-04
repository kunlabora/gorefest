package gorefest.shell.web

import gorefest.core.gameday.GameDaySummary
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

const val DEFAULT_TIME_ZONE = "Europe/Brussels"

/** The zone with this id, or [DEFAULT_TIME_ZONE] when there is none or it's unknown. */
fun zoneOrDefault(id: String?): ZoneId = id?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of(DEFAULT_TIME_ZONE)

fun zone(gameDay: GameDaySummary): ZoneId = zoneOrDefault(gameDay.timeZone)

/** The date on the wall in the zone with this id (default zone when unknown). */
fun Clock.todayIn(timeZone: String?): LocalDate = LocalDate.now(withZone(zoneOrDefault(timeZone)))
