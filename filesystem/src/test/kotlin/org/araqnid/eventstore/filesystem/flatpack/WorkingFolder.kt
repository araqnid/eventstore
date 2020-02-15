package org.araqnid.eventstore.filesystem.flatpack

import com.google.common.io.MoreFiles
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.hasElement
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream
import org.araqnid.eventstore.testutil.NIOTemporaryFolder
import java.nio.file.Files
import kotlin.text.Charsets.UTF_8

internal fun NIOTemporaryFolder.givenLooseFile(filename: String, content: String) {
    MoreFiles.asCharSink(newFile(filename), UTF_8).write(content)
}

internal fun NIOTemporaryFolder.givenPackFile(filename: String, builder: CpioArchiveOutputStream.() -> Unit) {
    CpioArchiveOutputStream(withXZ(MoreFiles.asByteSink(newFile(filename))).openStream()).use {
        it.builder()
    }
}

internal fun CpioArchiveOutputStream.addEntry(name: String, content: String) {
    val bytes = content.toByteArray()
    CpioArchiveEntry(name).let { entry ->
        entry.size = bytes.size.toLong()
        putArchiveEntry(entry)
    }
    write(bytes)
    closeArchiveEntry()
}


internal fun NIOTemporaryFolder.textFileContent(filename: String): String {
    assertThat(files(), hasElement(filename))
    return Files.readAllLines(root.resolve(filename), UTF_8).joinToString("\n")
}

internal fun NIOTemporaryFolder.files() = Files.list(root).map { it.fileName.toString() }.toListAndClose().toSet()
