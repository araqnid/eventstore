package org.araqnid.eventstore.filesystem.flatpack

import com.fasterxml.jackson.core.JsonFactory
import com.google.common.io.ByteStreams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant
import org.araqnid.eventstore.*
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
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
                emitAll(
                    if (packFiles.isEmpty()) {
                        filesAfter(pos)
                            .filter { it.isLooseFile() }
                            .sortedWith(sortByFilename)
                            .map { looseFile(it) }
                            .asFlow()
                    } else {
                        val latestPackFile = packFiles[packFiles.size - 1]
                        filesAfter(pos)
                            .filter { it.isPackFile() || it.isLooseFile() && it.fileName.toString() > latestPackFile.fileName.toString() }
                            .sortedWith(sortByFilename)
                            .asFlow()
                            .flatMapConcat { pathToEvents(it, pos) }
                    }
                )
            }
        }
    }

    override val positionCodec = codec

    private fun filesAfter(pos: FlatPackFilesystemPosition): List<Path> =
            Files.list(baseDirectory).filter { it.fileName.toString() > pos.looseFilename }.use { it.toList() }

    private fun pathToEvents(path: Path, pos: FlatPackFilesystemPosition): Flow<ResolvedEvent> {
        return when {
            path.isPackFile() -> unpack(path, pos)
            path.isLooseFile() -> flowOf(looseFile(path))
            else -> emptyFlow()
        }
    }

    private fun looseFile(path: Path): ResolvedEvent {
        val filename = path.fileName.toString()
        val data = Files.readAllBytes(path)
        return makeEvent(filename, data).toResolvedEvent(LooseFile(filename))
    }

    private fun unpack(path: Path, pos: FlatPackFilesystemPosition): Flow<ResolvedEvent> {
        val packFileName = path.fileName.toString()
        return readPackFileEntries(path) { entry, stream ->
            val data = ByteArray(entry.size.toInt())
            ByteStreams.readFully(stream, data)
            makeEvent(entry.name, data).toResolvedEvent(PackedFile(packFileName, entry.name))
        }.flowOn(Dispatchers.IO).filterAfter(pos)
    }

    private fun Flow<ResolvedEvent>.filterAfter(pos: FlatPackFilesystemPosition): Flow<ResolvedEvent> {
        return if (pos == emptyStorePosition)
            this
        else
            filter { (position) -> codec.comparePositions(position, pos) > 0 }
    }

    private fun makeEvent(filename: String, data: ByteArray): EventRecord {
        val matchResult = filenamePattern.matchEntire(filename) ?: error("Unparseable filename: $filename")
        val timestamp = Instant.parse(matchResult.groupValues[1])
        val category = matchResult.groupValues[2]
        val streamId = matchResult.groupValues[3]
        val eventNumber = matchResult.groupValues[4].toLong()
        val eventType = matchResult.groupValues[5].intern()
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
