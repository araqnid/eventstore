package org.araqnid.eventstore.subscription

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import org.araqnid.eventstore.EventRecord
import org.araqnid.eventstore.Position
import org.araqnid.eventstore.PositionCodec
import org.araqnid.eventstore.ResolvedEvent
import java.io.IOException
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

abstract class SnapshotEventProcessor(baseDirectory: Path, objectMapper: ObjectMapper, positionCodec: PositionCodec, compatibilityVersion: Long, clock: Clock)
    : PollingEventSubscriptionService.Sink,
        JsonFileSnapshotPersister(baseDirectory, objectMapper, positionCodec, compatibilityVersion, clock) {

    val lock: ReadWriteLock = ReentrantReadWriteLock()

     inline fun <T> withLock(lock: Lock, block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    inline fun <T> withWriteLock(block:() -> T) = withLock(lock.writeLock(), block)
    inline fun <T> withReadLock(block:() -> T) = withLock(lock.readLock(), block)

    private var lastPosition: Position? = null

    override fun accept(event: ResolvedEvent) {
        withWriteLock {
            process(event.event)
            lastPosition = event.position
        }
    }

    override fun loadSnapshotJson(jsonParser: JsonParser, position: Position) {
        withWriteLock {
            loadSnapshotJson(jsonParser)
            lastPosition = position
        }
    }

    override fun lockForSave(): PositionLock {
        lock.readLock().lock()
        val saveStartedAt = lastPosition!!
        return object : PositionLock {
            override val position: Position
                get() = saveStartedAt

            override fun saveSnapshotJson(jsonGenerator: JsonGenerator) {
                this@SnapshotEventProcessor.saveSnapshotJson(jsonGenerator)
            }

            override fun close() {
                lock.readLock().unlock()
            }
        }
    }

    abstract fun process(eventRecord: EventRecord)
    @Throws(IOException::class)
    abstract fun loadSnapshotJson(jsonParser: JsonParser)
    @Throws(IOException::class)
    abstract fun saveSnapshotJson(jsonGenerator: JsonGenerator)
}
