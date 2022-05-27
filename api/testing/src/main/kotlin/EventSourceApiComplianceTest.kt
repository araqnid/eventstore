package org.araqnid.eventstore.testing

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.araqnid.eventstore.*
import org.araqnid.kotlin.assertthat.*
import kotlin.test.Test
import kotlin.test.fail

abstract class EventSourceApiComplianceTest() {
    abstract val eventSource: EventSource

    @Test
    fun read_events_written_to_stream() = runBlocking {
        val streamId = StreamId("alpha", "1")
        val eventA = NewEvent(
            "type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent(
            "type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )

        eventSource.streamWriter.write(streamId, listOf(eventA, eventB))

        assertThat(
            eventSource.streamReader.readStreamForwards(streamId).readEvents(),
            containsInOrder(
                eventRecord(streamId, 0L, eventA),
                eventRecord(streamId, 1L, eventB)
            )
        )
    }

    @Test
    fun read_and_write_to_streams_independently() = runBlocking {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")

        val eventA = NewEvent(
            "type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent(
            "type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )

        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))

        assertThat(
            eventSource.streamReader.readStreamForwards(stream0).readEvents(),
            containsInOrder(
                eventRecord(stream0, 0L, eventA)
            )
        )

        assertThat(
            eventSource.streamReader.readStreamForwards(stream1).readEvents(),
            containsInOrder(
                eventRecord(stream1, 0L, eventB)
            )
        )

        val position1 = eventSource.streamReader.readStreamForwards(stream0).map { it.position }.single()
        val position2 = eventSource.streamReader.readStreamForwards(stream1).map { it.position }.single()
        assertThat(position1, !equalTo(position2))
        assertThat(eventSource.streamReader.positionCodec.comparePositions(position1, position2), lessThan(0))
    }

    @Test
    fun write_events_specifying_expected_version_number() {
        val streamId = StreamId("alpha", "1")
        eventSource.streamWriter.write(
            streamId, listOf(
                NewEvent(
                    "type-A",
                    jsonBlob("A-data"),
                    jsonBlob("A-metadata")
                )
            )
        )

        eventSource.streamWriter.write(
            streamId, 0, listOf(
                NewEvent(
                    "type-B",
                    jsonBlob("B-data"),
                    jsonBlob("B-metadata")
                )
            )
        )
    }

    @Test
    fun write_events_specifying_expected_empty_version_number() {
        eventSource.streamWriter.write(
            StreamId("alpha", "1"),
            emptyStreamEventNumber, listOf(
                NewEvent(
                    "type-A",
                    jsonBlob("A-data"),
                    jsonBlob("A-metadata")
                )
            )
        )
    }

    @Test
    fun fails_if_expected_event_number_not_satisfied_yet() {
        val ex = assertThrows {
            eventSource.streamWriter.write(
                StreamId("alpha", "1"), 0, listOf(
                    NewEvent(
                        "type-A",
                        jsonBlob("A-data"),
                        jsonBlob("A-metadata")
                    )
                )
            )
        }
        assertThat(ex, isWrongExpectedVersionException())
    }

    @Test
    fun fails_if_expected_event_number_already_passed() {
        val streamId = StreamId("alpha", "1")
        eventSource.streamWriter.write(
            streamId, listOf(
                NewEvent(
                    "type-A",
                    jsonBlob("A-data"),
                    jsonBlob("A-metadata")
                ),
                NewEvent(
                    "type-B",
                    jsonBlob("B-data"),
                    jsonBlob("B-metadata")
                )
            )
        )

        val ex = assertThrows {
            eventSource.streamWriter.write(
                streamId, 0, listOf(
                    NewEvent(
                        "type-C",
                        jsonBlob("C-data"),
                        jsonBlob("C-metadata")
                    )
                )
            )
        }
        assertThat(ex, isWrongExpectedVersionException())
    }

