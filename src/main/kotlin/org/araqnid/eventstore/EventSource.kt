package org.araqnid.eventstore

import java.time.Instant
import java.util.stream.Stream

interface EventSource {
    val storeReader: EventReader
    val categoryReader: EventCategoryReader
    val streamReader: EventStreamReader
    val streamWriter: EventStreamWriter
    val positionCodec: PositionCodec
}

interface EventReader {
    fun readAllForwards(after: Position = emptyStorePosition): Stream<ResolvedEvent>
    val emptyStorePosition: Position
}

interface EventCategoryReader {
    fun readCategoryForwards(category: String, after: Position = emptyCategoryPosition(category)): Stream<ResolvedEvent>
    fun emptyCategoryPosition(category: String): Position
}

interface EventStreamReader {
    @Throws(NoSuchStreamException::class)
    fun readStreamForwards(streamId: StreamId, after: Long = emptyStreamEventNumber): Stream<ResolvedEvent>
}

const val emptyStreamEventNumber: Long = -1

interface EventStreamWriter {
    fun write(streamId: StreamId, events: List<NewEvent>)
    @Throws(WrongExpectedVersionException::class)
    fun write(streamId: StreamId, expectedEventNumber: Long, events: List<NewEvent>)
}

data class StreamId(val category: String, val id: String)
data class ResolvedEvent(val position: Position, val event: EventRecord)
data class EventRecord(val streamId: StreamId, val eventNumber: Long, val timestamp: Instant, val type: String, val data: Blob, val metadata: Blob) {
    fun toResolvedEvent(position: Position) = ResolvedEvent(position, this)
}
data class NewEvent(val type: String, val data: Blob, val metadata: Blob = Blob.empty) {
    fun toEventRecord(streamId: StreamId, eventNumber: Long, timestamp: Instant) = EventRecord(streamId, eventNumber, timestamp, type, data, metadata)
}

class WrongExpectedVersionException(streamId: StreamId, eventNumber: Long, expectedEventNumber: Long) : Exception("Stream $streamId is at version $eventNumber, expected $expectedEventNumber")

class NoSuchStreamException(streamId: StreamId) : RuntimeException("No such stream: " + streamId)
