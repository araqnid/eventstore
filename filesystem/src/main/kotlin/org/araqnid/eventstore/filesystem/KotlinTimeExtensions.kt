package org.araqnid.eventstore.filesystem

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import java.time.format.DateTimeFormatter
import java.time.Clock as JavaClock

internal fun DateTimeFormatter.format(instant: Instant) = format(instant.toJavaInstant())

internal fun JavaClock.asKotlin(): Clock = ClockFromJava(this)

private class ClockFromJava(private val javaClock: JavaClock) : Clock {
    override fun now(): Instant {
        return javaClock.instant().toKotlinInstant()
    }
}
