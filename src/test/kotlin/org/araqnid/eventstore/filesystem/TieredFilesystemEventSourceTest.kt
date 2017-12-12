package org.araqnid.eventstore.filesystem

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.hasElement
import org.araqnid.eventstore.Blob
import org.araqnid.eventstore.EventSource
import org.araqnid.eventstore.EventSourceApiComplianceTest
import org.araqnid.eventstore.NewEvent
import org.araqnid.eventstore.StreamId
import org.araqnid.eventstore.toListAndClose
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TieredFilesystemEventSourceTest : EventSourceApiComplianceTest() {
    @Rule @JvmField val temporaryFolder = TemporaryFolder()

    var clock: Clock = Clock.systemDefaultZone()

    override val eventSource: EventSource by lazy { TieredFilesystemEventSource(temporaryFolder.root.toPath(), clock) }

    @Test fun filenames_encoded_for_lexical_ordering() {
        // timestamps have fixed precision
        // event numbers have fixed width, and are encoded as hex with lower case
        clock = Clock.fixed(Instant.parse("2017-03-30T22:54:00Z"), ZoneId.systemDefault())
        eventSource.streamWriter.write(StreamId("test", "test"),
                (1..20).map { NewEvent("test", Blob.empty, Blob.empty) })
        val streamDirectory = temporaryFolder.root.toPath().resolve("test/test")!!
        assertThat(Files.list(streamDirectory).map { p -> p.fileName.toString() }.toListAndClose(),
                hasElement("2017-03-30T22:54:00.000000000Z.0000000a.test.data.json"))
    }
}
