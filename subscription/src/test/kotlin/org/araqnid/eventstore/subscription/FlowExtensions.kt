package org.araqnid.eventstore.subscription

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

suspend fun <T> Flow<T>.maxWith(comparator: Comparator<T>): T? {
    var maxValue: T? = null
    collect { value ->
        maxValue = maxValue?.let {
            if (comparator.compare(value, it) > 0) value else it
        } ?: value
    }
    return maxValue
}
