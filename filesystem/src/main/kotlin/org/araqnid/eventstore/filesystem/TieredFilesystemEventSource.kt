package org.araqnid.eventstore.filesystem

import com.google.common.io.ByteSource
import com.google.common.io.MoreFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.stream.consumeAsFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.araqnid.eventstore.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import kotlin.streams.toList

class TieredFilesystemEventSource(val baseDirectory: Path, val clock: Clock) : EventSource {
    override val storeReader: EventReader = StoreReader()
    override val streamReader: EventStreamReader = StreamReader()
    override val streamWriter: EventStreamWriter = StreamWriter()

    internal inner class StreamReader : EventStreamReader {
        override fun readStreamForwards(streamId: StreamId, after: Long): Flow<ResolvedEvent> {
            val streamDirectory = streamDirectory(streamId)
            if (!Files.isDirectory(streamDirectory)) throw NoSuchStreamException(streamId)
            return Files.list(streamDirectory)
                .sorted(filenameComparator)
                .consumeAsFlow()
                .mapNotNull { readEvent(streamId, it) }
                .filter { it.event.eventNumber > after }
        }

        override val positionCodec = TieredFilesystemEventSource.positionCodec
    }

    internal inner class StoreReader : EventReader {
        override fun readAllForwards(after: Position): Flow<ResolvedEvent> {
            val afterFilename = (after as FilesystemPosition).filename.toString()
            val categories = baseDirectory.listFiles()
            val streamDirectories = categories.flatMap { category -> category.listFiles() }
            return streamDirectories.stream()
                .flatMap { path -> Files.list(path) }
                .filter { p -> p.fileName.toString() > afterFilename }
                .sorted(filenameComparator)
                .consumeAsFlow()
                .mapNotNull { readEvent(it) }
        }

        override val emptyStorePosition: Position = TieredFilesystemEventSource.emptyStorePosition

        override val positionCodec = TieredFilesystemEventSource.positionCodec
    }

    fun readCategory(category: String): EventReader = CategoryReader(category)

    private inner class CategoryReader(private val category: String) : EventReader {
        override fun readAllForwards(after: Position): Flow<ResolvedEvent> {
            val afterFilename = (after as FilesystemPosition).filename.toString()
            val categoryDirectory = baseDirectory.resolve(encodeForFilename(category))
            if (!Files.isDirectory(categoryDirectory)) return emptyFlow()
            return Files.list(categoryDirectory)
                .flatMap { path -> Files.list(path) }
                .filter { p -> p.fileName.toString() > afterFilename }
                .sorted(filenameComparator)
                .consumeAsFlow()
                .mapNotNull { readEvent(it) }
        }

        override val emptyStorePosition: Position = TieredFilesystemEventSource.emptyStorePosition

        override val positionCodec = TieredFilesystemEventSource.positionCodec
    }

    internal inner class StreamWriter : AbstractStreamWriter() {
        override fun saveEvents(firstEventNumber: Long, streamId: StreamId, events: List<NewEvent>) {
            val dir = streamDirectory(streamId)
            Files.createDirectories(dir)
            var nextEventNumber = firstEventNumber
            val timestampString = dateFormatter.format(clock.now())
            events.forEach { (eventType, data, metadata) ->
                val hexEventNumber = String.format("%08x", nextEventNumber)
                val dataPath = dir.resolve("$timestampString.$hexEventNumber.$eventType.data.json")
                val dataSink = MoreFiles.asByteSink(dataPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                data.copyTo(dataSink)
                if (!metadata.isEmpty) {
                    val metadataPath = dir.resolve("$timestampString.$hexEventNumber.$eventType.meta.json")
                    val metadataSink =
                        MoreFiles.asByteSink(metadataPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                    metadata.copyTo(metadataSink)
                }
                ++nextEventNumber
            }
        }

        override fun lastEventNumber(streamId: StreamId): Long {
            val dir = streamDirectory(streamId)
            if (!Files.isDirectory(dir)) return emptyStreamEventNumber
            return Files.list(dir).use { stream ->
                stream.filter { p -> filenamePattern.matches(p.fileName.toString()) }
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
        val matchResult = filenamePattern.matchEntire(dataPath.fileName.toString()) ?: return null
        val timestamp: Instant = Instant.parse(matchResult.groupValues[1])
        val eventNumber: Long = matchResult.groupValues[2].toLong(16)
        val eventType: String = matchResult.groupValues[3]
        val dataSource: ByteSource = MoreFiles.asByteSource(dataPath)
        val metadataPath = dataPath.resolveSibling(toMetadataFilename(dataPath.fileName.toString()))
        val metadataSource: ByteSource =
                if (Files.exists(metadataPath))
                    MoreFiles.asByteSource(metadataPath)
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

    private fun Path.listFiles() = Files.list(this).use { it.toList() }

    companion object {
        val positionCodec = positionCodecOfComparable(
                { (filename) -> filename.toString() },
                { str -> FilesystemPosition(Paths.get(str)) })
        private val emptyStorePosition = FilesystemPosition(Paths.get(""))

        private val filenamePattern = Regex("""(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z)\.([0-9a-f]+)\.(.+)\.data\.json""")
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
        private val filenameComparator = compareBy<Path> { it.fileName.toString() }
    }
}
