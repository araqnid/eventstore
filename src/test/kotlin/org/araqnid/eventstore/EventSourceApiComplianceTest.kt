package org.araqnid.eventstore

import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.emptyIterable
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeDiagnosingMatcher
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.nio.charset.StandardCharsets.UTF_8
import java.util.stream.Collectors
import java.util.stream.Collectors.maxBy

abstract class EventSourceApiComplianceTest {
    @Rule @JvmField val thrown = ExpectedException.none()!!

    abstract val eventSource: EventSource

    @Test fun read_events_written_to_stream() {
        val streamId = StreamId("alpha", "1")
        val eventA = NewEvent("type-A", Blob.fromString("A-data"), Blob.fromString("A-metadata"))
        val eventB = NewEvent("type-B", Blob.fromString("B-data"), Blob.fromString("B-metadata"))

        eventSource.streamWriter.write(streamId, listOf(eventA, eventB))

        assertThat(eventSource.streamReader.readStreamForwards(streamId).map { it.event }.toListAndClose(),
                contains(eventRecord(streamId, 0L, eventA), eventRecord(streamId, 1L, eventB)))
    }

    @Test fun read_and_write_to_streams_independently() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")

        val eventA = NewEvent("type-A", Blob.fromString("A-data"), Blob.fromString("A-metadata"))
        val eventB = NewEvent("type-B", Blob.fromString("B-data"), Blob.fromString("B-metadata"))

        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))

        assertThat(eventSource.streamReader.readStreamForwards(stream0).map { it.event }.toListAndClose(),
                contains(eventRecord(stream0, 0L, eventA)))

        assertThat(eventSource.streamReader.readStreamForwards(stream1).map { it.event }.toListAndClose(),
                contains(eventRecord(stream1, 0L, eventB)))

        val position1 = eventSource.streamReader.readStreamForwards(stream0).map { it.position }.onlyElement()!!
        val position2 = eventSource.streamReader.readStreamForwards(stream1).map { it.position }.onlyElement()!!
        assertThat(position1, not(equalTo(position2)))
        assertTrue(eventSource.streamReader.positionCodec.comparePositions(position1, position2) < 0)
    }

    @Test fun write_events_specifying_expected_version_number() {
        val streamId = StreamId("alpha", "1")
        eventSource.streamWriter.write(streamId, listOf(NewEvent("type-A", Blob.fromString("A-data"), Blob.fromString("A-metadata"))))

        eventSource.streamWriter.write(streamId, 0, listOf(NewEvent("type-B", Blob.fromString("B-data"), Blob.fromString("B-metadata"))))
    }

    @Test fun write_events_specifying_expected_empty_version_number() {
        eventSource.streamWriter.write(StreamId("alpha", "1"), emptyStreamEventNumber, listOf(NewEvent("type-A", Blob.fromString("A-data"), Blob.fromString("A-metadata"))))
    }

    @Test fun fails_if_expected_event_number_not_satisfied_yet() {
        thrown.expect(WrongExpectedVersionException::class.java)
        eventSource.streamWriter.write(StreamId("alpha", "1"), 0, listOf(NewEvent("type-A", Blob.fromString("A-data"), Blob.fromString("A-metadata"))))
    }

    @Test fun fails_if_expected_event_number_already_passed() {
        val streamId = StreamId("alpha", "1")
        eventSource.streamWriter.write(streamId, listOf(
                NewEvent("type-A", Blob.fromString("A-data"), Blob.fromString("A-metadata")),
                NewEvent("type-B", Blob.fromString("B-data"), Blob.fromString("B-metadata"))))

        thrown.expect(WrongExpectedVersionException::class.java)
        eventSource.streamWriter.write(streamId, 0, listOf(NewEvent("type-C", Blob.fromString("C-data"), Blob.fromString("C-metadata"))))
    }

    @Test fun read_stream_after_specific_event_number() {
        val streamId = StreamId("alpha", "1")
        val eventA = NewEvent("type-A", Blob.fromString("A-data"), Blob.fromString("A-metadata"))
        val eventB = NewEvent("type-B", Blob.fromString("B-data"), Blob.fromString("B-metadata"))

        eventSource.streamWriter.write(streamId, listOf(eventA, eventB))

        assertThat(eventSource.streamReader.readStreamForwards(streamId, 0).map { it.event }.toListAndClose(),
                contains(eventRecord(streamId, 1L, eventB)))
    }

    @Test fun read_empty_after_end_of_stream() {
        val streamId = StreamId("alpha", "1")
        val eventA = NewEvent("type-A", Blob.fromString("A-data"), Blob.fromString("A-metadata"))
        val eventB = NewEvent("type-B", Blob.fromString("B-data"), Blob.fromString("B-metadata"))
        eventSource.streamWriter.write(streamId, listOf(eventA, eventB))

        assertThat(eventSource.streamReader.readStreamForwards(streamId, 1).map { it.event }.toListAndClose(),
                emptyIterable())
    }

    @Test fun read_all_events() {
        val eventA = NewEvent("type-A", Blob.fromString("A-data"), Blob.fromString("A-metadata"))
        val eventB = NewEvent("type-B", Blob.fromString("B-data"), Blob.fromString("B-metadata"))
        val eventC = NewEvent("type-C", Blob.fromString("C-data"), Blob.fromString("C-metadata"))
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")
        val stream2 = StreamId("gamma", "3")
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))

        assertThat(eventSource.storeReader.readAllForwards().map { it.event }.toListAndClose(),
                contains(eventRecord(stream0, 0L, eventA), eventRecord(stream1, 0L, eventB), eventRecord(stream2, 0L, eventC)))
    }

    @Test fun read_all_events_from_position() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")
        val stream2 = StreamId("gamma", "3")
        val eventA = NewEvent("type-A", Blob.fromString("A-data"), Blob.fromString("A-metadata"))
        val eventB = NewEvent("type-B", Blob.fromString("B-data"), Blob.fromString("B-metadata"))
        val eventC = NewEvent("type-C", Blob.fromString("C-data"), Blob.fromString("C-metadata"))
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))

        val position = eventSource.storeReader.readAllForwards().toListAndClose()[1].position

        assertThat(eventSource.storeReader.readAllForwards(position).map { it.event }.toListAndClose(),
                contains(eventRecord(stream2, 0L, eventC)))
    }

    @Test fun read_empty_after_end_of_store() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "2")
        val stream2 = StreamId("gamma", "3")
        val eventA = NewEvent("type-A", Blob.fromString("A-data"), Blob.fromString("A-metadata"))
        val eventB = NewEvent("type-B", Blob.fromString("B-data"), Blob.fromString("B-metadata"))
        val eventC = NewEvent("type-C", Blob.fromString("C-data"), Blob.fromString("C-metadata"))
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))

        val position = eventSource.storeReader.readAllForwards().toListAndClose().last().position

        assertThat(eventSource.storeReader.readAllForwards(position).map { it.event }.toListAndClose(),
                emptyIterable())
    }

    @Test fun read_category_events() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "1")
        val stream2 = StreamId("gamma", "1")
        val stream3 = StreamId("alpha", "2")
        val eventA = NewEvent("type-A", Blob.fromString("A-data"), Blob.fromString("A-metadata"))
        val eventB = NewEvent("type-B", Blob.fromString("B-data"), Blob.fromString("B-metadata"))
        val eventC = NewEvent("type-C", Blob.fromString("C-data"), Blob.fromString("C-metadata"))
        val eventD = NewEvent("type-D", Blob.fromString("D-data"), Blob.fromString("D-metadata"))
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))
        eventSource.streamWriter.write(stream3, listOf(eventD))

        assertThat(eventSource.categoryReader.readCategoryForwards("alpha").map { it.event }.toListAndClose(),
                contains(eventRecord(stream0, 0, eventA), eventRecord(stream3, 0, eventD)))
    }

    @Test fun read_category_events_from_position() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "1")
        val stream2 = StreamId("gamma", "1")
        val stream3 = StreamId("alpha", "2")
        val eventA = NewEvent("type-A", Blob.fromString("A-data"), Blob.fromString("A-metadata"))
        val eventB = NewEvent("type-B", Blob.fromString("B-data"), Blob.fromString("B-metadata"))
        val eventC = NewEvent("type-C", Blob.fromString("C-data"), Blob.fromString("C-metadata"))
        val eventD = NewEvent("type-D", Blob.fromString("D-data"), Blob.fromString("D-metadata"))
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))
        eventSource.streamWriter.write(stream3, listOf(eventD))

        val position = eventSource.storeReader.readAllForwards().limit(1).map { re -> re.position }.collectAndClose(Collectors.maxBy(eventSource.storeReader.positionCodec::comparePositions)).get()

        assertThat(eventSource.categoryReader.readCategoryForwards("alpha", position).map { it.event }.toListAndClose(),
                contains(eventRecord(stream3, 0, eventD)))
    }

    @Test fun read_empty_after_end_of_category() {
        val stream0 = StreamId("alpha", "1")
        val stream1 = StreamId("beta", "1")
        val stream2 = StreamId("gamma", "1")
        val stream3 = StreamId("alpha", "2")
        val eventA = NewEvent("type-A", Blob.fromString("A-data"), Blob.fromString("A-metadata"))
        val eventB = NewEvent("type-B", Blob.fromString("B-data"), Blob.fromString("B-metadata"))
        val eventC = NewEvent("type-C", Blob.fromString("C-data"), Blob.fromString("C-metadata"))
        val eventD = NewEvent("type-D", Blob.fromString("D-data"), Blob.fromString("D-metadata"))
        eventSource.streamWriter.write(stream0, listOf(eventA))
        eventSource.streamWriter.write(stream1, listOf(eventB))
        eventSource.streamWriter.write(stream2, listOf(eventC))
        eventSource.streamWriter.write(stream3, listOf(eventD))

        val position = eventSource.storeReader.readAllForwards().map { re -> re.position }.collectAndClose(maxBy(eventSource.storeReader.positionCodec::comparePositions)).get()

        assertThat(eventSource.categoryReader.readCategoryForwards("alpha", position).map { it.event }.toListAndClose(),
                emptyIterable())
    }
}

