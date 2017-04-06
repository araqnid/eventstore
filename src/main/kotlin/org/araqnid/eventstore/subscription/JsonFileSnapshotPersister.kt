package org.araqnid.eventstore.subscription

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import org.araqnid.eventstore.Position
import org.araqnid.eventstore.PositionCodec
import org.slf4j.LoggerFactory
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZInputStream
import org.tukaani.xz.XZOutputStream
import java.io.Closeable
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock

abstract class JsonFileSnapshotPersister(baseDirectory: Path,
                                val objectMapper: ObjectMapper,
                                val positionCodec: PositionCodec,
                                val compatibilityVersion: Long,
                                clock: Clock)
    : FilesystemSnapshotPersister(baseDirectory, ".json.xz", clock) {

    private val logger = LoggerFactory.getLogger(JsonFileSnapshotPersister::class.java)

    override fun loadSnapshotFile(path: Path): Position? {
        XZInputStream(Files.newInputStream(path)).use { stream ->
            objectMapper.factory.createParser(stream).use { jsonParser ->
                val metadata = objectMapper.readValue(jsonParser, SnapshotMetadata::class.java)
                if (metadata.version != compatibilityVersion) {
                    logger.warn("Snapshot metadata version ({}) does not match ours ({}), skipping", metadata.version,
                            compatibilityVersion)
                    return null
                }
                logger.info("Loading snapshot from {}", path)
                val position = positionCodec.decode(metadata.position)
                loadSnapshotJson(jsonParser, position)
                return position
            }
        }
    }

    @Throws(IOException::class)
    abstract fun loadSnapshotJson(jsonParser: JsonParser, position: Position)

    override fun saveSnapshotFile(path: Path): Position {
        return lockForSave().use { locked ->
            XZOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), LZMA2Options(1)).use { output ->
                objectMapper.factory.createGenerator(output).use { jsonGenerator ->
                    jsonGenerator.writeObject(SnapshotMetadata(positionCodec.encode(locked.position), compatibilityVersion))
                    locked.saveSnapshotJson(jsonGenerator)
                    locked.position
                }
            }
        }
    }

    abstract fun lockForSave(): PositionLock

    data class SnapshotMetadata(val position: String, val version: Long)

    interface PositionLock : Closeable {
        val position: Position

        @Throws(IOException::class)
        fun saveSnapshotJson(jsonGenerator: JsonGenerator)
    }
}
