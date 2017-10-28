package org.araqnid.eventstore.filesystem.flatpack

import org.araqnid.eventstore.ResolvedEvent
import org.araqnid.eventstore.StreamId
import org.araqnid.eventstore.testutil.JsonEquivalenceMatchers.bytesEquivalentTo
import org.araqnid.eventstore.testutil.NIOTemporaryFolder
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.hamcrest.TypeSafeDiagnosingMatcher
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.streams.toList

class FlatPackFilesystemEventReaderTest {
    @Rule @JvmField
    val folder = NIOTemporaryFolder()

    private val eventReader by lazy { FlatPackFilesystemEventReader(folder.root, Lockable()) }

    @Test fun `empty directory has no events`() {
        assertThat(eventReader.readAllForwards().toList(), empty())
    }

    @Test fun `json file is returned as event`() {
        folder.givenLooseFile("2016-05-20T05:16:58.061Z.category.stream.0.EventType.json", """{ "key": "value" }""")
        assertThat(
                eventReader.readAllForwards().toList(),
                contains(event(StreamId("category", "stream"), "EventType", Instant.parse("2016-05-20T05:16:58.061Z"), 0L, "{ key: 'value' }"))
        )
    }

    @Test fun `json file is not returned when given position as criterion`() {
        folder.givenLooseFile("2016-05-20T05:16:58.061Z.category.stream.0.EventType.json", """{ "key": "value" }""")
        val firstEventPosition = eventReader.readAllForwards().toList()[0].position
        assertThat(eventReader.readAllForwards(firstEventPosition).toList(), empty())
    }

    @Test fun `json files returned in name order`() {
        folder.givenLooseFile("2016-05-20T05:18:58.061Z.category.stream.1.Beta.json", """{ "key": "value" }""")
        folder.givenLooseFile("2016-05-20T05:17:58.061Z.category.stream.0.Alpha.json", """{ "key": "value" }""")
        assertThat(eventReader.readAllForwards().toList(), contains(
                event(StreamId("category", "stream"), "Alpha", Instant.parse("2016-05-20T05:17:58.061Z"), 0L, "{ key: 'value' }"),
                event(StreamId("category", "stream"), "Beta", Instant.parse("2016-05-20T05:18:58.061Z"), 1L, "{ key: 'value' }")
        ))
    }

    @Test fun `events returned from cpio xz archive`() {
        folder.givenPackFile("2016-05-20T05:18:58.061Z.cpio.xz") {
            addEntry("2016-05-20T05:17:58.061Z.category.stream.0.Alpha.json",
                    """{ "key": "value" }""")
            addEntry("2016-05-20T05:18:58.061Z.category.stream.1.Beta.json",
                    """{ "key": "value" }""")
        }
        assertThat(eventReader.readAllForwards().toList(), contains(
                event(StreamId("category", "stream"), "Alpha", Instant.parse("2016-05-20T05:17:58.061Z"), 0L, "{ key: 'value' }"),
                event(StreamId("category", "stream"), "Beta", Instant.parse("2016-05-20T05:18:58.061Z"), 1L, "{ key: 'value' }")
        ))
    }

    @Test fun `events in archive skipped when before start position`() {
        folder.givenPackFile("2016-05-20T05:18:58.061Z.cpio.xz") {
            addEntry("2016-05-20T05:17:58.061Z.category.stream.0.Alpha.json",
                    """{ "key": "value" }""")
            addEntry("2016-05-20T05:18:58.061Z.category.stream.1.Beta.json",
                    """{ "key": "value" }""")
        }
        assertThat(eventReader.readAllForwards(PackedFile("2016-05-20T05:18:58.061Z.cpio.xz", "2016-05-20T05:17:58.061Z.category.stream.0.Alpha.json")).toList(), contains(
                event(StreamId("category", "stream"), "Beta", Instant.parse("2016-05-20T05:18:58.061Z"), 1L, "{ key: 'value' }")
        ))
    }

    @Test fun `events returned from cpio archive and loose files`() {
        folder.givenPackFile("2016-05-20T05:16:58.061Z.cpio.xz") {
            addEntry("2016-05-20T05:16:58.061Z.category.stream.0.Alpha.json",
                    """{ "type": "packed" }""")
        }
        folder.givenLooseFile("2016-05-20T05:17:58.061Z.category.stream.1.Beta.json",
                """{ "type" : "loose" }""")
        assertThat(eventReader.readAllForwards().toList(), contains(
                event(StreamId("category", "stream"), "Alpha", Instant.parse("2016-05-20T05:16:58.061Z"), 0L, "{ type: 'packed' }"),
                event(StreamId("category", "stream"), "Beta", Instant.parse("2016-05-20T05:17:58.061Z"), 1L, "{ type: 'loose' }")
        ))
    }

    @Test fun `events contained in pack files not read from loose files`() {
        folder.givenLooseFile("2016-05-20T05:30:00Z.category.stream.0.Alpha.json", """{ "type": "loose" }""")
        folder.givenLooseFile("2016-05-20T05:31:00Z.category.stream.1.Beta.json", """{ "type": "loose" }""")
        folder.givenPackFile("2016-05-20T05:30:00Z.cpio.xz") {
            addEntry("2016-05-20T05:30:00Z.category.stream.0.Alpha.json",
                    """{ "type": "packed" }""")
        }
        assertThat(eventReader.readAllForwards().toList(), contains(
                event(StreamId("category", "stream"), "Alpha", Instant.parse("2016-05-20T05:30:00Z"), 0L, "{ type: 'packed' }"),
                event(StreamId("category", "stream"), "Beta", Instant.parse("2016-05-20T05:31:00Z"), 1L, "{ type: 'loose' }")
        ))
    }

    private fun event(streamId: StreamId, eventType: String, timestamp: Instant, eventNumber: Long, expectedJson: String): Matcher<ResolvedEvent> {
        return object : TypeSafeDiagnosingMatcher<ResolvedEvent>() {
            private val dataMatcher = bytesEquivalentTo(expectedJson)

            override fun matchesSafely(item: ResolvedEvent, mismatchDescription: Description): Boolean {
                if (item.event.timestamp != timestamp) {
                    mismatchDescription.appendText("timestamp was ").appendValue(item.event.timestamp)
                    return false
                }
                if (item.event.streamId != streamId) {
                    mismatchDescription.appendText("stream id was ").appendValue(item.event.streamId)
                    return false
                }
                if (item.event.type != eventType) {
                    mismatchDescription.appendText("event type was ").appendValue(item.event.streamId)
                    return false
                }
                if (item.event.eventNumber != eventNumber) {
                    mismatchDescription.appendText("event number was ").appendValue(item.event.eventNumber)
                    return false
                }
                mismatchDescription.appendText("data ")
                val dataBytes = item.event.data.read()
                dataMatcher.describeMismatch(dataBytes, mismatchDescription)
                return dataMatcher.matches(dataBytes)
            }

            override fun describeTo(description: Description) {
                description.appendText("event at ").appendValue(timestamp)
                        .appendText(" containing ").appendDescriptionOf(dataMatcher)
            }
        }
    }
}
