package org.araqnid.eventstore.filesystem

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

internal suspend fun <T : Comparable<T>> Flow<T>.maxOrNull(): T? {
    var maxSeen: T? = null
    collect { value ->
        maxSeen = maxSeen?.coerceAtLeast(value) ?: value
    }
    return maxSeen
}
