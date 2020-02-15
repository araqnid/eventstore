package org.araqnid.eventstore.subscription

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.araqnid.eventstore.Position
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.allOf
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletableFuture.supplyAsync
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

class SnapshotEventSubscriptionService(val subscription: PollingEventSubscriptionService,
                                       val snapshotPersister: SnapshotPersister,
                                       clock: Clock,
                                       snapshotInterval: Duration) : AbstractService() {
    private val listeners = ListenerSet<SubscriptionListener>()
    private val emitter = listeners.proxy(SubscriptionListener::class.java)
    private val snapshotPersistenceExecutor = Executors.newSingleThreadExecutor(ThreadFactoryBuilder().setNameFormat(snapshotPersister.javaClass.simpleName).build())
    private var writingSnapshot: CompletableFuture<Position>? = null
    private val snapshotTrigger = SnapshotTrigger(subscription.eventReader.positionCodec, snapshotInterval, snapshotInterval.dividedBy(4), snapshotInterval.multipliedBy(4), clock)

    interface SubscriptionListener : PollingEventSubscriptionService.SubscriptionListener {
        fun noSnapshot() {}
        fun loadingSnapshot() {}
        fun loadedSnapshot(position: Position) {}
        fun writingSnapshot() {}
        fun wroteSnapshot(position: Position) {}
        fun initialReplayComplete(position: Position) {}
    }

    fun addListener(listener: SubscriptionListener, executor: Executor) {
        listeners.addListener(listener, executor)
    }

    override fun doStart() {
        emitter.loadingSnapshot()
        supplyAsync(Supplier { snapshotPersister.load() }, snapshotPersistenceExecutor.withThreadNameSuffix("LOAD"))
                .thenAccept { position ->
                    if (position != null) {
                        subscription.startPosition = position
                        emitter.loadedSnapshot(position)
                        snapshotTrigger.snapshotLoaded(position)
                    }
                    else {
                        emitter.noSnapshot()
                    }
                    subscription.addListener(object : Service.Listener() {
                        override fun failed(from: Service.State, failure: Throwable) {
                            notifyFailed(failure)
                        }
                    }, directExecutor())
                    subscription.addListener(PropagateSubscriptionNotifications(), directExecutor())
                    subscription.addListener(ConsiderWritingSnapshotAfterPolling(), directExecutor())
                    subscription.startAsync()
                    notifyStarted()
                }
                .propagateExceptionAsServiceFailure()
    }

    override fun doStop() {
        allOf(subscriptionStop(), writeSnapshotStop())
                .thenRunAsync({ shutdownAndAwaitTermination(snapshotPersistenceExecutor, 1, TimeUnit.SECONDS) },
                        { command -> Thread(command, "SnapshotEventSubscriptionService shutdown").start()})
                .thenRun(this::notifyStopped)
                .propagateExceptionAsServiceFailure()
    }

    @Synchronized
    private fun maybeWriteSnapshot(writeInitialSnapshot: Boolean): CompletableFuture<*> {
        if (writingSnapshot != null)
            return completedFuture(null)
        if (state() != Service.State.STARTING && state() != Service.State.RUNNING)
            return completedFuture(null)
        if (writeInitialSnapshot) {
            if (!snapshotTrigger.writeInitialSnapshot())
                return completedFuture(null)
        }
        else {
            if (!snapshotTrigger.writeNewSnapshot())
                return completedFuture(null)
        }
        return startWritingSnapshot()
    }

    @Synchronized
    private fun startWritingSnapshot(): CompletableFuture<Position> {
        emitter.writingSnapshot()
        val future = supplyAsync(Supplier { snapshotPersister.save() }, snapshotPersistenceExecutor.withThreadNameSuffix("SAVE"))
                .whenComplete(this::whenSnapshotWritten)
        writingSnapshot = future
        return future
    }

    private fun whenSnapshotWritten(savedAt: Position?, ex: Throwable?) {
        if (ex != null) {
            subscription.addListener(object : Service.Listener() {
                override fun terminated(from: Service.State) {
                    notifyFailed(ex)
                }

                override fun failed(from: Service.State, failure: Throwable) {
                    ex.addSuppressed(failure)
                    notifyFailed(ex)
                }
            }, directExecutor())
            subscription.stopAsync()
        }
        else if (savedAt != null) {
            emitter.wroteSnapshot(savedAt)
            snapshotTrigger.snapshotWritten(savedAt)
        }
        else {
            notifyFailed(IllegalStateException("Both savedAt and ex were null"))
        }
        clearWritingSnapshot()
    }

    @Synchronized
    private fun clearWritingSnapshot() {
        writingSnapshot = null
    }

    private fun subscriptionStop(): CompletableFuture<*> {
        val promise = CompletableFuture<Any>()
        subscription.addListener(object : Service.Listener() {
            override fun terminated(from: Service.State) {
                promise.complete(null)
            }
        }, directExecutor())
        subscription.stopAsync()
        return promise
    }

    @Synchronized
    private fun writeSnapshotStop(): CompletableFuture<*> = writingSnapshot ?: completedFuture(null)

    inner class ConsiderWritingSnapshotAfterPolling : PollingEventSubscriptionService.SubscriptionListener {
        private val doneInitialReplayNotification = AtomicBoolean()

        override fun pollFinished(position: Position, eventsRead: Int) {
            if (eventsRead > 0)
                snapshotTrigger.eventReceived(position)
            val doneNotify = doneInitialReplayNotification.getAndSet(true)
            if (doneNotify) {
                maybeWriteSnapshot(false)
            }
            else {
                maybeWriteSnapshot(true).thenRun {
                    listeners.emit { it.initialReplayComplete(position) }
                }
            }
        }
    }

    inner class PropagateSubscriptionNotifications : PollingEventSubscriptionService.SubscriptionListener {
        override fun pollStarted(position: Position) {
            emitter.pollStarted(position)
        }

        override fun pollFinished(position: Position, eventsRead: Int) {
            emitter.pollFinished(position, eventsRead)
        }
    }

    private fun <T> CompletableFuture<T>.propagateExceptionAsServiceFailure(): CompletableFuture<T> {
        return whenComplete { _, ex: Throwable? ->
            if (ex != null) notifyFailed(ex)
        }
    }

    private fun Executor.withThreadNameSuffix(suffix: String): Executor = Executor { command: Runnable ->
        execute {
            val baseName = Thread.currentThread().name!!
            Thread.currentThread().name = "$baseName $suffix"
            try {
                command.run()
            } finally {
                Thread.currentThread().name = baseName
            }
        }
    }
}
