package org.araqnid.eventstore.filesystem.flatpack

import com.google.common.io.MoreFiles
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream
import org.araqnid.eventstore.EventCategoryReader
import org.araqnid.eventstore.EventReader
import org.araqnid.eventstore.EventSource
import org.araqnid.eventstore.EventStreamReader
import org.araqnid.eventstore.EventStreamWriter
import org.araqnid.eventstore.Position
import org.araqnid.eventstore.PositionCodec
import org.araqnid.eventstore.StreamId
import org.slf4j.LoggerFactory
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.attribute.BasicFileAttributes
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.stream.Stream
import kotlin.streams.toList
import kotlin.text.Charsets.UTF_8

class FlatPackFilesystemEventSource(val clock: Clock, val baseDirectory: Path) : EventSource {
    private val lockable = Lockable()

    companion object {
        private val logger = LoggerFactory.getLogger(FlatPackFilesystemEventSource::class.java)
        private val random = SecureRandom()
    }

    override val storeReader: EventReader = FlatPackFilesystemEventReader(baseDirectory, lockable)

    override val categoryReader: EventCategoryReader = object : EventCategoryReader {
            override fun readCategoryForwards(category: String, after: Position) =
                    storeReader.readAllForwards(after)
                            .filter { re -> re.event.streamId.category == category }

            override fun emptyCategoryPosition(category: String) = storeReader.emptyStorePosition
            override val positionCodec: PositionCodec = storeReader.positionCodec
        }

    override val streamReader: EventStreamReader = object : EventStreamReader {
            override fun readStreamForwards(streamId: StreamId, after: Long) =
                    storeReader.readAllForwards()
                            .filter { re -> re.event.streamId == streamId }
                            .filter { re -> re.event.eventNumber > after }
            override val positionCodec: PositionCodec = storeReader.positionCodec
        }

    override val streamWriter: EventStreamWriter = FlatPackFilesystemEventStreamWriter(baseDirectory, clock, lockable)

    fun packLooseFiles(packMinimumFiles: Int) {
        lockable.acquireWrite().use {
            val tempFile = baseDirectory.resolve(".pack.${clock.millis()}.${random.nextLong().toString(16)}.tmp")
            var latestTimestamp: Instant? = null
            val packedLooseFiles = ArrayList<Path>()
            val streamPositions = HashMap<StreamId, Long>()
            CpioArchiveOutputStream(XZOutputStream(Files.newOutputStream(tempFile, CREATE_NEW), LZMA2Options(1))).use { cpio ->
                Files.list(baseDirectory).use { existingFiles: Stream<Path> ->
                    val looseFiles = existingFiles.filter { it.isLooseFile() }
                            .sorted(sortByFilename)
                            .toList()
                    looseFiles.forEach { looseFile ->
                        val entry = CpioArchiveEntry(looseFile.fileName.toString())
                        val attributes = Files.readAttributes(looseFile, BasicFileAttributes::class.java)
                        entry.size = attributes.size()
                        cpio.putArchiveEntry(entry)
                        cpio.write(Files.readAllBytes(looseFile))
                        cpio.closeArchiveEntry()
                        val matcher = filenamePattern.matcher(looseFile.fileName.toString())
                        if (!matcher.matches()) {
                            throw IllegalStateException("Unparseable filename: $looseFile")
                        }
                        latestTimestamp = Instant.parse(matcher.group(1))

                        val category = matcher.group(2)
                        val streamId = matcher.group(3)
                        val eventNumber = matcher.group(4).toLong()
                        streamPositions[StreamId(category, streamId)] = eventNumber

                        packedLooseFiles.add(looseFile)
                    }
                }
            }
            if (latestTimestamp != null && packedLooseFiles.size >= packMinimumFiles) {
                MoreFiles.asCharSink(baseDirectory.resolve("$latestTimestamp.manifest"), UTF_8, CREATE_NEW)
                        .writeLines(streamPositions.entries.map { (streamId, eventNumber) -> "${streamId.category} ${streamId.id} $eventNumber"})
                val packFile = baseDirectory.resolve("$latestTimestamp.cpio.xz")
                Files.move(tempFile, packFile)
                packedLooseFiles.forEach(Files::delete)

                logger.info("Packed ${packedLooseFiles.size} loose files into $packFile")
            }
            else {
                Files.delete(tempFile)
            }
        }
    }
}
