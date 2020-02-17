package org.araqnid.eventstore.filesystem

import java.util.Objects
import java.util.stream.Stream

internal fun <T> Stream<T?>.filterNotNull(): Stream<T> {
    @Suppress("UNCHECKED_CAST")
    return this.filter(Objects::nonNull) as Stream<T>
}

internal fun <T, R> Stream<T>.collectAndClose(collector: java.util.stream.Collector<in T, *, out R>): R = use {
    it.collect(collector)
}
