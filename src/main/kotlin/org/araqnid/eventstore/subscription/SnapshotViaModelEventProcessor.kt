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

abstract class SnapshotViaModelEventProcessor<Model>(baseDirectory: Path, objectMapper: ObjectMapper, positionCodec: PositionCodec, emptyPosition: Position, compatibilityVersion: Long, clock: Clock)
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

    var lastPosition = emptyPosition

    override fun accept(event: ResolvedEvent) {
        withWriteLock {
            process(event.event)
            lastPosition = event.position
        }
    }

    override fun loadSnapshotJson(jsonParser: JsonParser, position: Position) {
        withWriteLock {
            load(unmarshalModel(jsonParser), position)
            lastPosition = position
        }
    }

    override fun lockForSave(): PositionLock {
        val(model, position) = withReadLock { Pair(dump(), lastPosition) }

        return object : PositionLock {
            override val position: Position
                get() = position

            override fun saveSnapshotJson(jsonGenerator: JsonGenerator) {
                this@SnapshotViaModelEventProcessor.marshalModel(jsonGenerator, model)
            }

            override fun close() {
            }
        }
    }

    abstract fun load(model: Model, position: Position)
    @Throws(IOException::class)
    abstract fun unmarshalModel(jsonParser: JsonParser): Model

    abstract fun dump(): Model
    @Throws(IOException::class)
    abstract fun marshalModel(jsonGenerator: JsonGenerator, model: Model)

    abstract fun process(eventRecord: EventRecord)
}
