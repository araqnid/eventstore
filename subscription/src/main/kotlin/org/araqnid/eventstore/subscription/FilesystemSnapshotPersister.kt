package org.araqnid.eventstore.subscription

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.stream.consumeAsFlow
import org.araqnid.eventstore.Position
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.regex.Pattern

abstract class FilesystemSnapshotPersister(val baseDirectory: Path, private val fileExtension: String, val clock: Clock) : SnapshotPersister {
    private val logger = LoggerFactory.getLogger(FilesystemSnapshotPersister::class.java)
    private val filePattern = Regex("""(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z)\.snapshot""" + Pattern.quote(fileExtension))
    private val random = SecureRandom()

    override fun load(): Position? {
        val latestFile = snapshotFiles().maxByOrNull { it.timestamp }
        if (latestFile == null) {
            logger.info("No snapshot files")
            return null
        }

        val position = loadSnapshotFile(latestFile.path)
        if (position != null) {
            logger.info("Loaded {} to {}", latestFile.path, position)
        }
        else {
            logger.info("Skipped loading {}", latestFile.path)
        }

        return position
    }

    @Throws(IOException::class)
    abstract fun loadSnapshotFile(path: Path): Position?

    override fun save(): Position {
        val timestamp = Instant.now(clock)
        val filename = "$timestamp.snapshot$fileExtension"
        val snapshotPath = baseDirectory.resolve(filename)
        val temporaryPath = snapshotPath.resolveSibling("$filename.tmp.${random.nextLong()}")

        val snapshotPosition =
            try {
                val snapshotPosition =
                    try {
                        saveSnapshotFile(temporaryPath)
                    } catch (e: IOException) {
                        throw RuntimeException("Failed to write snapshot file $temporaryPath", e)
                    }

                try {
                    Files.move(temporaryPath, snapshotPath)
                    logger.info("Wrote {}", snapshotPath)
                } catch (e: IOException) {
                    throw RuntimeException("Failed to move temporary snapshot file to final location: $temporaryPath -> $snapshotPath", e)
                }

                snapshotPosition
            } finally {
                Files.deleteIfExists(temporaryPath)
            }

        try {
            snapshotFiles()
                    .filter { it.path != snapshotPath }
                    .forEach { (path) ->
                        Files.delete(path)
                        logger.info("Removed {}", path)
                    }
        } catch (e: IOException) {
            logger.warn("Ignoring error cleaning up old snapshots: $e")
        }

        return snapshotPosition
    }

    @Throws(IOException::class)
    abstract fun saveSnapshotFile(path: Path): Position

    private fun snapshotFiles(): List<FileInfo> = runBlocking {
        Files.list(baseDirectory).consumeAsFlow()
            .transform { path ->
                val matchResult = filePattern.matchEntire(path.fileName.toString())
                if (matchResult != null) {
                    val timestamp = Instant.parse(matchResult.groupValues[1])
                    emit(FileInfo(path, timestamp))
                }
            }
            .toList()
    }

    private data class FileInfo(val path: Path, val timestamp: Instant)
}
