package org.araqnid.eventstore.subscription

import org.araqnid.eventstore.Position
import org.araqnid.eventstore.filterNotNull
import org.araqnid.eventstore.toListAndClose
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
    private val filePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z)\\.snapshot" + Pattern.quote(fileExtension))
    private val random = SecureRandom()

    override fun load(): Position? {
        val latestFile = snapshotFiles().maxBy { it.timestamp }
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

    private fun snapshotFiles(): List<FileInfo> = Files.list(baseDirectory).map(this::snapshotFileInfo).filterNotNull().toListAndClose()

    private fun snapshotFileInfo(path: Path): FileInfo? {
        val matcher = filePattern.matcher(path.fileName.toString())
        if (!matcher.matches()) return null
        val timestamp = Instant.parse(matcher.group(1))
        return FileInfo(path, timestamp)
    }

    private data class FileInfo(val path: Path, val timestamp: Instant)
}
