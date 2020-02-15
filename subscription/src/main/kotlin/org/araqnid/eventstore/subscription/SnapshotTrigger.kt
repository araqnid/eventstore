package org.araqnid.eventstore.subscription

import org.araqnid.eventstore.Position
import org.araqnid.eventstore.PositionCodec
import java.time.Clock
import java.time.Duration
import java.time.Instant

class SnapshotTrigger(
        private val positionCodec: PositionCodec,
        private val minimalSnapshotInterval: Duration,
        private val quietPeriodAfterEvent: Duration,
        private val patienceForQuietPeriod: Duration,
        private val clock: Clock
) {
    private sealed class State {
        abstract fun eventReceived(now: PositionObserved): State
        abstract fun snapshotLoaded(now: PositionObserved): State
        abstract fun snapshotWritten(now: PositionObserved): State

        object Empty : State() {
            override fun eventReceived(now: PositionObserved) = InitialEventsReceived(now)
            override fun snapshotLoaded(now: PositionObserved) = SnapshotComplete(lastSnapshot = now)
            override fun snapshotWritten(now: PositionObserved) = throw IllegalStateException()
        }

        data class InitialEventsReceived(val lastEventReceived: PositionObserved, val becameNeedful: PositionObserved = lastEventReceived): State() {
            override fun eventReceived(now: PositionObserved) = copy(lastEventReceived = now)
            override fun snapshotLoaded(now: PositionObserved) = throw IllegalStateException()
            override fun snapshotWritten(now: PositionObserved) = if (lastEventReceived > now) SnapshotOutdated(lastEventReceived, lastSnapshot = now) else SnapshotComplete(now)
        }

        data class SnapshotComplete(val lastSnapshot: PositionObserved): State() {
            override fun eventReceived(now: PositionObserved) = SnapshotOutdated(now, lastSnapshot, becameNeedful = now)
            override fun snapshotLoaded(now: PositionObserved) = throw IllegalStateException()
            override fun snapshotWritten(now: PositionObserved) = throw IllegalStateException()
        }

        data class SnapshotOutdated(val lastEventReceived: PositionObserved, val lastSnapshot: PositionObserved, val becameNeedful: PositionObserved = lastEventReceived): State() {
            override fun eventReceived(now: PositionObserved) = copy(lastEventReceived = now)
            override fun snapshotLoaded(now: PositionObserved) = throw IllegalStateException()
            override fun snapshotWritten(now: PositionObserved) = if (lastEventReceived > now) copy(lastSnapshot = now) else SnapshotComplete(now)
        }
    }

    private var state: State = State.Empty

    @Synchronized
    fun eventReceived(position: Position) {
        state = state.eventReceived(observed(position))
    }

    @Synchronized
    fun snapshotLoaded(position: Position) {
        state = state.snapshotLoaded(observed(position))
    }

    @Synchronized
    fun snapshotWritten(position: Position) {
        state = state.snapshotWritten(observed(position))
    }

    @Synchronized
    fun writeInitialSnapshot(): Boolean = state is State.InitialEventsReceived || state is State.SnapshotOutdated

    @Synchronized
    fun writeNewSnapshot(): Boolean = state.run { when (this) {
        State.Empty, is State.SnapshotComplete -> false
        is State.InitialEventsReceived -> since(lastEventReceived) >= quietPeriodAfterEvent || since(becameNeedful) >= patienceForQuietPeriod
        is State.SnapshotOutdated -> since(lastSnapshot) > minimalSnapshotInterval && (since(lastEventReceived) >= quietPeriodAfterEvent || since(becameNeedful) >= patienceForQuietPeriod)
    } }

    private fun since(positionObserved: PositionObserved) = Duration.between(positionObserved.instant, Instant.now(clock))

    private fun observed(position: Position) = PositionObserved(position, Instant.now(clock), positionCodec)

    private data class PositionObserved(val position: Position, val instant: Instant, private val positionCodec: PositionCodec) : Comparable<PositionObserved> {
        override fun compareTo(other: PositionObserved) =
                positionCodec.comparePositions(this.position, other.position)

        override fun toString() = "$position @ $instant"
    }

    override fun toString() = state.toString()
}
