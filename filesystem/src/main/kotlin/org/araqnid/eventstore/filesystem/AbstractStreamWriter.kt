package org.araqnid.eventstore.filesystem

import org.araqnid.eventstore.EventStreamWriter
import org.araqnid.eventstore.NewEvent
import org.araqnid.eventstore.StreamId
import org.araqnid.eventstore.WrongExpectedVersionException

internal abstract class AbstractStreamWriter : EventStreamWriter {
    override fun write(streamId: StreamId, events: List<NewEvent>) {
        saveEvents(lastEventNumber(streamId) + 1, streamId, events)
    }

    override fun write(streamId: StreamId, expectedEventNumber: Long, events: List<NewEvent>) {
        val lastEventNumber = lastEventNumber(streamId)
        if (lastEventNumber != expectedEventNumber) throw WrongExpectedVersionException(streamId, lastEventNumber, expectedEventNumber)
        saveEvents(lastEventNumber + 1, streamId, events)
    }

    abstract fun saveEvents(firstEventNumber: Long, streamId: StreamId, events: List<NewEvent>)
    abstract fun lastEventNumber(streamId: StreamId): Long
}