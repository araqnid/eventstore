package org.araqnid.eventstore.subscription

import org.araqnid.eventstore.Position
import org.araqnid.eventstore.PositionCodec
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Comparator.comparing

class SnapshotTrigger(val positionCodec: PositionCodec, val minimalSnapshotInterval: Duration, val quietPeriodAfterEvent: Duration, val patienceForQuietPeriod: Duration, val clock: Clock) {
    private val comparator = comparing({ po: PositionObserved -> po.position }, positionCodec::comparePositions)

    private var lastEventReceived: PositionObserved? = null
    private var lastSnapshot: PositionObserved? = null
    private var snapshotBecameNeedful: PositionObserved? = null

    @Synchronized
    fun eventReceived(position: Position) {
        lastEventReceived = observed(position)
        if (snapshotBecameNeedful == null && (lastSnapshot == null || lastEventReceived!! > lastSnapshot!!))
            snapshotBecameNeedful = lastEventReceived
    }

    @Synchronized
    fun snapshotLoaded(position: Position) {
        lastSnapshot = observed(position)
    }

    @Synchronized
    fun snapshotWritten(position: Position) {
        lastSnapshot = observed(position)
        if (snapshotBecameNeedful != null && lastSnapshot!! >= snapshotBecameNeedful!!)
            snapshotBecameNeedful = null
    }

    @Synchronized
    fun writeNewSnapshot(): Boolean {
        if (lastEventReceived == null)
            return false
        if (lastSnapshot != null) {
            if (since(lastSnapshot!!) <= minimalSnapshotInterval)
                return false
            if (lastSnapshot!!.position == lastEventReceived!!.position)
                return false
        }
        return since(lastEventReceived!!) >= quietPeriodAfterEvent
                || since(snapshotBecameNeedful!!) >= patienceForQuietPeriod
    }

    @Synchronized
    fun writeInitialSnapshot(): Boolean {
        if (lastEventReceived == null)
            return false
        if (lastSnapshot == null)
            return true
        return lastEventReceived!! > lastSnapshot!!
    }

    private fun since(positionObserved: PositionObserved) = Duration.between(positionObserved.instant, Instant.now(clock))

    private fun observed(position: Position) = PositionObserved(position, Instant.now(clock))

    private fun comparePositions(left: PositionObserved, right: PositionObserved) = comparator.compare(left, right)

    private operator fun PositionObserved.compareTo(other: PositionObserved): Int = comparePositions(this, other)

    data class PositionObserved(val position: Position, val instant: Instant)
}
