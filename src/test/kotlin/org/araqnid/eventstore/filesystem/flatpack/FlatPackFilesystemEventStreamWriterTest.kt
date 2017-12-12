package org.araqnid.eventstore.filesystem.flatpack

import com.natpryce.hamkrest.anyElement
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasElement
import org.araqnid.eventstore.Blob
import org.araqnid.eventstore.NewEvent
import org.araqnid.eventstore.StreamId
import org.araqnid.eventstore.WrongExpectedVersionException
import org.araqnid.eventstore.testutil.NIOTemporaryFolder
import org.araqnid.hamkrest.json.equivalentTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset.UTC

class FlatPackFilesystemEventStreamWriterTest {
    @Rule @JvmField
    val folder = NIOTemporaryFolder()

    @Rule @JvmField
    val thrown: ExpectedException = ExpectedException.none()

    @Test fun `writes event as loose files`() {
        eventStreamWriterAt(Instant.parse("2017-03-13T19:23:45.123Z"))
                .write(StreamId("category", "stream"), listOf(NewEvent("EventType", Blob.fromString("""{"key":"value"}"""))))
        assertThat(folder.textFileContent("2017-03-13T19:23:45.123Z.category.stream.0.EventType.json"), equivalentTo("{key:'value'}"))
        assertThat(folder.files(), !anyElement(containsSubstring("LOCK")))
    }

    @Test fun `writes using next event number after loose file`() {
        folder.givenLooseFile("2017-03-13T19:00:00.000Z.category.stream.0.EventType.json", """{"when":"early"}""")
        eventStreamWriterAt(Instant.parse("2017-03-13T19:23:45.123Z"))
                .write(StreamId("category", "stream"), listOf(NewEvent("EventType", Blob.fromString("""{"when":"late"}"""))))
        assertThat(folder.textFileContent("2017-03-13T19:23:45.123Z.category.stream.1.EventType.json"), equivalentTo("{when:'late'}"))
        assertThat(folder.files(), !anyElement(containsSubstring("LOCK")))
    }

    @Test fun `writes event with satisfied expectation`() {
        folder.givenLooseFile("2017-03-13T19:00:00.000Z.category.stream.0.EventType.json", """{"when":"early"}""")
        eventStreamWriterAt(Instant.parse("2017-03-13T19:23:45.123Z"))
                .write(StreamId("category", "stream"), 0L, listOf(NewEvent("EventType", Blob.fromString("""{"when":"late"}"""))))
        assertThat(folder.textFileContent("2017-03-13T19:23:45.123Z.category.stream.1.EventType.json"), equivalentTo("{when:'late'}"))
        assertThat(folder.files(), !anyElement(containsSubstring("LOCK")))
    }

    @Test fun `refuses to write event with unsatisfied expectation`() {
        folder.givenLooseFile("2017-03-13T19:00:00.000Z.category.stream.0.EventType.json", """{"when":"early"}""")
        thrown.expect(WrongExpectedVersionException::class.java)
        eventStreamWriterAt(Instant.parse("2017-03-13T19:23:45.123Z"))
                .write(StreamId("category", "stream"), 2L, listOf(NewEvent("EventType", Blob.fromString("""{"when":"late"}"""))))
        assertThat(folder.files(), equalTo(setOf("2017-03-13T19:00:00.000Z.category.stream.0.EventType.json")))
    }

    @Test fun `writes using distinct event numbers in a single call`() {
        folder.givenLooseFile("2017-03-13T19:00:00.000Z.category.stream.0.EventType.json", """{"when":"early"}""")
        eventStreamWriterAt(Instant.parse("2017-03-13T19:23:45.123Z"))
                .write(StreamId("category", "stream"), listOf(
                        NewEvent("EventType", Blob.fromString("""{"when":"medium"}""")),
                        NewEvent("EventType", Blob.fromString("""{"when":"late"}"""))
                        ))
        assertThat(folder.textFileContent("2017-03-13T19:23:45.123Z.category.stream.1.EventType.json"), equivalentTo("{when:'medium'}"))
        assertThat(folder.textFileContent("2017-03-13T19:23:45.123Z.category.stream.2.EventType.json"), equivalentTo("{when:'late'}"))
        assertThat(folder.files(), !anyElement(containsSubstring("LOCK")))
    }

    @Test fun `writes using distinct event numbers across two calls`() {
        folder.givenLooseFile("2017-03-13T19:00:00.000Z.category.stream.0.EventType.json", """{"when":"early"}""")
        eventStreamWriterAt(Instant.parse("2017-03-13T19:23:45.123Z")).apply {
            write(StreamId("category", "stream"), listOf(NewEvent("EventType", Blob.fromString("""{"when":"medium"}"""))))
            write(StreamId("category", "stream"), listOf(NewEvent("EventType", Blob.fromString("""{"when":"late"}"""))))
        }
        assertThat(folder.textFileContent("2017-03-13T19:23:45.123Z.category.stream.1.EventType.json"), equivalentTo("{when:'medium'}"))
        assertThat(folder.textFileContent("2017-03-13T19:23:45.123Z.category.stream.2.EventType.json"), equivalentTo("{when:'late'}"))
        assertThat(folder.files(), !anyElement(containsSubstring("LOCK")))
    }

    @Test fun `writes using next event number after packed file and creates manifest`() {
        folder.givenPackFile("2017-03-13T19:00:00.000Z.cpio.xz") {
            addEntry("2017-03-13T19:00:00.000Z.category.stream.0.EventType.json", """{ "type": "packed" }""")
        }
        eventStreamWriterAt(Instant.parse("2017-03-13T19:23:45.123Z"))
                .write(StreamId("category", "stream"), listOf(NewEvent("EventType", Blob.fromString("""{"when":"late"}"""))))
        assertThat(folder.textFileContent("2017-03-13T19:23:45.123Z.category.stream.1.EventType.json"), equivalentTo("{when:'late'}"))
        assertThat(folder.textFileContent("2017-03-13T19:00:00.000Z.manifest"), equalTo("category stream 0"))
        assertThat(folder.files(), !anyElement(containsSubstring("LOCK")))
    }

    @Test fun `only scans most recent pack file containing event in relevant stream`() {
        folder.givenPackFile("2017-03-13T19:00:00.000Z.cpio.xz") {
            addEntry("2017-03-13T19:00:00.000Z.category.streamA.0.EventType.json", """{ "type": "packed" }""")
        }
        folder.givenPackFile("2017-03-13T20:00:00.000Z.cpio.xz") {
            addEntry("2017-03-13T20:00:00.000Z.category.streamA.1.EventType.json", """{ "type": "packed" }""")
        }
        eventStreamWriterAt(Instant.parse("2017-08-01T00:00:00Z"))
                .write(StreamId("category", "streamA"), listOf(NewEvent("EventType", Blob.fromString("""{"when":"late"}"""))))
        assertThat(folder.textFileContent("2017-08-01T00:00:00Z.category.streamA.2.EventType.json"), equivalentTo("{when:'late'}"))
        assertThat(folder.textFileContent("2017-03-13T20:00:00.000Z.manifest"), equalTo("category streamA 1"))
        assertThat(folder.files(), !hasElement("2017-03-13T19:00:00.000Z.manifest"))
    }

    private fun eventStreamWriterAt(now: Instant) = FlatPackFilesystemEventStreamWriter(folder.root, Clock.fixed(now, UTC), Lockable())
}
