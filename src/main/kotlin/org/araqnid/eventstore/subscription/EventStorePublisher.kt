package org.araqnid.eventstore.subscription

import com.google.common.util.concurrent.AbstractScheduledService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Service
import org.araqnid.eventstore.EventCategoryReader
import org.araqnid.eventstore.EventReader
import org.araqnid.eventstore.Position
import org.araqnid.eventstore.ResolvedEvent
import org.araqnid.eventstore.forEachOrderedAndClose
import java.time.Duration
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

class EventStorePublisher private constructor(private val produceEvents: (Position) -> Stream<ResolvedEvent>, private val emptyPosition: Position, private val interval: Duration) : Flow.Publisher<ResolvedEvent> {
    constructor(eventReader: EventReader, interval: Duration) : this(eventReader::readAllForwards, eventReader.emptyStorePosition, interval)
    constructor(eventCategoryReader: EventCategoryReader, category: String, interval: Duration) : this({ eventCategoryReader.readCategoryForwards(category) }, eventCategoryReader.emptyCategoryPosition(category), interval)

    override fun subscribe(subscriber: Flow.Subscriber<in ResolvedEvent>) {
        subscriber.onSubscribe(EventSubscription(subscriber))
    }

    inner class EventSubscription(subscriber: Flow.Subscriber<in ResolvedEvent>) : Flow.Subscription {
        private val service = EventSubscriptionService(subscriber::onNext, subscriber::onError)

        init {
            service.addListener(object : Service.Listener() {
                override fun failed(from: Service.State, failure: Throwable) {
                    subscriber.onError(failure)
                }
            }, MoreExecutors.directExecutor())
            service.startAsync()
        }

        override fun request(n: Long) {
            service.request(n)
        }

        override fun cancel() {
            service.stopAsync()
        }
    }

    inner class EventSubscriptionService(private val sink: (ResolvedEvent) -> Unit, private val onError: (Throwable) -> Unit) : AbstractScheduledService() {
        private var lastPosition = emptyPosition
        private var requested = 0L

        override fun scheduler(): Scheduler {
            return Scheduler.newFixedRateSchedule(0, interval.toNanos(), TimeUnit.NANOSECONDS)
        }

        fun request(n: Long) {
            synchronized(this) {
                if (n == Long.MAX_VALUE)
                    requested = n
                else if (requested != Long.MAX_VALUE)
                    requested += n
            }
        }

        override fun runOneIteration() {
            var eventsRead = 0
            val eventsWanted = synchronized(this) { requested }
            if (eventsWanted == 0L) return
            try {
                produceEvents(lastPosition)
                        .let { if (eventsWanted != Long.MAX_VALUE) it.limit(eventsWanted) else it }
                        .forEachOrderedAndClose { re ->
                    if (!isRunning) throw ConsumptionStoppedException()
                    try {
                        sink(re)
                        lastPosition = re.position
                        ++eventsRead
                    } catch (e: Exception) {
                        throw RuntimeException("Failed to process $re", e)
                    }
                }
            } catch (ignored: ConsumptionStoppedException) {
                // just jump out of consumption in order to finish iteration
            } catch (e: Exception) {
                onError(e)
                throw e
            }
            synchronized(this) {
                if (requested != Long.MAX_VALUE) {
                    if (requested > eventsRead)
                        requested -= eventsRead
                    else
                        requested = 0
                }
            }
        }
    }

    private class ConsumptionStoppedException : Exception()
}
