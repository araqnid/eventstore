package org.araqnid.eventstore

expect class Blob

expect fun String.toBlob(): Blob
val emptyBlob = "".toBlob()
expect fun Blob.readUTF8(): String
