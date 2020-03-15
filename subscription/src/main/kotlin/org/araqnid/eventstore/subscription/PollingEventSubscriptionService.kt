package org.araqnid.eventstore.subscription

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.AbstractScheduledService
import com.google.common.util.concurrent.Service
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.araqnid.eventstore.EventReader
import org.araqnid.eventstore.Position
import org.araqnid.eventstore.ResolvedEvent
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class PollingEventSubscriptionService(val eventReader: EventReader, private val sink: Sink, private val interval: Duration) : AbstractScheduledService() {
    interface Sink {
        fun accept(event: ResolvedEvent)
    }

    interface SubscriptionListener {
        fun pollStarted(position: Position) {}
        fun pollFinished(position: Position, eventsRead: Int) {}
    }

    var startPosition = eventReader.emptyStorePosition
        set(value) {
            if (state() != Service.State.NEW) throw IllegalStateException("Start position can only be set on subscription in NEW state")
            field = value
        }

    private val listeners = ListenerSet<SubscriptionListener>()
    private val emitter = listeners.proxy(SubscriptionListener::class.java)
    @Volatile private var lastPosition = eventReader.emptyStorePosition

    override fun scheduler(): Scheduler {
        return Scheduler.newFixedRateSchedule(0, interval.toNanos(), TimeUnit.NANOSECONDS)
    }

    override fun startUp() {
        lastPosition = startPosition
    }

    override fun runOneIteration() {
        var eventsRead = 0
        emitter.pollStarted(lastPosition)
        try {
            runBlocking {
                eventReader.readAllForwards(lastPosition).collect { re ->
                    if (!isRunning) throw ConsumptionStoppedException()
                    try {
                        sink.accept(re)
                        lastPosition = re.position
                        ++eventsRead
                    } catch (e: Exception) {
                        throw RuntimeException("Failed to process $re", e)
                    }
                }
            }
            emitter.pollFinished(lastPosition, eventsRead)
        } catch (ignored: ConsumptionStoppedException) {
            // just jump out of consumption in order to finish iteration
        }
        // TODO deal with transient failures reading event stream
    }

    @VisibleForTesting
    fun kick() {
        runOneIteration()
    }

    fun addListener(listener: SubscriptionListener, executor: Executor) {
        listeners.addListener(listener, executor)
    }

    private class ConsumptionStoppedException : Exception()
}
