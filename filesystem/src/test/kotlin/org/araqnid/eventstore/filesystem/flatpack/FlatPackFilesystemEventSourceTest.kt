package org.araqnid.eventstore.filesystem.flatpack

import com.timgroup.clocks.testing.ManualClock
import org.araqnid.eventstore.GuavaBlob
import org.araqnid.eventstore.NewEvent
import org.araqnid.eventstore.StreamId
import org.araqnid.eventstore.filesystem.blockingToList
import org.araqnid.eventstore.testutil.NIOTemporaryFolder
import org.araqnid.kotlin.assertthat.assertThat
import org.araqnid.kotlin.assertthat.containsInAnyOrder
import org.araqnid.kotlin.assertthat.containsTheItem
import org.araqnid.kotlin.assertthat.emptyCollection
import org.araqnid.kotlin.assertthat.equalTo
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.SECONDS

class FlatPackFilesystemEventSourceTest {
    @get:Rule
    val folder = NIOTemporaryFolder()

    private val clock = ManualClock(Instant.parse("2016-05-20T05:16:58.061Z"), UTC)

    private val eventSource by lazy { FlatPackFilesystemEventSource(clock, folder.root) }

    @Test fun `produces store reader`() {
        folder.givenLooseFile("2016-05-20T05:16:58.061Z.category.stream.0.EventType.json", """{ "key": "value" }""")
        assertThat(eventSource.storeReader.readAllForwards().blockingToList(), !emptyCollection)
    }

    @Test fun `produces stream writer`() {
        eventSource.streamWriter.write(StreamId("category", "stream"), listOf(NewEvent("EventType", GuavaBlob.fromString("{}"))))
        assertThat(folder.files(), containsTheItem(equalTo("2016-05-20T05:16:58.061Z.category.stream.0.EventType.json")))
    }

    @Test fun `packs loose event files and writes manifest`() {
        eventSource.streamWriter.write(StreamId("category", "stream"), listOf(NewEvent("EventType", GuavaBlob.fromString("{}"))))
        clock.bump(5, SECONDS)
        eventSource.packLooseFiles(packMinimumFiles = 1)
        assertThat(folder.files(), containsInAnyOrder(equalTo("2016-05-20T05:16:58.061Z.cpio.xz"), equalTo("2016-05-20T05:16:58.061Z.manifest"))) // timestamp from latest event, not clock
        assertThat(eventSource.storeReader.readAllForwards().blockingToList(), !emptyCollection)
        assertThat(folder.textFileContent("2016-05-20T05:16:58.061Z.manifest"), equalTo("category stream 0"))
    }

    @Test fun `skips packing if not enough files`() {
        eventSource.streamWriter.write(StreamId("category", "stream"), listOf(NewEvent("EventType", GuavaBlob.fromString("{}"))))
        clock.bump(5, SECONDS)
        eventSource.packLooseFiles(packMinimumFiles = 2)
        assertThat(folder.files(), containsInAnyOrder(equalTo("2016-05-20T05:16:58.061Z.category.stream.0.EventType.json")))
    }
}
