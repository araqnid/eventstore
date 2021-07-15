package org.araqnid.eventstore.filesystem

import com.google.common.collect.ImmutableSet

private val permittedFilenameCharacters: Set<Char> = ImmutableSet.builder<Char>()
        .addAll('A'..'Z')
        .addAll('a'..'z')
        .addAll('0'..'9')
        .add('-')
        .add('_')
        .add(':')
        .add('.')
        .add(',')
        .add('@')
        .build()

internal fun encodeForFilename(str: String): String {
    if (str.all { permittedFilenameCharacters.contains(it) }) return str

    return buildString(str.length) {
        for (c in str) {
            when {
                permittedFilenameCharacters.contains(c) -> append(c)
                c == '%' -> append("%%")
                else -> append("%u").append(String.format("%04x", c.code))
            }
        }
    }
}

internal fun decodeFilename(str: String): String {
    if (str.indexOf('%') < 0) return str

    return buildString(str.length) {
        var i = 0
        while (i < str.length - 1) {
            val c = str[i]
            if (c == '%') {
                val cc = str[i + 1]
                i += when (cc) {
                    '%' -> {
                        append('%')
                        2
                    }
                    'u' -> {
                        append(str.substring(i + 2, i + 6).toInt(16).toChar())
                        6
                    }
                    else -> error("Illegal escape specifier %$cc in $str")
                }
            }
            else {
                append(c)
                i += 1
            }
        }
    }
}
