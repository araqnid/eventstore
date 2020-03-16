package org.araqnid.eventstore.filesystem.flatpack

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

internal fun <T> readPackFileEntries(path: Path, readEntry: (CpioArchiveEntry, InputStream) -> T): Flow<T> {
    return flow {
        CpioArchiveInputStream(XZInputStream(Files.newInputStream(path))).use { cpio ->
            cpio.forEachEntry { entry ->
                emit(readEntry(entry, cpio))
            }
        }
    }
}

private inline fun CpioArchiveInputStream.forEachEntry(action: (CpioArchiveEntry) -> Unit) {
    while (true) {
        val entry: CpioArchiveEntry = nextCPIOEntry ?: return
        action(entry)
    }
}
