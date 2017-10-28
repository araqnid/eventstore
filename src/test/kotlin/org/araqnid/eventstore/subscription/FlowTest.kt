package org.araqnid.eventstore.subscription

import com.google.common.util.concurrent.Monitor
import org.araqnid.eventstore.Blob
import org.araqnid.eventstore.EventRecord
import org.araqnid.eventstore.InMemoryEventSource
import org.araqnid.eventstore.NewEvent
import org.araqnid.eventstore.ResolvedEvent
import org.araqnid.eventstore.StreamId
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.fail
import org.junit.Test
import java.lang.AssertionError
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit

class FlowTest {
    @Test
    fun `delivers events to subscriber`() {
        val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        val eventSource = InMemoryEventSource(clock)
        eventSource.write(StreamId("test", "test"), listOf(NewEvent("Test", Blob.empty)))

        val subscriber = TestSubscriber<ResolvedEvent>()
        val publisher = EventStorePublisher(eventSource, Duration.ofMillis(10L))
        publisher.subscribe(subscriber)

        subscriber.awaitReceived(1, Duration.ofSeconds(1))
        assertThat(subscriber.received.map { it.event }, equalTo(listOf(EventRecord(StreamId("test", "test"), 0L, Instant.EPOCH, "Test", Blob.empty, Blob.empty))))

        subscriber.cancel()
    }

    class TestSubscriber<T> : Flow.Subscriber<T> {
        val received = mutableListOf<T>()
        var error: Throwable? = null
        var completed: Boolean = false

        private lateinit var subscription: Flow.Subscription
        private val monitor = Monitor()

        fun awaitReceived(count: Long, duration: Duration) {
            if (!monitor.enterWhen(object : Monitor.Guard(monitor) {
                    override fun isSatisfied(): Boolean {
                        return error != null || received.size >= count;
                    }
                }, duration.toNanos(), TimeUnit.NANOSECONDS)) {
                fail("Did not reach $count entries received within $duration")
            }
            try {
                if (error != null && received.size < count)
                    throw AssertionError("Received error before receiving $count entries", error)
            } finally {
                monitor.leave()
            }
        }

        fun cancel() {
            subscription.cancel()
        }

        override fun onSubscribe(subscription: Flow.Subscription) {
            this.subscription = subscription
            subscription.request(Flow.defaultBufferSize().toLong())
        }

        private inline fun <U> withMonitor(block: () -> U): U {
            monitor.enter()
            return try {
                block()
            } finally {
                monitor.leave()
            }
        }

        override fun onNext(item: T) {
            withMonitor {
                received += item
            }
        }

        override fun onError(throwable: Throwable) {
            withMonitor {
                error = throwable
            }
        }

        override fun onComplete() {
            withMonitor {
                completed = true
            }
        }
    }
}