package org.araqnid.eventstore

import com.google.common.base.Preconditions
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAmount
import java.time.temporal.TemporalUnit

/**
 * Clock that only updates in positive increments when called directly.
 */
class ManualClock(@Volatile private var instant: Instant, private val zoneId: ZoneId) : Clock() {
    override fun instant(): Instant = instant

    fun bump(duration: TemporalAmount) {
        val newInstant = instant + duration
        if (newInstant < instant)
            throw IllegalArgumentException("temporal amount must be non-negative: $duration")
        instant = newInstant
    }

    fun bump(amountToAdd: Long, unit: TemporalUnit) {
        Preconditions.checkArgument(amountToAdd >= 0L)
        instant = instant.plus(amountToAdd, unit)
    }

    fun bumpNanos(nanos: Long) {
        Preconditions.checkArgument(nanos >= 0L)
        instant = instant.plusNanos(nanos)
    }

    fun bumpMillis(millis: Long) {
        Preconditions.checkArgument(millis >= 0L)
        instant = instant.plusMillis(millis)
    }

    fun bumpSeconds(seconds: Long) {
        Preconditions.checkArgument(seconds >= 0L)
        instant = instant.plusSeconds(seconds)
    }

    fun advanceTo(futureInstant: Instant) {
        val duration = Duration.between(instant, futureInstant)
        if (duration < Duration.ZERO)
            throw IllegalArgumentException("Instant is before current time: $futureInstant")
        instant = futureInstant
    }

    override fun withZone(newZone: ZoneId): Clock {
        return object : Clock() {
            override fun instant(): Instant = instant

            override fun getZone(): ZoneId = newZone

            override fun withZone(newNewZone: ZoneId): Clock = this@ManualClock.withZone(newNewZone)

            override fun toString(): String {
                return "${this@ManualClock}{zone:$newZone}"
            }
        }
    }

    override fun getZone(): ZoneId = zoneId

    override fun toString(): String {
        return "ManualClock:$instant,$zoneId"
    }
}