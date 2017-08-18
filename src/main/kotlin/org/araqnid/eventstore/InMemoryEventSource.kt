package org.araqnid.eventstore

import java.time.Clock
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.stream.Stream

class InMemoryEventSource(val clock: Clock) : EventSource, EventReader, EventCategoryReader, EventStreamReader, EventStreamWriter {
    companion object {
        val codec = positionCodecOfComparable(
                { (index) -> Integer.toString(index) },
                { str -> InMemoryPosition(Integer.parseInt(str)) })
    }

    val content = CopyOnWriteArrayList<ResolvedEvent>()

    override val storeReader: EventReader
        get() = this
    override val categoryReader: EventCategoryReader
        get() = this
    override val streamReader: EventStreamReader
        get() = this
    override val streamWriter: EventStreamWriter
        get() = this
    override val positionCodec: PositionCodec
        get() = codec

    override val emptyStorePosition: Position
        get() = InMemoryPosition(-1)

    override fun emptyCategoryPosition(category: String): Position = emptyStorePosition

    override fun readAllForwards(after: Position): Stream<ResolvedEvent> {
        return content.subList((after as InMemoryPosition).index + 1, content.size).stream()
    }

    override fun readCategoryForwards(category: String, after: Position): Stream<ResolvedEvent> {
        return readAllForwards(after).filter { it.event.streamId.category == category }
    }

    override fun readStreamForwards(streamId: StreamId, after: Long): Stream<ResolvedEvent> {
        if (content.find { it.event.streamId == streamId } == null)
            throw NoSuchStreamException(streamId)
        return content.filter { it.event.streamId == streamId && it.event.eventNumber > after }.stream()
    }

    @Synchronized
    override fun write(streamId: StreamId, events: List<NewEvent>) {
        val timestamp = Instant.now(clock)
        var lastEventNumber = lastEventNumber(streamId)
        events.forEach { ev ->
            content += ev.toEventRecord(streamId, ++lastEventNumber, timestamp).toResolvedEvent(InMemoryPosition(content.size))
        }
    }

    @Synchronized
    override fun write(streamId: StreamId, expectedEventNumber: Long, events: List<NewEvent>) {
        val timestamp = Instant.now(clock)
        var lastEventNumber = lastEventNumber(streamId)
        if (lastEventNumber != expectedEventNumber) throw WrongExpectedVersionException(streamId, lastEventNumber, expectedEventNumber)
        events.forEach { ev ->
            content += ev.toEventRecord(streamId, ++lastEventNumber, timestamp).toResolvedEvent(InMemoryPosition(content.size))
        }
    }

    private fun lastEventNumber(streamId: StreamId): Long = content.filter { it.event.streamId == streamId }.count().toLong() - 1

    private data class InMemoryPosition(val index: Int) : Position, Comparable<InMemoryPosition> {
        override fun compareTo(other: InMemoryPosition): Int = Integer.compare(index, other.index)
    }
}
