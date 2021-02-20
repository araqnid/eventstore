package org.araqnid.eventstore

import com.google.common.io.ByteProcessor
import com.google.common.io.ByteSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import kotlin.text.Charsets.UTF_8

class GuavaBlob(private val content: ByteArray) : ByteSource() {
    companion object {
        val empty = GuavaBlob(ByteArray(0))
        fun fromString(content: String, charset: Charset = UTF_8) = GuavaBlob(content.toByteArray(charset))

        fun fromSource(byteSource: ByteSource): GuavaBlob {
            return if (byteSource.isEmpty)
                empty
            else
                GuavaBlob(byteSource.read())
        }
    }

    val size: Int
        get() = content.size

    override fun openStream(): InputStream = ByteArrayInputStream(content)

    override fun sizeIfKnown(): com.google.common.base.Optional<Long> = com.google.common.base.Optional.of(content.size.toLong())

    override fun read(): ByteArray = content.copyOf()

    override fun <T> read(processor: ByteProcessor<T>): T {
        processor.processBytes(content.copyOf(), 0, content.size)
        return processor.result
    }

    override fun copyTo(output: OutputStream): Long {
        output.write(content)
        return content.size.toLong()
    }

    override fun slice(offset: Long, length: Long): ByteSource = Slice(offset.toInt(), length.toInt())

    override fun equals(other: Any?): Boolean {
        return if (other is GuavaBlob) {
            content.contentEquals(other.content)
        }
        else {
            false
        }
    }

    override fun hashCode(): Int = content.contentHashCode()

    override fun toString(): String = "Blob(${content.size} bytes)"

    private inner class Slice(private val offset: Int, private val length: Int) : ByteSource() {
        override fun openStream(): InputStream = ByteArrayInputStream(content, offset, length)

        override fun sizeIfKnown(): com.google.common.base.Optional<Long> = com.google.common.base.Optional.of(length.toLong())

        override fun read(): ByteArray = content.copyOfRange(offset, offset + length)

        override fun <T> read(processor: ByteProcessor<T>): T {
            processor.processBytes(content.copyOfRange(offset, offset + length), 0, length)
            return processor.result
        }

        override fun copyTo(output: OutputStream): Long {
            output.write(content, offset, length)
            return length.toLong()
        }

        override fun slice(newOffset: Long, length: Long): ByteSource = this@GuavaBlob.slice(offset + newOffset, length)

        override fun toString(): String {
            return "Blob(${content.size} bytes)[$offset+$length]"
        }
    }
}
