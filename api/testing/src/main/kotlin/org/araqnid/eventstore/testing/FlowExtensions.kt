package org.araqnid.eventstore.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.araqnid.eventstore.EventRecord
import org.araqnid.eventstore.ResolvedEvent

suspend fun <T> Flow<T>.maxWith(comparator: Comparator<T>): T? {
    var maxValue: T? = null
    collect { value ->
        maxValue = maxValue?.let {
            if (comparator.compare(value, it) > 0) value else it
        } ?: value
    }
    return maxValue
}

fun <T> Flow<T>.blockingToList(): List<T> = runBlocking { toList() }

fun Flow<ResolvedEvent>.readEvents(): List<EventRecord> = map { it.event }.blockingToList()