    @Test
    fun read_stream_after_specific_event_number() = runBlocking {
        val streamId = StreamId("alpha", "1")
        val eventA = NewEvent(
            "type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent(
            "type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )

        eventSource.streamWriter.write(streamId, listOf(eventA, eventB))

        assertThat(
            eventSource.streamReader.readStreamForwards(streamId, 0).readEvents(),
            containsOnly(
                eventRecord(
                    streamId,
                    1L,
                    eventB
                )
            )
        )
    }

    @Test
    fun read_empty_after_end_of_stream() = runBlocking {
        val streamId = StreamId("alpha", "1")
        val eventA = NewEvent(
            "type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent(
            "type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )
        eventSource.streamWriter.write(streamId, listOf(eventA, eventB))

        assertThat(eventSource.streamReader.readStreamForwards(streamId, 1).readEvents(), emptyCollection)
    }

    @Test
    fun read_all_events() = runBlocking {
        val eventA = NewEvent(
            "type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent(
            "type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )
        val eventC = NewEvent(
            "type-C",
            jsonBlob("C-data"),
            jsonBlob("C-metadata")
        )
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")
        val stream2 = StreamId("gamma", "3")
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))

        assertThat(
            eventSource.storeReader.readAllForwards().readEvents(),
            containsInOrder(
                eventRecord(stream0, 0L, eventA),
                eventRecord(stream1, 0L, eventB),
                eventRecord(stream2, 0L, eventC)
            )
        )
    }

    @Test
    fun read_all_events_from_position() = runBlocking {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")
        val stream2 = StreamId("gamma", "3")
        val eventA = NewEvent(
            "type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent(
            "type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )
        val eventC = NewEvent(
            "type-C",
            jsonBlob("C-data"),
            jsonBlob("C-metadata")
        )
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))

        val position = eventSource.storeReader.readAllForwards().toList()[1].position

        assertThat(
            eventSource.storeReader.readAllForwards(position).readEvents(),
            containsOnly(
                eventRecord(stream2, 0L, eventC)
            )
        )
    }

    @Test
    fun read_empty_after_end_of_store() = runBlocking {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")
        val stream2 = StreamId("gamma", "3")
        val eventA = NewEvent(
            "type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent(
            "type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )
        val eventC = NewEvent(
            "type-C",
            jsonBlob("C-data"),
            jsonBlob("C-metadata")
        )
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))

        val position = eventSource.storeReader.readAllForwards().toList().last().position

        assertThat(eventSource.storeReader.readAllForwards(position).readEvents(), emptyCollection)
    }
}

private fun jsonBlob(data: String) = Blob.fromString("{\"data\":\"$data\"}")

private fun eventRecord(streamId: StreamId, eventNumber: Long, asCreated: NewEvent) = eventRecord(
    streamId,
    eventNumber,
    asCreated.type,
    asCreated.data,
    asCreated.metadata
)

private fun eventRecord(
    streamId: StreamId,
    eventNumber: Long,
    type: String,
    data: Blob,
    metadata: Blob
): Matcher<EventRecord> {
    return has(EventRecord::streamId, equalTo(streamId)) and
            has(EventRecord::eventNumber, equalTo(eventNumber)) and
            has(EventRecord::type, equalTo(type)) and
            has(EventRecord::data, equalTo(data)).describedBy { data.read().toString(Charsets.UTF_8) } and
            has(EventRecord::metadata, equalTo(metadata)).describedBy { metadata.read().toString(Charsets.UTF_8) }
}

private fun isWrongExpectedVersionException(): Matcher<Throwable> {
    return object : Matcher<Throwable> {
        override fun match(actual: Throwable): AssertionResult {
            return if (actual is WrongExpectedVersionException) AssertionResult.Match
            else AssertionResult.Mismatch("is $actual")
        }

        override val description: String
            get() = "is WrongExpectedVersionException"
    }
}

private inline fun assertThrows(crossinline body: () -> Unit): Throwable {
    try {
        body()
        fail("Expected to throw an exception")
    } catch (e: Throwable) {
        return e
    }
}

private suspend fun Flow<ResolvedEvent>.readEvents(): List<EventRecord> = map { it.event }.toList()

private suspend fun <T : Any> Flow<T>.maxWithOrNull(comparator: Comparator<in T>): T? {
    var maxValue: T? = null
    collect { value ->
        maxValue = maxValue?.let {
            if (comparator.compare(value, it) > 0) value else it
        } ?: value
    }
    return maxValue
}
