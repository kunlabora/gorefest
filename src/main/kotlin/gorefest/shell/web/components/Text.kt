package gorefest.shell.web.components

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val longDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
private val fullDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

/** "Sun 27 Sep 2026" */
fun LocalDate.long(): String = format(longDate)

/** "Sunday 27 September" */
fun LocalDate.full(): String = format(fullDate)

/** "Sep" */
fun LocalDate.monthShort(): String = format(shortMonth)

/** "Two", "Four"… for small counts in sentences; digits from ten up. */
fun Int.inWords(): String =
    listOf("No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine").getOrElse(this) { toString() }

/** "1 game", "3 games" */
fun Int.of(singular: String, plural: String = singular + "s") = "$this ${if (this == 1) singular else plural}"
