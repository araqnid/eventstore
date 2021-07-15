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
    val builder = StringBuilder(str.length)
    for (c in str) {
        when {
            permittedFilenameCharacters.contains(c) -> builder.append(c)
            c == '%' -> builder.append("%%")
            else -> builder.append("%u").append(String.format("%04x", c.code))
        }
    }
    if (builder.length == str.length) return str
    return builder.toString()
}

internal fun decodeFilename(str: String): String {
    if (str.indexOf('%') < 0) return str
    val builder = StringBuilder(str.length)
    var i = 0
    while (i < str.length - 1) {
        val c = str[i]
        if (c == '%') {
            val cc = str[i + 1]
            i += when (cc) {
                '%' -> {
                    builder.append('%')
                    2
                }
                'u' -> {
                    builder.append(str.substring(i + 2, i + 6).toInt(16).toChar())
                    6
                }
                else -> throw RuntimeException("Illegal escape specifier %$cc in $str")
            }
        }
        else {
            builder.append(c)
            i += 1
        }
    }
    return builder.toString()
}
