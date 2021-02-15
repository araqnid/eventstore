package org.araqnid.eventstore.filesystem.flatpack

import org.araqnid.eventstore.Blob
import org.araqnid.eventstore.EventRecord
import org.araqnid.eventstore.ResolvedEvent
import org.araqnid.eventstore.StreamId
import org.araqnid.eventstore.filesystem.bytesEquivalentTo
import org.araqnid.eventstore.testing.blockingToList
import org.araqnid.eventstore.testutil.NIOTemporaryFolder
import org.araqnid.kotlin.assertthat.Matcher
import org.araqnid.kotlin.assertthat.and
import org.araqnid.kotlin.assertthat.assertThat
import org.araqnid.kotlin.assertthat.containsInOrder
import org.araqnid.kotlin.assertthat.containsOnly
import org.araqnid.kotlin.assertthat.emptyCollection
import org.araqnid.kotlin.assertthat.equalTo
import org.araqnid.kotlin.assertthat.has
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class FlatPackFilesystemEventReaderTest {
    @get:Rule
    val folder = NIOTemporaryFolder()

    private val eventReader by lazy { FlatPackFilesystemEventReader(folder.root, Lockable()) }

    @Test fun `empty directory has no events`() {
        assertThat(eventReader.readAllForwards().blockingToList(), emptyCollection)
    }

    @Test fun `json file is returned as event`() {
        folder.givenLooseFile("2016-05-20T05:16:58.061Z.category.stream.0.EventType.json", """{ "key": "value" }""")
        assertThat(
                eventReader.readAllForwards().blockingToList(),
                containsOnly(event(StreamId("category", "stream"), "EventType", Instant.parse("2016-05-20T05:16:58.061Z"), 0L, "{ key: \"value\" }"))
        )
    }

    @Test fun `json file is not returned when given position as criterion`() {
        folder.givenLooseFile("2016-05-20T05:16:58.061Z.category.stream.0.EventType.json", """{ "key": "value" }""")
        val firstEventPosition = eventReader.readAllForwards().blockingToList()[0].position
        assertThat(eventReader.readAllForwards(firstEventPosition).blockingToList(), emptyCollection)
    }

    @Test fun `json files returned in name order`() {
        folder.givenLooseFile("2016-05-20T05:18:58.061Z.category.stream.1.Beta.json", """{ "key": "value" }""")
        folder.givenLooseFile("2016-05-20T05:17:58.061Z.category.stream.0.Alpha.json", """{ "key": "value" }""")
        assertThat(eventReader.readAllForwards().blockingToList(), containsInOrder(
                event(StreamId("category", "stream"), "Alpha", Instant.parse("2016-05-20T05:17:58.061Z"), 0L, "{ key: \"value\" }"),
                event(StreamId("category", "stream"), "Beta", Instant.parse("2016-05-20T05:18:58.061Z"), 1L, "{ key: \"value\" }")
        ))
    }

    @Test fun `events returned from cpio xz archive`() {
        folder.givenPackFile("2016-05-20T05:18:58.061Z.cpio.xz") {
            addEntry("2016-05-20T05:17:58.061Z.category.stream.0.Alpha.json",
                    """{ "key": "value" }""")
            addEntry("2016-05-20T05:18:58.061Z.category.stream.1.Beta.json",
                    """{ "key": "value" }""")
        }
        assertThat(eventReader.readAllForwards().blockingToList(), containsInOrder(
                event(StreamId("category", "stream"), "Alpha", Instant.parse("2016-05-20T05:17:58.061Z"), 0L, "{ key: \"value\" }"),
                event(StreamId("category", "stream"), "Beta", Instant.parse("2016-05-20T05:18:58.061Z"), 1L, "{ key: \"value\" }")
        ))
    }

    @Test fun `events in archive skipped when before start position`() {
        folder.givenPackFile("2016-05-20T05:18:58.061Z.cpio.xz") {
            addEntry("2016-05-20T05:17:58.061Z.category.stream.0.Alpha.json",
                    """{ "key": "value" }""")
            addEntry("2016-05-20T05:18:58.061Z.category.stream.1.Beta.json",
                    """{ "key": "value" }""")
        }
        assertThat(eventReader.readAllForwards(PackedFile("2016-05-20T05:18:58.061Z.cpio.xz", "2016-05-20T05:17:58.061Z.category.stream.0.Alpha.json")).blockingToList(), containsOnly(
                event(StreamId("category", "stream"), "Beta", Instant.parse("2016-05-20T05:18:58.061Z"), 1L, "{ key: \"value\" }")
        ))
    }

    @Test fun `events returned from cpio archive and loose files`() {
        folder.givenPackFile("2016-05-20T05:16:58.061Z.cpio.xz") {
            addEntry("2016-05-20T05:16:58.061Z.category.stream.0.Alpha.json",
                    """{ "type": "packed" }""")
        }
        folder.givenLooseFile("2016-05-20T05:17:58.061Z.category.stream.1.Beta.json",
                """{ "type" : "loose" }""")
        assertThat(eventReader.readAllForwards().blockingToList(), containsInOrder(
                event(StreamId("category", "stream"), "Alpha", Instant.parse("2016-05-20T05:16:58.061Z"), 0L, "{ type: \"packed\" }"),
                event(StreamId("category", "stream"), "Beta", Instant.parse("2016-05-20T05:17:58.061Z"), 1L, "{ type: \"loose\" }")
        ))
    }

    @Test fun `events contained in pack files not read from loose files`() {
        folder.givenLooseFile("2016-05-20T05:30:00Z.category.stream.0.Alpha.json", """{ "type": "loose" }""")
        folder.givenLooseFile("2016-05-20T05:31:00Z.category.stream.1.Beta.json", """{ "type": "loose" }""")
        folder.givenPackFile("2016-05-20T05:30:00Z.cpio.xz") {
            addEntry("2016-05-20T05:30:00Z.category.stream.0.Alpha.json",
                    """{ "type": "packed" }""")
        }
        assertThat(eventReader.readAllForwards().blockingToList(), containsInOrder(
                event(StreamId("category", "stream"), "Alpha", Instant.parse("2016-05-20T05:30:00Z"), 0L, "{ type: \"packed\" }"),
                event(StreamId("category", "stream"), "Beta", Instant.parse("2016-05-20T05:31:00Z"), 1L, "{ type: \"loose\" }")
        ))
    }

    private fun event(streamId: StreamId, eventType: String, timestamp: Instant, eventNumber: Long, expectedJson: String): Matcher<ResolvedEvent> {
        val record = has(EventRecord::streamId, equalTo(streamId)) and
                has(EventRecord::eventNumber, equalTo(eventNumber)) and
                has(EventRecord::type, equalTo(eventType)) and
                has(EventRecord::data, has(Blob::content, bytesEquivalentTo(expectedJson)))

        return has(ResolvedEvent::event, record)
    }
}

private val Blob.content: ByteArray
    get() = read()
