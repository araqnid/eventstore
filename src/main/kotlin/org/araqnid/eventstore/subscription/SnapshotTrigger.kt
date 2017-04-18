package org.araqnid.eventstore.subscription

import org.araqnid.eventstore.Position
import org.araqnid.eventstore.PositionCodec
import java.time.Clock
import java.time.Duration
import java.time.Instant

class SnapshotTrigger(val positionCodec: PositionCodec, val minimalSnapshotInterval: Duration, val quietPeriodAfterEvent: Duration, val patienceForQuietPeriod: Duration, val clock: Clock) {
    private data class State(val lastEventReceived: PositionObserved? = null, val lastSnapshot: PositionObserved? = null, val snapshotBecameNeedful: PositionObserved? = null)

    private var state = State()

    @Synchronized
    fun eventReceived(position: Position) {
        nextState {
            val now = observed(position)
            if (snapshotBecameNeedful == null && (lastSnapshot == null || now > lastSnapshot)) {
                copy(snapshotBecameNeedful = now, lastEventReceived = now)
            }
            else {
                copy(lastEventReceived = now)
            }
        }
    }

    @Synchronized
    fun snapshotLoaded(position: Position) {
        nextState {
            val now = observed(position)
            copy(lastSnapshot = now)
        }
    }

    @Synchronized
    fun snapshotWritten(position: Position) {
        nextState {
            val now = observed(position)
            if (snapshotBecameNeedful != null && now >= snapshotBecameNeedful) {
                copy(lastSnapshot = now, snapshotBecameNeedful = null)
            }
            else {
                copy(lastSnapshot = now)
            }
        }
    }

    @Synchronized
    fun writeNewSnapshot(): Boolean = state.run {
        when {
            lastEventReceived == null -> false
            lastSnapshot != null && lastSnapshot.position == lastEventReceived.position -> false
            lastSnapshot != null && since(lastSnapshot) <= minimalSnapshotInterval -> false
            else -> since(lastEventReceived) >= quietPeriodAfterEvent
                    || since(snapshotBecameNeedful!!) >= patienceForQuietPeriod
        }
    }

    @Synchronized
    fun writeInitialSnapshot(): Boolean = state.run {
        when {
            lastEventReceived == null -> false
            lastSnapshot == null -> true
            else -> lastEventReceived > lastSnapshot
        }
    }

    private fun since(positionObserved: PositionObserved) = Duration.between(positionObserved.instant, Instant.now(clock))

    private fun observed(position: Position) = PositionObserved(position, Instant.now(clock))

    private operator fun PositionObserved.compareTo(other: PositionObserved): Int = positionCodec.comparePositions(this.position, other.position)

    private inline fun nextState(block: State.() -> State): Unit {
        state = state.block()
    }

    private data class PositionObserved(val position: Position, val instant: Instant)
}
