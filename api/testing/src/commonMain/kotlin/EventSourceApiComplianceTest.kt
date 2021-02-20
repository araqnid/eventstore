package org.araqnid.eventstore.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.araqnid.eventstore.Blob
import org.araqnid.eventstore.EventRecord
import org.araqnid.eventstore.EventSource
import org.araqnid.eventstore.NewEvent
import org.araqnid.eventstore.ResolvedEvent
import org.araqnid.eventstore.StreamId
import org.araqnid.eventstore.emptyStreamEventNumber
import org.araqnid.eventstore.isWrongExpectedVersionException
import org.araqnid.eventstore.readUTF8
import org.araqnid.eventstore.toBlob
import org.araqnid.kotlin.assertthat.Matcher
import org.araqnid.kotlin.assertthat.and
import org.araqnid.kotlin.assertthat.assertThat
import org.araqnid.kotlin.assertthat.containsInOrder
import org.araqnid.kotlin.assertthat.containsOnly
import org.araqnid.kotlin.assertthat.describedBy
import org.araqnid.kotlin.assertthat.emptyCollection
import org.araqnid.kotlin.assertthat.equalTo
import org.araqnid.kotlin.assertthat.has
import org.araqnid.kotlin.assertthat.lessThan
import kotlin.test.fail

expect abstract class EventSourceApiComplianceTest {
    abstract val eventSource: EventSource
}

class ComplianceTestImplementations(private val eventSource: EventSource) {
    suspend fun read_events_written_to_stream() {
        val streamId = StreamId("alpha", "1")
        val eventA = NewEvent("type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent("type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )

        eventSource.streamWriter.write(streamId, listOf(eventA, eventB))

        assertThat(eventSource.streamReader.readStreamForwards(streamId).readEvents(),
            containsInOrder(
                eventRecord(
                    streamId,
                    0L,
                    eventA), eventRecord(streamId, 1L, eventB)
            )
        )
    }

    suspend fun read_and_write_to_streams_independently() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")

