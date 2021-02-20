package org.araqnid.eventstore.filesystem.flatpack

import com.google.common.io.MoreFiles
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.stream.consumeAsFlow
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream
import org.araqnid.eventstore.EventReader
import org.araqnid.eventstore.EventSource
import org.araqnid.eventstore.EventStreamReader
import org.araqnid.eventstore.EventStreamWriter
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
import kotlin.text.Charsets.UTF_8

class FlatPackFilesystemEventSource(val clock: Clock, val baseDirectory: Path) : EventSource {
    private val lockable = Lockable()

    companion object {
        private val logger = LoggerFactory.getLogger(FlatPackFilesystemEventSource::class.java)
        private val random = SecureRandom()
    }

    override val storeReader: EventReader = FlatPackFilesystemEventReader(baseDirectory, lockable)

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
                runBlocking {
                    Files.list(baseDirectory).consumeAsFlow().filter { it.isLooseFile() }.toList()
                }.sortedWith(sortByFilename).forEach { looseFile ->
                    val entry = CpioArchiveEntry(looseFile.fileName.toString())
                    val attributes = Files.readAttributes(looseFile, BasicFileAttributes::class.java)
                    entry.size = attributes.size()
                    cpio.putArchiveEntry(entry)
                    cpio.write(Files.readAllBytes(looseFile))
                    cpio.closeArchiveEntry()
                    val matcher = filenamePattern.matchEntire(looseFile.fileName.toString())
                        ?: error("Unparseable filename: $looseFile")
                    latestTimestamp = Instant.parse(matcher.groupValues[1])

                    val category = matcher.groupValues[2]
                    val streamId = matcher.groupValues[3]
                    val eventNumber = matcher.groupValues[4].toLong()
                    streamPositions[StreamId(category, streamId)] = eventNumber

                    packedLooseFiles.add(looseFile)
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
