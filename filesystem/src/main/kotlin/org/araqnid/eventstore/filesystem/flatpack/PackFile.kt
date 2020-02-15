package org.araqnid.eventstore.filesystem.flatpack

import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Spliterator
import java.util.function.Consumer
import java.util.stream.Stream
import java.util.stream.StreamSupport

internal fun <T> readPackFileEntries(path: Path, readEntry: (CpioArchiveEntry, InputStream) -> T): Stream<T> {
    val cpio = CpioArchiveInputStream(XZInputStream(Files.newInputStream(path)))
    val spliterator = object : Spliterator<T> {
        override fun tryAdvance(action: Consumer<in T>): Boolean {
            val entry: CpioArchiveEntry? = cpio.nextCPIOEntry
            if (entry == null) {
                cpio.close()
                return false
            }
            action.accept(readEntry(entry, cpio))
            return true
        }

        override fun trySplit() = null

        override fun estimateSize() = Long.MAX_VALUE

        override fun characteristics() = Spliterator.ORDERED or Spliterator.DISTINCT or Spliterator.IMMUTABLE or Spliterator.NONNULL
    }
    return StreamSupport.stream(spliterator, false).onClose { cpio.close() }
}