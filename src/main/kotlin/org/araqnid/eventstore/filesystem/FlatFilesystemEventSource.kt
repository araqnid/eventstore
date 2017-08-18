package org.araqnid.eventstore.filesystem

import com.google.common.io.MoreFiles
import org.araqnid.eventstore.Blob
import org.araqnid.eventstore.EventCategoryReader
import org.araqnid.eventstore.EventReader
import org.araqnid.eventstore.EventRecord
import org.araqnid.eventstore.EventSource
import org.araqnid.eventstore.EventStreamReader
import org.araqnid.eventstore.EventStreamWriter
import org.araqnid.eventstore.NewEvent
import org.araqnid.eventstore.Position
import org.araqnid.eventstore.ResolvedEvent
import org.araqnid.eventstore.StreamId
import org.araqnid.eventstore.WrongExpectedVersionException
import org.araqnid.eventstore.collectAndClose
import org.araqnid.eventstore.emptyStreamEventNumber
import org.araqnid.eventstore.filterNotNull
import org.araqnid.eventstore.positionCodecOfComparable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors.maxBy
import java.util.stream.Stream

class FlatFilesystemEventSource(val baseDirectory: Path, val clock: Clock) : EventSource {
    override val storeReader: EventReader = StoreReader()
    override val categoryReader: EventCategoryReader = CategoryReader()
    override val streamReader: EventStreamReader = StreamReader()
    override val streamWriter: EventStreamWriter = StreamWriter()
    override val positionCodec = positionCodecOfComparable(
            { p -> p.toString() },
            { str ->
                val index = str.indexOf('#')
                if (index < 0)
                    LooseFile(str)
                else
                    PackedFile(str.substring(0, index - 1), str.substring(index + 1))
            })

    internal inner class StoreReader : EventReader {
        override fun readAllForwards(after: Position): Stream<ResolvedEvent> {
            val afterFilesystemPosition = after as FilesystemPosition
            return Files.list(baseDirectory)
                    .filter { it.fileName.toString() > afterFilesystemPosition.filename && it.fileName.toString().endsWith(".data.json") }
                    .sorted(Comparator.comparing<Path, String> { it.fileName.toString() })
                    .map { readEvent(it) }
                    .filterNotNull()
        }

        fun readEvent(dataPath: Path): ResolvedEvent? {
            val matcher: Matcher = filenamePattern.matcher(dataPath.fileName.toString())
            if (!matcher.matches()) return null
            val timestamp = Instant.parse(matcher.group(1))
            val streamId = StreamId(matcher.group(2), matcher.group(3))
            val eventNumber = matcher.group(4).toLong(16)
            val eventType = matcher.group(5)
            val metadataPath = dataPath.resolveSibling(metadataFilenameFor(dataPath.fileName.toString()))

            val dataBlob = Blob.fromSource(MoreFiles.asByteSource(dataPath))
            val metadataBlob = if (Files.exists(metadataPath))
                Blob.fromSource(MoreFiles.asByteSource(metadataPath))
            else
                Blob.empty

            return EventRecord(streamId, eventNumber, timestamp, eventType, dataBlob, metadataBlob).toResolvedEvent(LooseFile(dataPath.fileName.toString()))
        }

        override val emptyStorePosition: Position = Empty
    }

    internal inner class CategoryReader : EventCategoryReader {
        override fun readCategoryForwards(category: String, after: Position): Stream<ResolvedEvent> {
            return storeReader.readAllForwards(after).filter { it.event.streamId.category == category }
        }

        override fun emptyCategoryPosition(category: String): Position = Empty
    }

    internal inner class StreamReader : EventStreamReader {
        override fun readStreamForwards(streamId: StreamId, after: Long): Stream<ResolvedEvent> {
            return storeReader.readAllForwards().filter { it.event.streamId == streamId && it.event.eventNumber > after }
        }
    }

    internal inner class StreamWriter : AbstractStreamWriter() {
        override fun saveEvents(firstEventNumber: Long, streamId: StreamId, events: List<NewEvent>) {
            var nextEventNumber = firstEventNumber
            val timestamp = Instant.now(clock)
            events.forEach { event ->
                val dataFilename = "${dateFormatter.format(timestamp)}.${streamId.category}.${streamId.id}.${String.format("%08x", nextEventNumber)}.${event.type}.data.json"
                event.data.copyTo(MoreFiles.asByteSink(baseDirectory.resolve(dataFilename), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
                if (!event.metadata.isEmpty) {
                    val metadataFilename = metadataFilenameFor(dataFilename)
                    event.metadata.copyTo(MoreFiles.asByteSink(baseDirectory.resolve(metadataFilename), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
                }
                nextEventNumber++
            }
        }

        override fun lastEventNumber(streamId: StreamId): Long {
            return Files.list(baseDirectory)
                    .map { path -> eventNumber(path.fileName.toString(), streamId) }
                    .filterNotNull()
                    .collectAndClose(maxBy(naturalOrder()))
                    .orElse(emptyStreamEventNumber)
        }

        fun eventNumber(filename: String, matchStreamId: StreamId): Long? {
            with(filenamePattern.matcher(filename)) {
                if (!matches()) return null
                if (StreamId(group(2), group(3)) != matchStreamId) return null
                return group(4).toLong(16)
            }
        }
    }

    private fun metadataFilenameFor(dataFilename: String): String {
        val suffix = ".data.json"
        val prefix = dataFilename.substring(0, dataFilename.length - suffix.length)
        return prefix + ".meta.json"
    }

    private abstract class FilesystemPosition : Comparable<FilesystemPosition>, Position {
        abstract val filename: String
        override fun compareTo(other: FilesystemPosition): Int = filename.compareTo(other.filename)
    }

    private object Empty : FilesystemPosition() {
        override val filename: String = ""
    }
    private data class LooseFile(override val filename: String) : FilesystemPosition()
    private data class PackedFile(val packfile: String, override val filename: String): FilesystemPosition()

    companion object {
        private val filenamePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z)\\.([^.]+)\\.([^.]+)\\.([0-9a-f]+)\\.([^.]+)\\.data\\.json")!!
        private val dateFormatter = DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .appendLiteral('T')
                .appendValue(ChronoField.HOUR_OF_DAY, 2)
                .appendLiteral(':')
                .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
                .optionalStart()
                .appendLiteral(':')
                .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
                .appendFraction(ChronoField.NANO_OF_SECOND, 9, 9, true)
                .appendOffsetId()
                .toFormatter()
                .withResolverStyle(ResolverStyle.STRICT)
                .withZone(ZoneOffset.UTC)
    }
}
