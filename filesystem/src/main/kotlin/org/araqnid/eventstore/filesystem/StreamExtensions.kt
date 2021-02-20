package org.araqnid.eventstore.filesystem

import java.util.Objects
import java.util.stream.Stream

internal fun <T, U : Any> Stream<T>.mapNotNull(mapper: (T) -> U?): Stream<U> {
    @Suppress("UNCHECKED_CAST")
    return this.map(mapper).filter(Objects::nonNull) as Stream<U>
}

internal fun <T, R> Stream<T>.collectAndClose(collector: java.util.stream.Collector<in T, *, out R>): R = use {
    it.collect(collector)
}
