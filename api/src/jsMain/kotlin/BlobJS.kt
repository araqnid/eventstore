package org.araqnid.eventstore

import Buffer

actual typealias Blob = Buffer
actual fun String.toBlob() = Buffer.from(this, encoding = "utf-8")
actual fun Blob.readUTF8() = toString(encoding = "utf-8")
