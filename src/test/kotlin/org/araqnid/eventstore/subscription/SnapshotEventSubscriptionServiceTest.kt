package org.araqnid.eventstore.subscription

import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Monitor
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.common.util.concurrent.Service
import junit.framework.AssertionFailedError
import org.araqnid.eventstore.Blob
import org.araqnid.eventstore.EventRecord
import org.araqnid.eventstore.InMemoryEventSource
import org.araqnid.eventstore.NewEvent
import org.araqnid.eventstore.Position
import org.araqnid.eventstore.ResolvedEvent
import org.araqnid.eventstore.StreamId
import org.araqnid.eventstore.TestPosition
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.sameInstance
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Comparator.comparing
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Phaser
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SnapshotEventSubscriptionServiceTest {
    @Rule @JvmField val mockito: MockitoRule = MockitoJUnit.rule()

    @Test fun attempts_to_load_snapshot_and_performs_initial_play_on_startup() {
        val sink = mock<PollingEventSubscriptionService.Sink>()
        val subscriptionListener = mock<SnapshotEventSubscriptionService.SubscriptionListener>()
        val snapshotPersister = mock<SnapshotPersister>()
        val serviceListener = mock<Service.Listener>()
        val awaitListener = AwaitListener(serviceListener)

        val clock = Clock.systemUTC()
        val eventSource = InMemoryEventSource(clock)
        val subscription = PollingEventSubscriptionService(eventSource, sink, Duration.ofSeconds(1))
        val snapshotEventSubscriptionService = SnapshotEventSubscriptionService(subscription, snapshotPersister, clock, Duration.ofSeconds(1))
        snapshotEventSubscriptionService.addListener(subscriptionListener, directExecutor())
        snapshotEventSubscriptionService.addListener(awaitListener, directExecutor())

        `when`(snapshotPersister.load()).thenReturn(null)
        snapshotEventSubscriptionService.startAsync().awaitRunning()

        awaitListener.await("running")

        val inOrder = inOrder(snapshotPersister, subscriptionListener, serviceListener, sink)
        inOrder.verify(subscriptionListener).loadingSnapshot()
        inOrder.verify(snapshotPersister).load()
        inOrder.verify(subscriptionListener).noSnapshot()
        inOrder.verify(subscriptionListener).pollStarted(eventSource.storeReader.emptyStorePosition)
        inOrder.verify(subscriptionListener).pollFinished(eventSource.storeReader.emptyStorePosition, 0)
        inOrder.verify(subscriptionListener).initialReplayComplete(eventSource.storeReader.emptyStorePosition)
        inOrder.verify(serviceListener).running()

        verify(snapshotPersister, never()).save()
        verify(subscriptionListener, never()).writingSnapshot()
        verify(subscriptionListener, never()).wroteSnapshot(anyPosition())

        snapshotEventSubscriptionService.stopAsync().awaitTerminated()
        assertThat(subscription.state(), equalTo(Service.State.TERMINATED))
    }

    @Test fun attempts_to_load_snapshot_and_immediately_writes_snapshot() {
        val sink = mock<PollingEventSubscriptionService.Sink>()
        val subscriptionListener = mock<SnapshotEventSubscriptionService.SubscriptionListener>()
        val snapshotPersister = mock<SnapshotPersister>()
        val serviceListener = mock<Service.Listener>()
        val awaitListener = AwaitListener(serviceListener)

        val clock = Clock.systemUTC()
        val eventSource = InMemoryEventSource(clock)
        val subscription = PollingEventSubscriptionService(eventSource, sink, Duration.ofSeconds(1))
        val snapshotEventSubscriptionService = SnapshotEventSubscriptionService(subscription, snapshotPersister, clock, Duration.ofSeconds(1))
        snapshotEventSubscriptionService.addListener(subscriptionListener, directExecutor())
        snapshotEventSubscriptionService.addListener(awaitListener, directExecutor())

        val event1 = writeEvent(eventSource)

        `when`(snapshotPersister.load()).thenReturn(null)
        `when`(snapshotPersister.save()).thenReturn(event1.position)
        snapshotEventSubscriptionService.startAsync().awaitRunning()

        awaitListener.await("running")

        val inOrder = inOrder(snapshotPersister, subscriptionListener, serviceListener, sink)
        inOrder.verify(subscriptionListener).loadingSnapshot()
        inOrder.verify(snapshotPersister).load()
        inOrder.verify(subscriptionListener).noSnapshot()
        inOrder.verify(subscriptionListener).pollStarted(eventSource.storeReader.emptyStorePosition)
        inOrder.verify(sink).accept(event1)
        inOrder.verify(subscriptionListener).pollFinished(event1.position, 1)
        inOrder.verify(snapshotPersister).save()
        inOrder.verify(subscriptionListener).wroteSnapshot(event1.position)
        inOrder.verify(subscriptionListener).initialReplayComplete(event1.position)
        inOrder.verify(serviceListener).running()

        snapshotEventSubscriptionService.stopAsync().awaitTerminated()
        assertThat(subscription.state(), equalTo(Service.State.TERMINATED))
    }

    @Test fun loads_snapshot_and_retains_it_if_no_more_events() {
        val sink = mock<PollingEventSubscriptionService.Sink>()
        val subscriptionListener = mock<SnapshotEventSubscriptionService.SubscriptionListener>()
        val snapshotPersister = mock<SnapshotPersister>()
        val serviceListener = mock<Service.Listener>()

        val clock = Clock.systemUTC()
        val eventSource = InMemoryEventSource(clock)
        val subscription = PollingEventSubscriptionService(eventSource, sink, Duration.ofSeconds(1))
        val snapshotEventSubscriptionService = SnapshotEventSubscriptionService(subscription, snapshotPersister, clock, Duration.ofSeconds(1))
        snapshotEventSubscriptionService.addListener(subscriptionListener, directExecutor())
        snapshotEventSubscriptionService.addListener(serviceListener, directExecutor())

        val event1 = writeEvent(eventSource)

        `when`(snapshotPersister.load()).thenReturn(event1.position)
        snapshotEventSubscriptionService.startAsync().awaitRunning()

        val inOrder = inOrder(snapshotPersister, subscriptionListener, serviceListener, sink)
        inOrder.verify(subscriptionListener).loadingSnapshot()
        inOrder.verify(snapshotPersister).load()
        inOrder.verify(subscriptionListener).loadedSnapshot(event1.position)
        inOrder.verify(subscriptionListener).pollStarted(event1.position)
        inOrder.verify(subscriptionListener).pollFinished(event1.position, 0)
        inOrder.verify(subscriptionListener).initialReplayComplete(event1.position)
        inOrder.verify(serviceListener).running()

        snapshotEventSubscriptionService.stopAsync().awaitTerminated()
        assertThat(subscription.state(), equalTo(Service.State.TERMINATED))
    }

    @Test fun loads_snapshot_and_then_plays_up_to_tip_on_startup() {
        val sink = mock<PollingEventSubscriptionService.Sink>()
        val subscriptionListener = mock<SnapshotEventSubscriptionService.SubscriptionListener>()
        val snapshotPersister = mock<SnapshotPersister>()
        val serviceListener = mock<Service.Listener>()
        val awaitListener = AwaitListener(serviceListener)

        val clock = Clock.systemUTC()
        val eventSource = InMemoryEventSource(clock)
        val subscription = PollingEventSubscriptionService(eventSource, sink, Duration.ofSeconds(1))
        val snapshotEventSubscriptionService = SnapshotEventSubscriptionService(subscription, snapshotPersister, clock, Duration.ofSeconds(1))
        snapshotEventSubscriptionService.addListener(subscriptionListener, directExecutor())
        snapshotEventSubscriptionService.addListener(awaitListener, directExecutor())

        val event1 = writeEvent(eventSource)
        val event2 = writeEvent(eventSource)

        `when`(snapshotPersister.load()).thenReturn(event1.position)
        `when`(snapshotPersister.save()).thenReturn(event2.position)
        snapshotEventSubscriptionService.startAsync().awaitRunning()

        awaitListener.await("running")

        val inOrder = inOrder(snapshotPersister, subscriptionListener, serviceListener, sink)
        inOrder.verify(subscriptionListener).loadingSnapshot()
        inOrder.verify(snapshotPersister).load()
        inOrder.verify(subscriptionListener).loadedSnapshot(event1.position)
        inOrder.verify(subscriptionListener).pollStarted(event1.position)
        inOrder.verify(sink).accept(event2)
        inOrder.verify(subscriptionListener).pollFinished(event2.position, 1)
        inOrder.verify(subscriptionListener).writingSnapshot()
        inOrder.verify(snapshotPersister).save()
        inOrder.verify(subscriptionListener).wroteSnapshot(event2.position)
        inOrder.verify(subscriptionListener).initialReplayComplete(event2.position)
        inOrder.verify(serviceListener).running()

        snapshotEventSubscriptionService.stopAsync().awaitTerminated()
        assertThat(subscription.state(), equalTo(Service.State.TERMINATED))
    }

    @Test fun when_running_event_is_passed_to_sink_then_snapshot_writer_called() {
        val sink = mock<PollingEventSubscriptionService.Sink>()
        val subscriptionListener = mock<SnapshotEventSubscriptionService.SubscriptionListener>()
        val snapshotPersister = mock<SnapshotPersister>()
        val serviceListener = mock<Service.Listener>()

        val clock = Clock.systemUTC()
        val eventSource = InMemoryEventSource(clock)

        val snapshotWrites = Semaphore(0)
        val lastPollPosition = AtomicReference(eventSource.storeReader.emptyStorePosition)

        val subscription = PollingEventSubscriptionService(eventSource, sink, Duration.ofMillis(10L))
        val snapshotEventSubscriptionService = SnapshotEventSubscriptionService(subscription, snapshotPersister, clock, Duration.ofMillis(20L))
        snapshotEventSubscriptionService.addListener(subscriptionListener, directExecutor())
        snapshotEventSubscriptionService.addListener(serviceListener, directExecutor())
        snapshotEventSubscriptionService.addListener(object : SnapshotEventSubscriptionService.SubscriptionListener {
            override fun pollFinished(position: Position, eventsRead: Int) {
                lastPollPosition.set(position)
            }

            override fun wroteSnapshot(position: Position) {
                snapshotWrites.release()
            }

            override fun noSnapshot() {}

            override fun loadingSnapshot() {}

            override fun loadedSnapshot(position: Position) {}

            override fun writingSnapshot() {}

            override fun pollStarted(position: Position) {}
        }, directExecutor())

        `when`(snapshotPersister.load()).thenReturn(null)
        `when`(snapshotPersister.save()).then({ lastPollPosition.get() })

        snapshotEventSubscriptionService.startAsync().awaitRunning()

        val event1 = writeEvent(eventSource)
        if (!snapshotWrites.tryAcquire(1, TimeUnit.SECONDS))
            fail("Timed out waiting for snapshot to be written")

        val inOrder = inOrder(snapshotPersister, subscriptionListener, serviceListener, sink)
        inOrder.verify(subscriptionListener).loadingSnapshot()
        inOrder.verify(snapshotPersister).load()
        inOrder.verify(subscriptionListener).noSnapshot()
        inOrder.verify(subscriptionListener).pollStarted(eventSource.storeReader.emptyStorePosition)
        inOrder.verify(subscriptionListener).pollFinished(eventSource.storeReader.emptyStorePosition, 0)
        inOrder.verify(subscriptionListener).initialReplayComplete(eventSource.storeReader.emptyStorePosition)
        inOrder.verify(serviceListener).running()
        inOrder.verify(subscriptionListener).writingSnapshot()
        inOrder.verify(snapshotPersister).save()
        inOrder.verify(subscriptionListener).wroteSnapshot(event1.position)

        snapshotEventSubscriptionService.stopAsync().awaitTerminated()
        assertThat(subscription.state(), equalTo(Service.State.TERMINATED))
    }

    @Test fun waits_for_snapshot_writer_to_finish_when_shutting_down() {
        val clock = Clock.systemUTC()
        val eventSource = InMemoryEventSource(clock)

        val lastPolledPosition = AtomicReference(eventSource.storeReader.emptyStorePosition)

        val subscriptionListener = object : PollingEventSubscriptionService.SubscriptionListener {
            override fun pollFinished(position: Position, eventsRead: Int) {
                lastPolledPosition.set(position)
            }

            override fun pollStarted(position: Position) {}
        }

        class SnapshotPersisterImpl : SnapshotPersister {
            private val phaser = Phaser(2)
            private val snapshotWritingSemaphore = Semaphore(0)

            override fun load(): Position? {
                return null
            }

            override fun save(): Position {
                phaser.arriveAndAwaitAdvance()
                snapshotWritingSemaphore.acquireUninterruptibly()
                return lastPolledPosition.get()
            }

            fun awaitSnapshotSave() {
                phaser.arriveAndAwaitAdvance()
            }

            fun completeSnapshotSave() {
                snapshotWritingSemaphore.release()
            }
        }

        val snapshotPersister = SnapshotPersisterImpl()

        val sink = mock<PollingEventSubscriptionService.Sink>()
        val serviceListener = mock<Service.Listener>()

        val subscription = PollingEventSubscriptionService(eventSource, sink, Duration.ofMillis(10L))
        subscription.addListener(subscriptionListener, directExecutor())
        val snapshotEventSubscriptionService = SnapshotEventSubscriptionService(subscription, snapshotPersister, clock, Duration.ofMillis(20L))
        snapshotEventSubscriptionService.addListener(serviceListener, directExecutor())

        snapshotEventSubscriptionService.startAsync().awaitRunning()

        writeEvent(eventSource)
        snapshotPersister.awaitSnapshotSave()

        beginStopping(snapshotEventSubscriptionService)
        assertThat(snapshotEventSubscriptionService.state(), equalTo(Service.State.STOPPING))
        subscription.awaitTerminated()

        snapshotPersister.completeSnapshotSave()

        snapshotEventSubscriptionService.awaitTerminated(500, TimeUnit.MILLISECONDS)
        assertThat(subscription.state(), equalTo(Service.State.TERMINATED))
    }

    @Test fun service_fails_without_trying_to_start_subscription_if_loading_snapshot_fails() {
        val sink = mock<PollingEventSubscriptionService.Sink>()
        val subscriptionListener = mock<SnapshotEventSubscriptionService.SubscriptionListener>()
        val snapshotPersister = mock<SnapshotPersister>()
        val serviceListener = mock<Service.Listener>()

        val clock = Clock.systemUTC()
        val eventSource = InMemoryEventSource(clock)
        val subscription = PollingEventSubscriptionService(eventSource, sink, Duration.ofSeconds(1))
        val snapshotEventSubscriptionService = SnapshotEventSubscriptionService(subscription, snapshotPersister, clock, Duration.ofSeconds(1))
        snapshotEventSubscriptionService.addListener(subscriptionListener, directExecutor())
        snapshotEventSubscriptionService.addListener(serviceListener, directExecutor())

        val snapshotFailure = RuntimeException()
        `when`(snapshotPersister.load()).thenThrow(snapshotFailure)
        val expectedFailure = attemptStartExpectingFailure(snapshotEventSubscriptionService)

        assertThat(expectedFailure.cause, sameInstance<Throwable>(snapshotFailure))
        val inOrder = inOrder(snapshotPersister, subscriptionListener, serviceListener, sink)
        inOrder.verify(subscriptionListener).loadingSnapshot()
        inOrder.verify(snapshotPersister).load()

        verifyNoMoreInteractions(snapshotPersister)
        verify(serviceListener, never()).running()
        verify(subscriptionListener, never()).writingSnapshot()
        verify(subscriptionListener, never()).wroteSnapshot(anyPosition())

        assertThat(subscription.state(), equalTo(Service.State.NEW))
    }

    @Test fun service_fails_during_starting_and_stops_subscription_if_snapshot_writer_throws_runtime_exception() {
        val sink = mock<PollingEventSubscriptionService.Sink>()
        val subscriptionListener = mock<SnapshotEventSubscriptionService.SubscriptionListener>()
        val snapshotPersister = mock<SnapshotPersister>()
        val serviceListener = mock<Service.Listener>()
        val awaitListener = AwaitListener(serviceListener)

        val clock = Clock.systemUTC()
        val eventSource = InMemoryEventSource(clock)
        val subscription = PollingEventSubscriptionService(eventSource, sink, Duration.ofSeconds(1))
        val snapshotEventSubscriptionService = SnapshotEventSubscriptionService(subscription, snapshotPersister, clock, Duration.ofSeconds(1))
        snapshotEventSubscriptionService.addListener(subscriptionListener, directExecutor())
        snapshotEventSubscriptionService.addListener(awaitListener, directExecutor())

        val event1 = writeEvent(eventSource)

        `when`(snapshotPersister.load()).thenReturn(null)
        `when`(snapshotPersister.save()).thenThrow(RuntimeException())
        try {
            snapshotEventSubscriptionService.startAsync().awaitRunning()
            fail()
        } catch (e: IllegalStateException) {
            // as expected
        }

        awaitListener.await("failed")

        val inOrder = inOrder(snapshotPersister, subscriptionListener, serviceListener, sink)
        inOrder.verify(subscriptionListener).loadingSnapshot()
        inOrder.verify(snapshotPersister).load()
        inOrder.verify(subscriptionListener).noSnapshot()
        inOrder.verify(subscriptionListener).pollStarted(eventSource.storeReader.emptyStorePosition)
        inOrder.verify(sink).accept(event1)
        inOrder.verify(subscriptionListener).pollFinished(event1.position, 1)
        inOrder.verify(subscriptionListener).writingSnapshot()
        inOrder.verify(snapshotPersister).save()
        inOrder.verify(serviceListener).failed(eq(Service.State.STARTING), anyThrowable())

        assertThat(subscription.state(), equalTo(Service.State.TERMINATED))
    }

    @Test fun service_fails_if_subscription_fails_during_startup() {
        val sink = mock<PollingEventSubscriptionService.Sink>()
        val subscriptionListener = mock<SnapshotEventSubscriptionService.SubscriptionListener>()
        val snapshotPersister = mock<SnapshotPersister>()
        val serviceListener = mock<Service.Listener>()

        val clock = Clock.systemUTC()
        val eventSource = InMemoryEventSource(clock)
        val subscription = PollingEventSubscriptionService(eventSource, sink, Duration.ofSeconds(1))
        val snapshotEventSubscriptionService = SnapshotEventSubscriptionService(subscription, snapshotPersister, clock, Duration.ofSeconds(1))
        snapshotEventSubscriptionService.addListener(subscriptionListener, directExecutor())
        snapshotEventSubscriptionService.addListener(serviceListener, directExecutor())

        val event1 = writeEvent(eventSource)
        val deliveryException = RuntimeException()

        `when`(snapshotPersister.load()).thenReturn(null)
        doThrow(deliveryException).`when`(sink).accept(anyResolvedEvent())

        val expectedFailure = attemptStartExpectingFailure(snapshotEventSubscriptionService)

        assertThat(expectedFailure.cause, sameInstance<Throwable>(deliveryException))

        val inOrder = inOrder(snapshotPersister, subscriptionListener, serviceListener, sink)
        inOrder.verify(subscriptionListener).loadingSnapshot()
        inOrder.verify(snapshotPersister).load()
        inOrder.verify(subscriptionListener).noSnapshot()
        inOrder.verify(subscriptionListener).pollStarted(eventSource.storeReader.emptyStorePosition)
        inOrder.verify(sink).accept(event1)

        verifyNoMoreInteractions(snapshotPersister)

        assertThat(snapshotEventSubscriptionService.state(), equalTo(Service.State.FAILED))
        assertThat(subscription.state(), equalTo(Service.State.FAILED))
    }

    @Test fun service_fails_if_subscription_fails_when_running() {
        val subscriptionListener = mock<SnapshotEventSubscriptionService.SubscriptionListener>()
        val snapshotPersister = mock<SnapshotPersister>()
        val serviceListener = mock<Service.Listener>()

        val clock = Clock.systemUTC()
        val eventSource = InMemoryEventSource(clock)

        val eventConsumptions = Semaphore(0)
        val sink = object : PollingEventSubscriptionService.Sink {
            override fun accept(event: ResolvedEvent) {
                eventConsumptions.release()
                throw RuntimeException("error from sink")
            }
        }

        val subscription = PollingEventSubscriptionService(eventSource, sink, Duration.ofMillis(10L))
        val snapshotEventSubscriptionService = SnapshotEventSubscriptionService(subscription, snapshotPersister, clock, Duration.ofMillis(20L))
        snapshotEventSubscriptionService.addListener(subscriptionListener, directExecutor())
        snapshotEventSubscriptionService.addListener(serviceListener, directExecutor())

        `when`(snapshotPersister.load()).thenReturn(null)

        snapshotEventSubscriptionService.startAsync().awaitRunning()

        val event1 = writeEvent(eventSource)
        if (!eventConsumptions.tryAcquire(1, TimeUnit.SECONDS))
            fail("Timed out waiting for event delivery")

        try {
            snapshotEventSubscriptionService.awaitTerminated(1, TimeUnit.SECONDS)
        } catch (e: IllegalStateException) {
            // as expected
        }

        assertThat(snapshotEventSubscriptionService.state(), equalTo(Service.State.FAILED))
        assertThat(snapshotEventSubscriptionService.failureCause().message, containsString(event1.position.toString()))
        assertThat(snapshotEventSubscriptionService.failureCause().cause?.message, equalTo("error from sink"))
        assertThat(subscription.state(), equalTo(Service.State.FAILED))
    }

    private fun writeEvent(eventSource: InMemoryEventSource): ResolvedEvent {
        val eventsWritten = eventSource.storeReader.readAllForwards(eventSource.storeReader.emptyStorePosition).count()
        eventSource.write(StreamId("test", "test"),
                ImmutableList.of(NewEvent("Test", Blob.fromString(eventsWritten.toString(), UTF_8), Blob.empty)))
        return eventSource.storeReader.readAllForwards(eventSource.storeReader.emptyStorePosition)
                .max(comparing<ResolvedEvent, Position>({ it.position }, { left, right -> eventSource.positionCodec.comparePositions(left, right) }))
                .get()
    }

    private fun beginStopping(service: Service) {
        val reachedStopping = CompletableFuture<Any>()
        service.addListener(object : Service.Listener() {
            override fun stopping(from: Service.State) {
                reachedStopping.complete(null)
            }

            override fun failed(from: Service.State, failure: Throwable) {
                reachedStopping.completeExceptionally(failure)
            }
        }, directExecutor())
        service.stopAsync()
        reachedStopping.join()
    }

    private fun attemptStartExpectingFailure(service: Service): Throwable {
        val expectedFailureFuture = CompletableFuture<Throwable>()
        service.addListener(object : Service.Listener() {
            override fun running() {
                expectedFailureFuture.completeExceptionally(IllegalStateException("Service reached RUNNNING state"))
            }

            override fun failed(from: Service.State, failure: Throwable) {
                expectedFailureFuture.complete(failure)
            }
        }, directExecutor())
        service.startAsync()
        return expectedFailureFuture.join()
    }

    private inline fun <reified T> mock(): T = Mockito.mock(T::class.java)

    private fun anyPosition(): Position {
        Mockito.any(Position::class.java)
        return TestPosition(0)
    }

    private fun anyThrowable(): Throwable {
        Mockito.any(Throwable::class.java)
        return UnsupportedOperationException()
    }

    private fun anyResolvedEvent(): ResolvedEvent {
        Mockito.any(ResolvedEvent::class.java)
        return ResolvedEvent(TestPosition(0), EventRecord(StreamId("",""), 0L, Instant.EPOCH, "", Blob.empty, Blob.empty))
    }

    private class AwaitListener(private val serviceListener: Service.Listener) : Service.Listener() {
        override fun running() {
            serviceListener.running()
            called("running")
        }

        override fun stopping(from: Service.State) {
            serviceListener.stopping(from)
            called("stopping")
        }

        override fun failed(from: Service.State, failure: Throwable) {
            serviceListener.failed(from, failure)
            called("failed")
        }

        override fun terminated(from: Service.State) {
            serviceListener.terminated(from)
            called("terminated")
        }

        override fun starting() {
            serviceListener.starting()
            called("starting")
        }

        private val monitor = Monitor()
        private val methodsCalled = HashSet<String>()

        private fun called(methodName: String) {
            monitor.enter()
            try {
                methodsCalled.add(methodName)
            } finally {
                monitor.leave()
            }
        }

        fun await(methodName: String) {
            if (!monitor.enterWhen(methodHasBeenCalled(methodName), 1, TimeUnit.SECONDS)) {
                throw AssertionFailedError("Listener method never called: $methodName")
            }
            monitor.leave()
        }

        private fun methodHasBeenCalled(methodName: String) = object : Monitor.Guard(monitor) {
            override fun isSatisfied() = methodsCalled.contains(methodName)
        }
    }
}