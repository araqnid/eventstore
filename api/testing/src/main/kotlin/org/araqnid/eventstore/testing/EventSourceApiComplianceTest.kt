package org.araqnid.eventstore.testing

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.araqnid.eventstore.Blob
import org.araqnid.eventstore.EventRecord
import org.araqnid.eventstore.EventSource
import org.araqnid.eventstore.NewEvent
import org.araqnid.eventstore.StreamId
import org.araqnid.eventstore.WrongExpectedVersionException
import org.araqnid.eventstore.emptyStreamEventNumber
import org.araqnid.kotlin.assertthat.Matcher
import org.araqnid.kotlin.assertthat.and
import org.araqnid.kotlin.assertthat.assertThat
import org.araqnid.kotlin.assertthat.containsInOrder
import org.araqnid.kotlin.assertthat.containsOnly
import org.araqnid.kotlin.assertthat.describedBy
import org.araqnid.kotlin.assertthat.emptyCollection
import org.araqnid.kotlin.assertthat.equalTo
import org.araqnid.kotlin.assertthat.has
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.text.Charsets.UTF_8

abstract class EventSourceApiComplianceTest {
    abstract val eventSource: EventSource

    @Test fun read_events_written_to_stream() {
        val streamId = StreamId("alpha", "1")
        val eventA = NewEvent("type-A",
                jsonBlob("A-data"),
                jsonBlob("A-metadata"))
        val eventB = NewEvent("type-B",
                jsonBlob("B-data"),
                jsonBlob("B-metadata"))

        eventSource.streamWriter.write(streamId, listOf(eventA, eventB))

        assertThat(eventSource.streamReader.readStreamForwards(streamId).readEvents(),
                containsInOrder(eventRecord(
                        streamId,
                        0L,
                        eventA), eventRecord(streamId, 1L, eventB)))
    }

    @Test fun read_and_write_to_streams_independently() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")