private fun eventRecord(streamId: StreamId, eventNumber: Long, asCreated: NewEvent) = eventRecord(streamId, eventNumber, asCreated.type, asCreated.data, asCreated.metadata)

private fun eventRecord(streamId: StreamId, eventNumber: Long, type: String, data: Blob, metadata: Blob): Matcher<EventRecord?> {
    return object : TypeSafeDiagnosingMatcher<EventRecord>() {
        override fun matchesSafely(item: EventRecord, mismatchDescription: Description): Boolean {
            if (item.streamId != streamId) {
                mismatchDescription.appendText("stream id was ").appendValue(item.streamId)
                return false
            }
            if (item.eventNumber != eventNumber) {
                mismatchDescription.appendText("event number was ").appendValue(item.eventNumber)
                return false
            }
            if (item.type != type) {
                mismatchDescription.appendText("type was ").appendValue(item.type)
                return false
            }
            if (item.data != data) {
                mismatchDescription.appendText("data was ").appendValue(pretty(item.data))
                return false
            }
            if (item.metadata != metadata) {
                mismatchDescription.appendText("metadata was ").appendValue(pretty(item.metadata))
                return false
            }
            return true
        }

        override fun describeTo(description: Description) {
            description.appendText("event in stream ").appendValue(streamId)
                    .appendText(" at ").appendValue(eventNumber)
                    .appendText(" of type ").appendValue(type)
                    .appendText(" with data ").appendValue(pretty(data))
                    .appendText(" with metadata ").appendValue(pretty(metadata))
        }

        private fun pretty(blob: Blob): String = blob.asCharSource(UTF_8).read()
    }
}
