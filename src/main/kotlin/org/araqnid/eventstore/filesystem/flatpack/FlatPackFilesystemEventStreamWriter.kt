package org.araqnid.eventstore.filesystem.flatpack

import com.google.common.io.LineProcessor
import com.google.common.io.MoreFiles
import org.araqnid.eventstore.EventStreamWriter
import org.araqnid.eventstore.NewEvent
import org.araqnid.eventstore.StreamId
import org.araqnid.eventstore.WrongExpectedVersionException
import java.io.OutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.toList

class FlatPackFilesystemEventStreamWriter(val baseDirectory: Path, val clock: Clock, val lockable: Lockable) : EventStreamWriter {
    private val streamCounters: MutableMap<StreamId, StreamCounter> = ConcurrentHashMap()

    override fun write(streamId: StreamId, events: List<NewEvent>) {
        lockable.acquireWrite().use {
            streamCounter(streamId).lock().use {
                write(streamId, events, it)
            }
        }
    }

    @Throws(WrongExpectedVersionException::class)
    override fun write(streamId: StreamId, expectedEventNumber: Long, events: List<NewEvent>) {
        lockable.acquireWrite().use {
            streamCounter(streamId).lock().use {
                it.checkEventNumber(expectedEventNumber)
                write(streamId, events, it)
            }
        }
    }

    private fun write(streamId: StreamId, events: List<NewEvent>, streamCounter: StreamCounter.Locked) {
        val timestamp = Instant.now(clock)
        events.forEach {
            writeEvent(it, streamId, timestamp, streamCounter.nextEventNumber())
        }
    }

    private fun writeEvent(event: NewEvent, streamId: StreamId, timestamp: Instant, eventNumber: Long) {
        val filename = "$timestamp.${streamId.category}.${streamId.id}.$eventNumber.${event.type}.json"
        Files.newOutputStream(baseDirectory.resolve(filename), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output: OutputStream ->
            event.data.copyTo(output)
            if (event.metadata.size > 0) {
                event.metadata.copyTo(output)
            }
        }
    }

    private fun streamCounter(streamId: StreamId): StreamCounter = streamCounters.computeIfAbsent(streamId, { StreamCounter(it) })

    inner class StreamCounter(val streamId: StreamId) {
        @Volatile
        private var lastSeen: StreamPosition? = null

        fun lock(): Locked {
            val path = baseDirectory.resolve("LOCK.${streamId.category}.${streamId.id}")
            Files.createFile(path)
            read()
            return Locked(path, lastSeen?.eventNumber ?: -1)
        }

        inner class Locked(val path: Path, var lastEventNumber: Long) : AutoCloseable {

            override fun close() {
                Files.delete(path)
            }

            fun nextEventNumber(): Long = ++lastEventNumber

            fun checkEventNumber(expectedEventNumber: Long) {
                if (lastEventNumber != expectedEventNumber) {
                    throw WrongExpectedVersionException(streamId, lastEventNumber, expectedEventNumber)
                }
            }
        }

        private fun read() {
            val files = scanFileNames().use {
                it.sorted(Comparator.comparing<Path, String> { it.fileName.toString() }.reversed()).toList()
            }
            files.forEach {
                val streamPosition = positionIfStreamMatches(it)
                if (streamPosition != null) {
                    lastSeen = streamPosition
                    return
                }
            }
        }

        private fun scanFileNames() = lastSeen?.let { fileNamesAfter(it.filename) } ?: allFileNames()

        private fun allFileNames() = Files.list(baseDirectory)

        private fun fileNamesAfter(lastFilename: String) = Files.list(baseDirectory).filter { it.fileName.toString() > lastFilename }

        private fun positionIfStreamMatches(path: Path): StreamPosition? {
            return when {
                path.isLooseFile() -> {
                    val matcher = filenamePattern.matcher(path.fileName.toString())
                    if (!matcher.matches()) throw RuntimeException("Unparseable filename: $path")
                    val category = matcher.group(2)
                    val id = matcher.group(3)
                    if (streamId.category == category && streamId.id == id) {
                        val eventNumber = matcher.group(4).toLong()
                        StreamPosition(path.fileName.toString(), eventNumber)
                    } else {
                        null
                    }
                }
                path.isPackFile() -> {
                    val manifestPath = path.resolveSibling(path.fileName.toString().removeSuffix(".cpio.xz") + ".manifest")
                    if (!Files.exists(manifestPath)) {
                        val streams = HashMap<StreamId, Long>()
                        readPackFileEntries(path) { entry, _ -> entry.name }.use {
                            it.forEachOrdered { filename ->
                                val matcher = filenamePattern.matcher(filename.toString())
                                if (!matcher.matches()) throw RuntimeException("Unparseable filename in pack $path: $filename")
                                val category = matcher.group(2)
                                val id = matcher.group(3)
                                val eventNumber = matcher.group(4).toLong()
                                streams[StreamId(category, id)] = eventNumber
                            }
                        }
                        MoreFiles.asCharSink(manifestPath, UTF_8, StandardOpenOption.CREATE_NEW)
                                .writeLines(streams.entries.map { (streamId, eventNumber) -> "${streamId.category} ${streamId.id} $eventNumber"})
                    }
                    MoreFiles.asCharSource(manifestPath, UTF_8).readLines(object : LineProcessor<StreamPosition?> {
                        private var eventNumber: Long? = null

                        override fun processLine(line: String): Boolean {
                            val(category, id, eventNumberString) = line.split(" ")
                            return if (category == streamId.category && id == streamId.id) {
                                eventNumber = eventNumberString.toLong()
                                false
                            } else {
                                true
                            }
                        }

                        override fun getResult() = eventNumber?.let { StreamPosition(path.fileName.toString(), it) }
                    })
                }
                else -> null
            }
        }
    }

    private data class StreamPosition(val filename: String, val eventNumber: Long)
}