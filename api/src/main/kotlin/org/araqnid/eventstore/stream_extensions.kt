package org.araqnid.eventstore

import java.util.Objects
import java.util.stream.Collector
import java.util.stream.Stream
import kotlin.streams.toList

fun <T, R> Stream<T>.collectAndClose(collector: Collector<in T, *, out R>): R = use { it.collect(collector) }
fun <T> Stream<T>.toListAndClose(): List<T> = use { it.toList() }
fun <T> Stream<T>.forEachOrderedAndClose(action: (T) -> Unit) = use { it.forEachOrdered(action) }
fun <T> Stream<T?>.filterNotNull(): Stream<T> {
    @Suppress("UNCHECKED_CAST")
    return this.filter(Objects::nonNull) as Stream<T>
}
fun <T> Stream<T>.onlyElement(): T? = with(toListAndClose()) {
    if (size != 1) throw IllegalArgumentException("stream produced more than one element: $this")
    get(0)
}
