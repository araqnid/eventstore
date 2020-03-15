package org.araqnid.eventstore.filesystem

import com.google.common.io.MoreFiles
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.araqnid.eventstore.Blob
import org.araqnid.eventstore.EventRecord
import org.araqnid.eventstore.EventSource
import org.araqnid.eventstore.StreamId
import org.araqnid.eventstore.testing.EventSourceApiComplianceTest
import org.araqnid.eventstore.testing.readEvents
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import kotlin.text.Charsets.UTF_8

class FlatFilesystemEventSourceTest : EventSourceApiComplianceTest() {
    @get:Rule val temporaryFolder = TemporaryFolder()
    val baseDirectory: Path
        get() = temporaryFolder.root.toPath()

    val clock: Clock = Clock.systemDefaultZone()

    override val eventSource: EventSource by lazy { FlatFilesystemEventSource(baseDirectory, clock) }

    @Test fun reads_events_from_directory() {
        MoreFiles.asByteSink(baseDirectory.resolve("2017-03-30T22:54:00.000000000Z.category.id.0000000a.test.data.json"))
                .asCharSink(UTF_8).write("{ }")
        MoreFiles.asByteSink(baseDirectory.resolve("2017-03-30T22:54:00.000000000Z.category.id.0000000a.test.meta.json"))
                .asCharSink(UTF_8).write("{ /*meta*/ }")
        val eventRecord = EventRecord(StreamId("category", "id"), 10L, Instant.parse("2017-03-30T22:54:00Z"),
                "test", Blob.fromString("{ }"), Blob.fromString("{ /*meta*/ }"))

        assertThat(eventSource.storeReader.readAllForwards().readEvents(), equalTo(listOf(eventRecord)))
        assertThat(eventSource.categoryReader.readCategoryForwards("category").readEvents(), equalTo(listOf(eventRecord)))
        assertThat(eventSource.streamReader.readStreamForwards(eventRecord.streamId).readEvents(), equalTo(listOf(eventRecord)))
    }
}
