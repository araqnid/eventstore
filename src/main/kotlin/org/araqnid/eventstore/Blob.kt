package org.araqnid.eventstore

import com.google.common.io.ByteSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Arrays

class Blob(private val content: ByteArray) : ByteSource() {
    companion object {
        val empty = Blob(ByteArray(0))
        fun fromString(content: String, charset: Charset = UTF_8) = Blob(content.toByteArray(charset))

        fun fromSource(byteSource: ByteSource): Blob {
            if (byteSource.sizeIfKnown().isPresent && byteSource.sizeIfKnown().get() == 0L)
                return Blob.empty
            else
                return Blob(byteSource.read())
        }
    }

    val size = content.size

    override fun openStream(): InputStream = ByteArrayInputStream(content)

    override fun sizeIfKnown(): com.google.common.base.Optional<Long> = com.google.common.base.Optional.of(size.toLong())

    override fun equals(other: Any?): Boolean {
        if (other is Blob) {
            return Arrays.equals(content, other.content)
        }
        else {
            return false
        }
    }

    override fun hashCode(): Int = Arrays.hashCode(content)

    override fun toString(): String = "Blob($size bytes)"
}
