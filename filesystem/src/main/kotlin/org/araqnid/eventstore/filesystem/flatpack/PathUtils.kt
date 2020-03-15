package org.araqnid.eventstore.filesystem.flatpack

import java.nio.file.Path
import java.util.regex.Pattern

internal val sortByFilename = compareBy(Path::getFileName)

internal fun Path.isPackFile() = fileName.toString().endsWith(".cpio.xz")

internal fun Path.isLooseFile() = fileName.toString().endsWith(".json")

internal val filenamePattern: Pattern = Pattern.compile("(\\d+-\\d+-\\d+T\\d+:\\d+:\\d+(?:[_.]\\d+)?Z)\\.([^.]+)\\.([^.]+)\\.([^.]+)\\.([^.]+)\\.json")
