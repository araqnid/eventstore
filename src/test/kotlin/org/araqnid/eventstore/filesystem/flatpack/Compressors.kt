package org.araqnid.eventstore.filesystem.flatpack

import com.google.common.io.ByteSink
import com.google.common.io.ByteSource
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.UnsupportedOptionsException
import org.tukaani.xz.XZInputStream
import org.tukaani.xz.XZOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

fun uncompress(source: ByteSource, filename: String): ByteSource {
    if (filename.endsWith(".gz")) {
        return withGunzip(source)
    } else if (filename.endsWith(".xz")) {
        return withUnXZ(source)
    } else {
        throw IllegalArgumentException(filename)
    }
}

fun compress(sink: ByteSink, filename: String): ByteSink {
    if (filename.endsWith(".gz")) {
        return withGzip(sink)
    } else if (filename.endsWith(".xz")) {
        return withXZ(sink)
    } else {
        throw IllegalArgumentException(filename)
    }
}

fun withGzip(underlying: ByteSink): ByteSink {
    return GzipByteSink(underlying)
}

fun withXZ(underlying: ByteSink): ByteSink {
    return withXZ(underlying, LZMA2Options())
}

@Throws(UnsupportedOptionsException::class)
fun withXZ(underlying: ByteSink, preset: Int): ByteSink {
    return withXZ(underlying, LZMA2Options(preset))
}

fun withXZ(underlying: ByteSink, options: LZMA2Options): ByteSink {
    return XZByteSink(underlying, options)
}

fun withGunzip(underlying: ByteSource): ByteSource {
    return GunzipByteSource(underlying)
}

fun withUnXZ(underlying: ByteSource): ByteSource {
    return UnXZByteSource(underlying)
}

private class GzipByteSink(private val underlying: ByteSink) : ByteSink() {
    @Throws(IOException::class)
    override fun openStream(): OutputStream {
        return GZIPOutputStream(underlying.openStream())
    }
}

private class GunzipByteSource(private val underlying: ByteSource) : ByteSource() {
    @Throws(IOException::class)
    override fun openStream(): InputStream {
        return GZIPInputStream(underlying.openStream())
    }
}

private class XZByteSink(private val underlying: ByteSink, private val options: LZMA2Options) : ByteSink() {
    @Throws(IOException::class)
    override fun openStream(): OutputStream {
        return XZOutputStream(underlying.openBufferedStream(), options)
    }
}

private class UnXZByteSource(private val underlying: ByteSource) : ByteSource() {
    @Throws(IOException::class)
    override fun openStream(): InputStream {
        return XZInputStream(underlying.openBufferedStream())
    }
}
