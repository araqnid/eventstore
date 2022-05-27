package org.araqnid.eventstore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.datetime.Clock
import java.util.concurrent.CopyOnWriteArrayList

class LocalEventSource(private val clock: Clock = Clock.System) : EventSource, EventReader, EventStreamReader, EventStreamWriter {
    companion object {
        val codec = positionCodecOfComparable(
            { (index) -> index.toString() },
            { str -> LocalPosition(str.toInt()) })
    }

    private val content: MutableList<ResolvedEvent> = CopyOnWriteArrayList()

    override val storeReader: EventReader
        get() = this
    override val streamReader: EventStreamReader
        get() = this
    override val streamWriter: EventStreamWriter
        get() = this
    override val positionCodec: PositionCodec
        get() = codec

    override val emptyStorePosition: Position = LocalPosition(-1)

    override fun readAllForwards(after: Position): Flow<ResolvedEvent> {
        return content.subList((after as LocalPosition).index + 1, content.size).asFlow()
    }

    override fun readStreamForwards(streamId: StreamId, after: Long): Flow<ResolvedEvent> {
        if (content.find { it.event.streamId == streamId } == null)
            throw NoSuchStreamException(streamId)
        return content.filter { it.event.streamId == streamId && it.event.eventNumber > after }.asFlow()
    }

    @Synchronized
    override fun write(streamId: StreamId, events: List<NewEvent>) {
        val timestamp = clock.now()
        var lastEventNumber = lastEventNumber(streamId)
        events.forEach { ev ->
            content += ev.toEventRecord(streamId, ++lastEventNumber, timestamp).toResolvedEvent(LocalPosition(content.size))
        }
    }

    @Synchronized
    override fun write(streamId: StreamId, expectedEventNumber: Long, events: List<NewEvent>) {
        val timestamp = clock.now()
        var lastEventNumber = lastEventNumber(streamId)
        if (lastEventNumber != expectedEventNumber) throw WrongExpectedVersionException(streamId, lastEventNumber, expectedEventNumber)
        events.forEach { ev ->
            content += ev.toEventRecord(streamId, ++lastEventNumber, timestamp).toResolvedEvent(LocalPosition(content.size))
        }
    }

    private fun lastEventNumber(streamId: StreamId): Long = content.filter { it.event.streamId == streamId }.count().toLong() - 1

    private data class LocalPosition(val index: Int) : Position, Comparable<LocalPosition> {
        override fun compareTo(other: LocalPosition): Int = index.compareTo(other.index)
    }
}
