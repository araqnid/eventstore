package org.araqnid.eventstore

import kotlin.text.Charsets.UTF_8

actual typealias Blob = GuavaBlob

actual fun String.toBlob() = GuavaBlob.fromString(this)
actual fun Blob.readUTF8(): String = asCharSource(UTF_8).toString()
