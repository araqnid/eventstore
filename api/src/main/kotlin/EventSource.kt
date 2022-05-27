package org.araqnid.eventstore

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface EventSource {
    val storeReader: EventReader
    val streamReader: EventStreamReader
    val streamWriter: EventStreamWriter
}

interface EventReader {
    fun readAllForwards(after: Position = emptyStorePosition): Flow<ResolvedEvent>
    val emptyStorePosition: Position
    val positionCodec: PositionCodec
}

interface EventStreamReader {
    @Throws(NoSuchStreamException::class)
    fun readStreamForwards(streamId: StreamId, after: Long = emptyStreamEventNumber): Flow<ResolvedEvent>
    val positionCodec: PositionCodec
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

class NoSuchStreamException(streamId: StreamId) : RuntimeException("No such stream: $streamId")
