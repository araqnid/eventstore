package org.araqnid.eventstore.filesystem.flatpack

import org.araqnid.eventstore.Position
import org.araqnid.eventstore.positionCodecOfComparable

sealed class FlatPackFilesystemPosition : Comparable<FlatPackFilesystemPosition>, Position {
    abstract val looseFilename: String

    override fun compareTo(other: FlatPackFilesystemPosition) = looseFilename.compareTo(other.looseFilename)
}

data class PackedFile(val packFileName: String, val entryFileName: String) : FlatPackFilesystemPosition() {
    override val looseFilename: String
        get() = entryFileName

    override fun toString() = "$packFileName#$entryFileName"
}

data class LooseFile(val filename: String) : FlatPackFilesystemPosition() {
    override val looseFilename: String
        get() = filename

    override fun toString() = filename
}

val codec = positionCodecOfComparable(
        { position ->
            when (position) {
                is PackedFile -> "${position.packFileName}#${position.looseFilename}"
                is LooseFile -> position.looseFilename
            }
        },
        { stored ->
            val parts = stored.split("#", limit = 2)
            if (parts.size == 2) {
                PackedFile(parts[0], parts[1])
            }
            else {
                LooseFile(stored)
            }
        }

)
