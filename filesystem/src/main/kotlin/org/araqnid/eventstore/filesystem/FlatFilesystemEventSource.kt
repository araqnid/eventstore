package org.araqnid.eventstore.filesystem

import com.google.common.io.MoreFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.stream.consumeAsFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.araqnid.eventstore.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField

class FlatFilesystemEventSource(val baseDirectory: Path, val clock: Clock = Clock.System) : EventSource {
    override val storeReader: EventReader = StoreReader()
    override val streamReader: EventStreamReader = StreamReader()
    override val streamWriter: EventStreamWriter = StreamWriter()

    internal inner class StoreReader : EventReader {
        override fun readAllForwards(after: Position): Flow<ResolvedEvent> {
            val afterFilesystemPosition = after as FilesystemPosition
            return Files.list(baseDirectory)
                .filter {
                    it.fileName.toString() > afterFilesystemPosition.filename
                            && it.fileName.toString().endsWith(".data.json")
                }
                .sorted(compareBy { it.fileName.toString() })
                .consumeAsFlow()
                .mapNotNull { readEvent(it) }
        }

        private fun readEvent(dataPath: Path): ResolvedEvent? {
            val matcher = filenamePattern.matchEntire(dataPath.fileName.toString()) ?: return null
            val timestamp = Instant.parse(matcher.groupValues[1])
            val streamId = StreamId(matcher.groupValues[2], matcher.groupValues[3])
            val eventNumber = matcher.groupValues[4].toLong(16)
            val eventType = matcher.groupValues[5]
            val metadataPath = dataPath.resolveSibling(metadataFilenameFor(dataPath.fileName.toString()))

            val dataBlob = Blob.fromSource(MoreFiles.asByteSource(dataPath))
            val metadataBlob = if (Files.exists(metadataPath))
                Blob.fromSource(MoreFiles.asByteSource(metadataPath))
            else
                Blob.empty

            return EventRecord(streamId, eventNumber, timestamp, eventType, dataBlob, metadataBlob).toResolvedEvent(LooseFile(dataPath.fileName.toString()))
        }

        override val emptyStorePosition: Position = Empty
        override val positionCodec: PositionCodec = FlatFilesystemEventSource.positionCodec
    }

    internal inner class StreamReader : EventStreamReader {
        override fun readStreamForwards(streamId: StreamId, after: Long): Flow<ResolvedEvent> {
            return storeReader.readAllForwards().filter { it.event.streamId == streamId && it.event.eventNumber > after }
        }
        override val positionCodec: PositionCodec = FlatFilesystemEventSource.positionCodec
    }

    internal inner class StreamWriter : AbstractStreamWriter() {
        override fun saveEvents(firstEventNumber: Long, streamId: StreamId, events: List<NewEvent>) {
            var nextEventNumber = firstEventNumber
            val timestamp = clock.now()
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
            return runBlocking {
                Files.list(baseDirectory)
                    .consumeAsFlow()
                    .mapNotNull { path -> eventNumber(path.fileName.toString(), streamId) }
                    .maxOrNull() ?: emptyStreamEventNumber
            }
        }

        private fun eventNumber(filename: String, matchStreamId: StreamId): Long? {
            with(filenamePattern.matchEntire(filename) ?: return null) {
                if (StreamId(groupValues[2], groupValues[3]) != matchStreamId) return null
                return groupValues[4].toLong(16)
            }
        }
    }

    private fun metadataFilenameFor(dataFilename: String): String {
        val suffix = ".data.json"
        val prefix = dataFilename.substring(0, dataFilename.length - suffix.length)
        return "$prefix.meta.json"
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
        val positionCodec = positionCodecOfComparable(
                { p -> p.toString() },
                { str ->
                    val index = str.indexOf('#')
                    if (index < 0)
                        LooseFile(str)
                    else
                        PackedFile(str.substring(0, index - 1), str.substring(index + 1))
                })
        private val filenamePattern = Regex("""(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z)\.([^.]+)\.([^.]+)\.([0-9a-f]+)\.([^.]+)\.data\.json""")
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
