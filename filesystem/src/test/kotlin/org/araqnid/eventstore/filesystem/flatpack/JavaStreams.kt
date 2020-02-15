package org.araqnid.eventstore.filesystem.flatpack

internal fun <T, R> java.util.stream.Stream<T>.collectAndClose(collector: java.util.stream.Collector<in T, *, out R>): R = use { it.collect(collector) }
internal fun <T> java.util.stream.Stream<T>.toListAndClose(): List<T> = collectAndClose(java.util.stream.Collectors.toList())
internal fun <T> java.util.stream.Stream<T>.onlyElement(): T? = with(toListAndClose()) {
    if (size != 1) throw IllegalArgumentException("stream produced more than one element: $this")
    get(0)
}
