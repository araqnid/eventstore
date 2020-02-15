package org.araqnid.eventstore.filesystem.flatpack

import com.natpryce.hamkrest.anyElement
import com.natpryce.hamkrest.anything
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasElement
import com.timgroup.clocks.testing.ManualClock
import org.araqnid.eventstore.Blob
import org.araqnid.eventstore.NewEvent
import org.araqnid.eventstore.StreamId
import org.araqnid.eventstore.containsInAnyOrder
import org.araqnid.eventstore.testutil.NIOTemporaryFolder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.SECONDS

class FlatPackFilesystemEventSourceTest {
    @Rule
    @JvmField
    val folder = NIOTemporaryFolder()

    @Rule
    @JvmField
    val thrown: ExpectedException = ExpectedException.none()

    private val clock = ManualClock(Instant.parse("2016-05-20T05:16:58.061Z"), UTC)

    private val eventSource by lazy { FlatPackFilesystemEventSource(clock, folder.root) }

    @Test fun `produces store reader`() {
        folder.givenLooseFile("2016-05-20T05:16:58.061Z.category.stream.0.EventType.json", """{ "key": "value" }""")
        assertThat(eventSource.storeReader.readAllForwards().toListAndClose(), anyElement(anything))
    }

    @Test fun `produces stream writer`() {
        eventSource.streamWriter.write(StreamId("category", "stream"), listOf(NewEvent("EventType", Blob.fromString("{}"))))
        assertThat(folder.files(), hasElement("2016-05-20T05:16:58.061Z.category.stream.0.EventType.json"))
    }

    @Test fun `packs loose event files and writes manifest`() {
        eventSource.streamWriter.write(StreamId("category", "stream"), listOf(NewEvent("EventType", Blob.fromString("{}"))))
        clock.bump(5, SECONDS)
        eventSource.packLooseFiles(packMinimumFiles = 1)
        assertThat(folder.files(), containsInAnyOrder(equalTo("2016-05-20T05:16:58.061Z.cpio.xz"), equalTo("2016-05-20T05:16:58.061Z.manifest"))) // timestamp from latest event, not clock
        assertThat(eventSource.storeReader.readAllForwards().toListAndClose(), anyElement(anything))
        assertThat(folder.textFileContent("2016-05-20T05:16:58.061Z.manifest"), equalTo("category stream 0"))
    }

    @Test fun `skips packing if not enough files`() {
        eventSource.streamWriter.write(StreamId("category", "stream"), listOf(NewEvent("EventType", Blob.fromString("{}"))))
        clock.bump(5, SECONDS)
        eventSource.packLooseFiles(packMinimumFiles = 2)
        assertThat(folder.files(), containsInAnyOrder(equalTo("2016-05-20T05:16:58.061Z.category.stream.0.EventType.json")))
    }
}
