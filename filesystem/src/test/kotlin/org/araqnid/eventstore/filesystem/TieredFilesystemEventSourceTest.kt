package org.araqnid.eventstore.filesystem

import org.araqnid.eventstore.Blob
import org.araqnid.eventstore.EventSource
import org.araqnid.eventstore.NewEvent
import org.araqnid.eventstore.StreamId
import org.araqnid.eventstore.testing.EventSourceApiComplianceTest
import org.araqnid.kotlin.assertthat.assertThat
import org.araqnid.kotlin.assertthat.containsTheItem
import org.araqnid.kotlin.assertthat.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import kotlin.streams.toList
import java.time.Clock as JavaClock

class TieredFilesystemEventSourceTest : EventSourceApiComplianceTest() {
    @get:Rule val temporaryFolder = TemporaryFolder()

    var clock: JavaClock = JavaClock.systemDefaultZone()

    override val eventSource: EventSource by lazy { TieredFilesystemEventSource(temporaryFolder.root.toPath(), clock.asKotlin()) }

    @Test fun filenames_encoded_for_lexical_ordering() {
        // timestamps have fixed precision
        // event numbers have fixed width, and are encoded as hex with lower case
        clock = JavaClock.fixed(Instant.parse("2017-03-30T22:54:00Z"), ZoneId.systemDefault())
        eventSource.streamWriter.write(StreamId("test", "test"),
                (1..20).map { NewEvent("test", Blob.empty ) })
        val streamDirectory = temporaryFolder.root.toPath().resolve("test/test")
        assertThat(Files.list(streamDirectory).map { p -> p.fileName.toString() }.use { it.toList() },
                containsTheItem(equalTo("2017-03-30T22:54:00.000000000Z.0000000a.test.data.json"))
        )
    }
}
