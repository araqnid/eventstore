package org.araqnid.eventstore.filesystem.flatpack

import com.fasterxml.jackson.core.JsonFactory
import com.google.common.io.ByteStreams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.stream.consumeAsFlow
import org.araqnid.eventstore.Blob
import org.araqnid.eventstore.EventReader
import org.araqnid.eventstore.EventRecord
import org.araqnid.eventstore.Position
import org.araqnid.eventstore.ResolvedEvent
import org.araqnid.eventstore.StreamId
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.stream.Stream
import kotlin.streams.toList

class FlatPackFilesystemEventReader(val baseDirectory: Path, private val lockable: Lockable) : EventReader {
    private val jsonFactory = JsonFactory()

    override val emptyStorePosition = LooseFile("")

    override fun readAllForwards(after: Position): Flow<ResolvedEvent> {
        val pos = after as FlatPackFilesystemPosition
        return flow {
            lockable.acquireRead().use {
                val packFiles = filesAfter(pos)
                    .filter { it.isPackFile() }
                    .sortedWith(sortByFilename)
                if (packFiles.isEmpty()) {
                    emitAll(filesAfter(pos)
                        .filter { it.isLooseFile() }
                        .sortedWith(sortByFilename)
                        .map { looseFile(it) }
                        .asFlow())
                } else {
                    val latestPackFile = packFiles[packFiles.size - 1]
                    for (path in filesAfter(pos)
                        .filter { it.isPackFile() || it.isLooseFile() && it.fileName.toString() > latestPackFile.fileName.toString() }
                        .sortedWith(sortByFilename)) {
                        emitAll(pathToEvents(path, pos))
                    }
                }
            }
        }
    }

    override val positionCodec = codec

    private fun filesAfter(pos: FlatPackFilesystemPosition): List<Path> =
            Files.list(baseDirectory).filter { it.fileName.toString() > pos.looseFilename }.use { it.toList() }

    private fun pathToEvents(path: Path, pos: FlatPackFilesystemPosition): Flow<ResolvedEvent> {
        return when {
            path.isPackFile() -> unpack(path, pos).consumeAsFlow()
            path.isLooseFile() -> flowOf(looseFile(path))
            else -> emptyFlow()
        }
    }

    private fun looseFile(path: Path): ResolvedEvent {
        val filename = path.fileName.toString()
        val data = Files.readAllBytes(path)
        return makeEvent(filename, data).toResolvedEvent(LooseFile(filename))
    }

    private fun unpack(path: Path, pos: FlatPackFilesystemPosition): Stream<ResolvedEvent> {
        val packFileName = path.fileName.toString()
        return readPackFileEntries(path) { entry, stream ->
            val data = ByteArray(entry.size.toInt())
            ByteStreams.readFully(stream, data)
            makeEvent(entry.name, data).toResolvedEvent(PackedFile(packFileName, entry.name))
        }.filter { re: ResolvedEvent -> pos == emptyStorePosition || codec.comparePositions(re.position, pos) > 0 }
    }

    private fun makeEvent(filename: String, data: ByteArray): EventRecord {
        val matcher = filenamePattern.matcher(filename)
        if (!matcher.matches()) {
            throw RuntimeException("Unparseable filename: $filename")
        }
        val timestamp = Instant.parse(matcher.group(1))
        val category = matcher.group(2)
        val streamId = matcher.group(3)
        val eventNumber = matcher.group(4).toLong()
        val eventType = matcher.group(5).intern()
        val dataOutput = ByteArrayOutputStream()
        val metadataOutput = ByteArrayOutputStream()
        jsonFactory.createParser(data).use { parser ->
            parser.nextToken()
            jsonFactory.createGenerator(dataOutput).use {
                it.copyCurrentStructure(parser)
            }
            if (parser.nextToken() != null) {
                jsonFactory.createGenerator(metadataOutput).use {
                    it.copyCurrentStructure(parser)
                }
            }
        }
        return EventRecord(StreamId(category, streamId), eventNumber, timestamp, eventType,
                Blob(dataOutput.toByteArray()), Blob(metadataOutput.toByteArray()))
    }
}