        val eventA = NewEvent("type-A",
                jsonBlob("A-data"),
                jsonBlob("A-metadata"))
        val eventB = NewEvent("type-B",
                jsonBlob("B-data"),
                jsonBlob("B-metadata"))

        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))

        assertThat(eventSource.streamReader.readStreamForwards(stream0).readEvents(),
                containsInOrder(eventRecord(
                        stream0,
                        0L,
                        eventA)))

        assertThat(eventSource.streamReader.readStreamForwards(stream1).readEvents(),
                containsInOrder(eventRecord(
                        stream1,
                        0L,
                        eventB)))

        runBlocking {
            val position1 = eventSource.streamReader.readStreamForwards(stream0).map { it.position }.single()
            val position2 = eventSource.streamReader.readStreamForwards(stream1).map { it.position }.single()
            assertThat(position1, !equalTo(position2))
            assertTrue(eventSource.streamReader.positionCodec.comparePositions(position1, position2) < 0)
        }
    }

    @Test fun write_events_specifying_expected_version_number() {
        val streamId = StreamId("alpha", "1")
        eventSource.streamWriter.write(streamId, listOf(NewEvent("type-A",
                jsonBlob("A-data"),
                jsonBlob("A-metadata"))))

        eventSource.streamWriter.write(streamId, 0, listOf(NewEvent("type-B",
                jsonBlob("B-data"),
                jsonBlob("B-metadata"))))
    }

    @Test fun write_events_specifying_expected_empty_version_number() {
        eventSource.streamWriter.write(StreamId("alpha", "1"),
                emptyStreamEventNumber, listOf(NewEvent(
                "type-A",
                jsonBlob("A-data"),
                jsonBlob("A-metadata"))))
    }

    @Test fun fails_if_expected_event_number_not_satisfied_yet() {
        assertThrows<WrongExpectedVersionException> {
            eventSource.streamWriter.write(StreamId("alpha", "1"), 0, listOf(
                    NewEvent("type-A",
                            jsonBlob("A-data"),
                            jsonBlob("A-metadata"))))
        }
    }

    @Test fun fails_if_expected_event_number_already_passed() {
        val streamId = StreamId("alpha", "1")
        eventSource.streamWriter.write(streamId, listOf(
                NewEvent("type-A",
                        jsonBlob("A-data"),
                        jsonBlob("A-metadata")),
                NewEvent("type-B",
                        jsonBlob("B-data"),
                        jsonBlob("B-metadata"))))

        assertThrows<WrongExpectedVersionException> {
            eventSource.streamWriter.write(streamId, 0, listOf(NewEvent("type-C",
                    jsonBlob("C-data"),
                    jsonBlob("C-metadata"))))
        }
    }

    @Test fun read_stream_after_specific_event_number() {
        val streamId = StreamId("alpha", "1")
        val eventA = NewEvent("type-A",
                jsonBlob("A-data"),
                jsonBlob("A-metadata"))
        val eventB = NewEvent("type-B",
                jsonBlob("B-data"),
                jsonBlob("B-metadata"))

        eventSource.streamWriter.write(streamId, listOf(eventA, eventB))

        assertThat(eventSource.streamReader.readStreamForwards(streamId, 0).readEvents(),
                containsOnly(eventRecord(
                        streamId,
                        1L,
                        eventB)))
    }

    @Test fun read_empty_after_end_of_stream() {
        val streamId = StreamId("alpha", "1")
        val eventA = NewEvent("type-A",
                jsonBlob("A-data"),
                jsonBlob("A-metadata"))
        val eventB = NewEvent("type-B",
                jsonBlob("B-data"),
                jsonBlob("B-metadata"))
        eventSource.streamWriter.write(streamId, listOf(eventA, eventB))

        assertThat(eventSource.streamReader.readStreamForwards(streamId, 1).readEvents(), emptyCollection)
    }

    @Test fun read_all_events() {
        val eventA = NewEvent("type-A",
                jsonBlob("A-data"),
                jsonBlob("A-metadata"))
        val eventB = NewEvent("type-B",
                jsonBlob("B-data"),
                jsonBlob("B-metadata"))
        val eventC = NewEvent("type-C",
                jsonBlob("C-data"),
                jsonBlob("C-metadata"))
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")
        val stream2 = StreamId("gamma", "3")
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))

        assertThat(eventSource.storeReader.readAllForwards().readEvents(),
                containsInOrder(eventRecord(
                        stream0,
                        0L,
                        eventA),
                        eventRecord(stream1, 0L, eventB),
                        eventRecord(stream2, 0L, eventC)))
    }

    @Test fun read_all_events_from_position() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")
        val stream2 = StreamId("gamma", "3")
        val eventA = NewEvent("type-A",
                jsonBlob("A-data"),
                jsonBlob("A-metadata"))
        val eventB = NewEvent("type-B",
                jsonBlob("B-data"),
                jsonBlob("B-metadata"))
        val eventC = NewEvent("type-C",
                jsonBlob("C-data"),
                jsonBlob("C-metadata"))
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))

        val position = runBlocking { eventSource.storeReader.readAllForwards().toList() }[1].position

        assertThat(eventSource.storeReader.readAllForwards(position).readEvents(),
                containsOnly(eventRecord(
                        stream2,
                        0L,
                        eventC)))
    }

    @Test fun read_empty_after_end_of_store() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")
        val stream2 = StreamId("gamma", "3")
        val eventA = NewEvent("type-A",
                jsonBlob("A-data"),
                jsonBlob("A-metadata"))
        val eventB = NewEvent("type-B",
                jsonBlob("B-data"),
                jsonBlob("B-metadata"))
        val eventC = NewEvent("type-C",
                jsonBlob("C-data"),
                jsonBlob("C-metadata"))
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))

        val position = runBlocking { eventSource.storeReader.readAllForwards().toList().last()  }.position

        assertThat(eventSource.storeReader.readAllForwards(position).readEvents(), emptyCollection)
    }

    @Test fun read_category_events() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "1")
        val stream2 = StreamId("gamma", "1")
        val stream3 = StreamId("alpha", "2")
        val eventA = NewEvent("type-A",
                jsonBlob("A-data"),
                jsonBlob("A-metadata"))
        val eventB = NewEvent("type-B",
                jsonBlob("B-data"),
                jsonBlob("B-metadata"))
        val eventC = NewEvent("type-C",
                jsonBlob("C-data"),
                jsonBlob("C-metadata"))
        val eventD = NewEvent("type-D",
                jsonBlob("D-data"),
                jsonBlob("D-metadata"))
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))
        eventSource.streamWriter.write(stream3, listOf(eventD))

        assertThat(eventSource.categoryReader.readCategoryForwards("alpha").readEvents(),
                containsInOrder(eventRecord(
                        stream0,
                        0,
                        eventA), eventRecord(stream3, 0, eventD)))
    }

    @Test fun read_category_events_from_position() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "1")
        val stream2 = StreamId("gamma", "1")
        val stream3 = StreamId("alpha", "2")
        val eventA = NewEvent("type-A",
                jsonBlob("A-data"),
                jsonBlob("A-metadata"))
        val eventB = NewEvent("type-B",
                jsonBlob("B-data"),
                jsonBlob("B-metadata"))
        val eventC = NewEvent("type-C",
                jsonBlob("C-data"),
                jsonBlob("C-metadata"))
        val eventD = NewEvent("type-D",
                jsonBlob("D-data"),
                jsonBlob("D-metadata"))
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))
        eventSource.streamWriter.write(stream3, listOf(eventD))

        val position = runBlocking {
            eventSource.storeReader.readAllForwards().first().position
        }

        assertThat(eventSource.categoryReader.readCategoryForwards("alpha", position).readEvents(),
                containsOnly(eventRecord(
                        stream3,
                        0,
                        eventD)))
    }

    @Test fun read_empty_after_end_of_category() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "1")
        val stream2 = StreamId("gamma", "1")
        val stream3 = StreamId("alpha", "2")
        val eventA = NewEvent("type-A",
                jsonBlob("A-data"),
                jsonBlob("A-metadata"))
        val eventB = NewEvent("type-B",
                jsonBlob("B-data"),
                jsonBlob("B-metadata"))
        val eventC = NewEvent("type-C",
                jsonBlob("C-data"),
                jsonBlob("C-metadata"))
        val eventD = NewEvent("type-D",
                jsonBlob("D-data"),
                jsonBlob("D-metadata"))
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))
        eventSource.streamWriter.write(stream3, listOf(eventD))

        val position = runBlocking {
            eventSource.storeReader.readAllForwards().map { re -> re.position }
                .maxWith(Comparator(eventSource.storeReader.positionCodec::comparePositions))!!
        }

        assertThat(eventSource.categoryReader.readCategoryForwards("alpha", position).readEvents(), emptyCollection)
    }
}

private fun jsonBlob(data: String) = Blob.fromString("{\"data\":\"$data\"}")

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
            has(EventRecord::data, equalTo(data)).describedBy { data.pretty } and
            has(EventRecord::metadata, equalTo(metadata)).describedBy { metadata.pretty }
}

private val Blob.pretty: String
    get() = asCharSource(UTF_8).read()

private inline fun <reified X : Throwable> assertThrows(crossinline body: () -> Unit) {
    Assert.assertThrows(X::class.java) { body() }
}
