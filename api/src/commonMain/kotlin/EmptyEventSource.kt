package org.araqnid.eventstore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

object EmptyEventSource : EventSource, EventReader, EventCategoryReader, EventStreamReader {
    private object EmptyPosition : Position, Comparable<EmptyPosition> {
        override fun compareTo(other: EmptyPosition): Int = 0
        override fun equals(other: Any?): Boolean = other is EmptyPosition
        override fun hashCode(): Int = 0
        override fun toString(): String = "EmptyPosition"
    }

    override val positionCodec: PositionCodec = positionCodecOfComparable(
            { "" },
            { EmptyPosition }
    )

    override val storeReader: EventReader
        get() = this
    override val categoryReader: EventCategoryReader
        get() = this
    override val streamReader: EventStreamReader
        get() = this
    override val streamWriter: EventStreamWriter
        get() = throw UnsupportedOperationException()

    override fun readAllForwards(after: Position): Flow<ResolvedEvent> = emptyFlow()
    override val emptyStorePosition: Position
        get() = EmptyPosition

    override fun readCategoryForwards(category: String, after: Position): Flow<ResolvedEvent> = emptyFlow()
    override fun emptyCategoryPosition(category: String): Position = EmptyPosition

    override fun readStreamForwards(streamId: StreamId, after: Long): Flow<ResolvedEvent> = emptyFlow()
}
