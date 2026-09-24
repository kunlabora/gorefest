package gorefest.shell

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock tests can move forward. */
class TestClock(var now: Instant = Instant.parse("2026-09-24T10:00:00Z"), private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = object : Clock() {
        override fun instant() = this@TestClock.now
        override fun getZone() = zone
        override fun withZone(zone: ZoneId) = this@TestClock.withZone(zone)
    }

    fun advance(by: Duration) {
        now += by
    }
}