        val eventA = NewEvent("type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent("type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )

        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))

        assertThat(eventSource.streamReader.readStreamForwards(stream0).readEvents(),
            containsInOrder(
                eventRecord(
                    stream0,
                    0L,
                    eventA)
            )
        )

        assertThat(eventSource.streamReader.readStreamForwards(stream1).readEvents(),
            containsInOrder(
                eventRecord(
                    stream1,
                    0L,
                    eventB)
            )
        )

        val position1 = eventSource.streamReader.readStreamForwards(stream0).map { it.position }.single()
        val position2 = eventSource.streamReader.readStreamForwards(stream1).map { it.position }.single()
        assertThat(position1, !equalTo(position2))
        assertThat(eventSource.streamReader.positionCodec.comparePositions(position1, position2), lessThan(0))
    }

    suspend fun write_events_specifying_expected_version_number() {
        val streamId = StreamId("alpha", "1")
        eventSource.streamWriter.write(streamId, listOf(
            NewEvent("type-A",
                jsonBlob("A-data"),
                jsonBlob("A-metadata")
            )
        ))

        eventSource.streamWriter.write(streamId, 0, listOf(
            NewEvent("type-B",
                jsonBlob("B-data"),
                jsonBlob("B-metadata")
            )
        ))
    }

    suspend fun write_events_specifying_expected_empty_version_number() {
        eventSource.streamWriter.write(
            StreamId("alpha", "1"),
            emptyStreamEventNumber, listOf(
                NewEvent(
                    "type-A",
                    jsonBlob("A-data"),
                    jsonBlob("A-metadata")
                )
            ))
    }

    suspend fun fails_if_expected_event_number_not_satisfied_yet() {
        val ex = assertThrows {
            eventSource.streamWriter.write(
                StreamId("alpha", "1"), 0, listOf(
                    NewEvent("type-A",
                        jsonBlob("A-data"),
                        jsonBlob("A-metadata")
                    )
                ))
        }
        assertThat(ex, has(Throwable::isWrongExpectedVersionException, equalTo(true)))
    }

    suspend fun fails_if_expected_event_number_already_passed() {
        val streamId = StreamId("alpha", "1")
        eventSource.streamWriter.write(streamId, listOf(
            NewEvent("type-A",
                jsonBlob("A-data"),
                jsonBlob("A-metadata")
            ),
            NewEvent("type-B",
                jsonBlob("B-data"),
                jsonBlob("B-metadata")
            )
        ))

        val ex = assertThrows {
            eventSource.streamWriter.write(streamId, 0, listOf(
                NewEvent("type-C",
                    jsonBlob("C-data"),
                    jsonBlob("C-metadata")
                )
            ))
        }
        assertThat(ex, has(Throwable::isWrongExpectedVersionException, equalTo(true)))
    }

    suspend fun read_stream_after_specific_event_number() {
        val streamId = StreamId("alpha", "1")
        val eventA = NewEvent("type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent("type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )

        eventSource.streamWriter.write(streamId, listOf(eventA, eventB))

        assertThat(eventSource.streamReader.readStreamForwards(streamId, 0).readEvents(),
            containsOnly(
                eventRecord(
                    streamId,
                    1L,
                    eventB)
            )
        )
    }

    suspend fun read_empty_after_end_of_stream() {
        val streamId = StreamId("alpha", "1")
        val eventA = NewEvent("type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent("type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )
        eventSource.streamWriter.write(streamId, listOf(eventA, eventB))

        assertThat(eventSource.streamReader.readStreamForwards(streamId, 1).readEvents(), emptyCollection)
    }

    suspend fun read_all_events() {
        val eventA = NewEvent("type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent("type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )
        val eventC = NewEvent("type-C",
            jsonBlob("C-data"),
            jsonBlob("C-metadata")
        )
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")
        val stream2 = StreamId("gamma", "3")
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))

        assertThat(eventSource.storeReader.readAllForwards().readEvents(),
            containsInOrder(
                eventRecord(
                    stream0,
                    0L,
                    eventA),
                eventRecord(stream1, 0L, eventB),
                eventRecord(stream2, 0L, eventC)
            )
        )
    }

    suspend fun read_all_events_from_position() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")
        val stream2 = StreamId("gamma", "3")
        val eventA = NewEvent("type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent("type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )
        val eventC = NewEvent("type-C",
            jsonBlob("C-data"),
            jsonBlob("C-metadata")
        )
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))

        val position = eventSource.storeReader.readAllForwards().toList()[1].position

        assertThat(eventSource.storeReader.readAllForwards(position).readEvents(),
            containsOnly(
                eventRecord(
                    stream2,
                    0L,
                    eventC)
            )
        )
    }

    suspend fun read_empty_after_end_of_store() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")
        val stream2 = StreamId("gamma", "3")
        val eventA = NewEvent("type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent("type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )
        val eventC = NewEvent("type-C",
            jsonBlob("C-data"),
            jsonBlob("C-metadata")
        )
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))

        val position = eventSource.storeReader.readAllForwards().toList().last().position

        assertThat(eventSource.storeReader.readAllForwards(position).readEvents(), emptyCollection)
    }

    suspend fun read_category_events() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "1")
        val stream2 = StreamId("gamma", "1")
        val stream3 = StreamId("alpha", "2")
        val eventA = NewEvent("type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent("type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )
        val eventC = NewEvent("type-C",
            jsonBlob("C-data"),
            jsonBlob("C-metadata")
        )
        val eventD = NewEvent("type-D",
            jsonBlob("D-data"),
            jsonBlob("D-metadata")
        )
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))
        eventSource.streamWriter.write(stream3, listOf(eventD))

        assertThat(eventSource.categoryReader.readCategoryForwards("alpha").readEvents(),
            containsInOrder(
                eventRecord(
                    stream0,
                    0,
                    eventA), eventRecord(stream3, 0, eventD)
            )
        )
    }

    suspend fun read_category_events_from_position() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "1")
        val stream2 = StreamId("gamma", "1")
        val stream3 = StreamId("alpha", "2")
        val eventA = NewEvent("type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent("type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )
        val eventC = NewEvent("type-C",
            jsonBlob("C-data"),
            jsonBlob("C-metadata")
        )
        val eventD = NewEvent("type-D",
            jsonBlob("D-data"),
            jsonBlob("D-metadata")
        )
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))
        eventSource.streamWriter.write(stream3, listOf(eventD))

        val position = eventSource.storeReader.readAllForwards().first().position

        assertThat(eventSource.categoryReader.readCategoryForwards("alpha", position).readEvents(),
            containsOnly(
                eventRecord(
                    stream3,
                    0,
                    eventD)
            )
        )
    }

    suspend fun read_empty_after_end_of_category() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "1")
        val stream2 = StreamId("gamma", "1")
        val stream3 = StreamId("alpha", "2")
        val eventA = NewEvent("type-A",
            jsonBlob("A-data"),
            jsonBlob("A-metadata")
        )
        val eventB = NewEvent("type-B",
            jsonBlob("B-data"),
            jsonBlob("B-metadata")
        )
        val eventC = NewEvent("type-C",
            jsonBlob("C-data"),
            jsonBlob("C-metadata")
        )
        val eventD = NewEvent("type-D",
            jsonBlob("D-data"),
            jsonBlob("D-metadata")
        )
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))
        eventSource.streamWriter.write(stream3, listOf(eventD))

        val position = eventSource.storeReader.readAllForwards().map { re -> re.position }
            .maxWithOrNull(Comparator(eventSource.storeReader.positionCodec::comparePositions))!!

        assertThat(eventSource.categoryReader.readCategoryForwards("alpha", position).readEvents(), emptyCollection)
    }
}

private fun jsonBlob(data: String) = "{\"data\":\"$data\"}".toBlob()

private fun eventRecord(streamId: StreamId, eventNumber: Long, asCreated: NewEvent) = eventRecord(
    streamId,
    eventNumber,
    asCreated.type,
    asCreated.data,
    asCreated.metadata)

private fun eventRecord(streamId: StreamId, eventNumber: Long, type: String, data: Blob, metadata: Blob): Matcher<EventRecord> {
    return has(EventRecord::streamId, equalTo(streamId)) and
            has(EventRecord::eventNumber, equalTo(eventNumber)) and
            has(EventRecord::type, equalTo(type)) and
            has(EventRecord::data, equalTo(data)).describedBy { data.readUTF8() } and
            has(EventRecord::metadata, equalTo(metadata)).describedBy { metadata.readUTF8() }
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
