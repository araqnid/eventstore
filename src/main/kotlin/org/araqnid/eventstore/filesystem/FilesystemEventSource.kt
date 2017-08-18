package org.araqnid.eventstore.filesystem

import com.google.common.io.ByteSource
import com.google.common.io.MoreFiles
import org.araqnid.eventstore.Blob
import org.araqnid.eventstore.EventCategoryReader
import org.araqnid.eventstore.EventReader
import org.araqnid.eventstore.EventRecord
import org.araqnid.eventstore.EventSource
import org.araqnid.eventstore.EventStreamReader
import org.araqnid.eventstore.EventStreamWriter
import org.araqnid.eventstore.NewEvent
import org.araqnid.eventstore.NoSuchStreamException
import org.araqnid.eventstore.Position
import org.araqnid.eventstore.ResolvedEvent
import org.araqnid.eventstore.StreamId
import org.araqnid.eventstore.emptyStreamEventNumber
import org.araqnid.eventstore.filterNotNull
import org.araqnid.eventstore.positionCodecOfComparable
import org.araqnid.eventstore.toListAndClose
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.util.Comparator.comparing
import java.util.regex.Pattern
import java.util.stream.Stream

class FilesystemEventSource(val baseDirectory: Path, val clock: Clock) : EventSource {
    override val storeReader: EventReader = StoreReader()
    override val categoryReader: EventCategoryReader = CategoryReader()
    override val streamReader: EventStreamReader = StreamReader()
    override val streamWriter: EventStreamWriter = StreamWriter()
    override val positionCodec = positionCodecOfComparable(
                { (filename) -> filename.toString() },
                { str -> FilesystemPosition(Paths.get(str)) })

    private val emptyStorePosition = FilesystemPosition(Paths.get(""))

    internal inner class StreamReader : EventStreamReader {
        override fun readStreamForwards(streamId: StreamId, after: Long): Stream<ResolvedEvent> {
            val streamDirectory = streamDirectory(streamId)
            if (!Files.isDirectory(streamDirectory)) throw NoSuchStreamException(streamId)
            return Files.list(streamDirectory)
                    .sorted(filenameComparator)
                    .map { readEvent(streamId, it) }
                    .filterNotNull()
                    .filter { it.event.eventNumber > after }
        }
    }

    internal inner class StoreReader : EventReader {
        override fun readAllForwards(after: Position): Stream<ResolvedEvent> {
            val afterFilename = (after as FilesystemPosition).filename.toString()
            val categories = Files.list(baseDirectory).toListAndClose()
            val streamDirectories = categories.flatMap { category -> Files.list(category).toListAndClose() }
            return streamDirectories.stream()
                    .flatMap { path -> Files.list(path) }
                    .filter { p -> p.fileName.toString() > afterFilename }
                    .sorted(filenameComparator)
                    .map { readEvent(it) }
                    .filterNotNull()
        }

        override val emptyStorePosition: Position
            get() = this@FilesystemEventSource.emptyStorePosition
    }

    internal inner class CategoryReader : EventCategoryReader {
        override fun readCategoryForwards(category: String, after: Position): Stream<ResolvedEvent> {
            val afterFilename = (after as FilesystemPosition).filename.toString()
            val categoryDirectory = baseDirectory.resolve(encodeForFilename(category))
            if (!Files.isDirectory(categoryDirectory)) return Stream.empty()
            return Files.list(categoryDirectory)
                    .flatMap { path -> Files.list(path) }
                    .filter { p -> p.fileName.toString() > afterFilename }
                    .sorted(filenameComparator)
                    .map { readEvent(it) }
                    .filterNotNull()
        }

        override fun emptyCategoryPosition(category: String): Position = this@FilesystemEventSource.emptyStorePosition
    }

    internal inner class StreamWriter : AbstractStreamWriter() {
        override fun saveEvents(firstEventNumber: Long, streamId: StreamId, events: List<NewEvent>) {
            val dir = streamDirectory(streamId)
            Files.createDirectories(dir)
            var nextEventNumber = firstEventNumber
            val timestampString = dateFormatter.format(Instant.now(clock))
            events.forEach { (eventType, data, metadata) ->
                val hexEventNumber = String.format("%08x", nextEventNumber)
                val dataPath = dir.resolve("$timestampString.$hexEventNumber.$eventType.data.json")!!
                val dataSink = MoreFiles.asByteSink(dataPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)!!
                data.copyTo(dataSink)
                if (!metadata.isEmpty) {
                    val metadataPath = dir.resolve("$timestampString.$hexEventNumber.$eventType.meta.json")!!
                    val metadataSink = MoreFiles.asByteSink(metadataPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)!!
                    metadata.copyTo(metadataSink)
                }
                ++nextEventNumber
            }
        }

        override fun lastEventNumber(streamId: StreamId): Long {
            val dir = streamDirectory(streamId)
            if (!Files.isDirectory(dir)) return emptyStreamEventNumber
            return Files.list(dir).use { stream ->
                stream.filter { p -> filenamePattern.matcher(p.fileName.toString()).matches() }
                        .count() - 1
            }
        }
    }

    private fun streamDirectory(streamId: StreamId): Path = baseDirectory.resolve(encodeForFilename(streamId.category)).resolve(encodeForFilename(streamId.id))

    private fun readEvent(dataPath: Path): ResolvedEvent? {
        val id = decodeFilename(dataPath.parent.fileName.toString())
        val category = decodeFilename(dataPath.parent.parent.fileName.toString())
        return readEvent(StreamId(category, id), dataPath)
    }

    private fun readEvent(streamId: StreamId, dataPath: Path): ResolvedEvent? {
        val matcher = filenamePattern.matcher(dataPath.fileName.toString())
        if (!matcher.matches()) return null
        val timestamp: Instant = Instant.parse(matcher.group(1))
        val eventNumber: Long = matcher.group(2).toLong(16)
        val eventType: String = matcher.group(3)
        val dataSource: ByteSource = MoreFiles.asByteSource(dataPath)
        val metadataPath = dataPath.resolveSibling(toMetadataFilename(dataPath.fileName.toString()))!!
        val metadataSource: ByteSource =
                if (Files.exists(metadataPath))
                    MoreFiles.asByteSource(metadataPath)!!
                else
                    ByteSource.empty()

        return EventRecord(streamId, eventNumber, timestamp, eventType, Blob.fromSource(dataSource), Blob.fromSource(metadataSource))
                .toResolvedEvent(FilesystemPosition(dataPath.fileName))
    }

    private fun toMetadataFilename(filename: String): String {
        if (!filename.endsWith(".data.json")) throw IllegalArgumentException("Invalid data filename: $filename")
        return filename.substring(0, filename.length - 10) + ".meta.json"
    }

    private data class FilesystemPosition(val filename: Path) : Comparable<FilesystemPosition>, Position {
        override fun compareTo(other: FilesystemPosition): Int = filename.compareTo(other.filename)
    }

    companion object {
        private val filenamePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z)\\.([0-9a-f]+)\\.(.+)\\.data\\.json")!!
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
        private val filenameComparator: Comparator<Path> = comparing<Path, String> { it.fileName.toString() }
    }
}
