package org.araqnid.eventstore.filesystem

import java.util.Objects
import java.util.stream.Stream

internal fun <T, U : Any> Stream<T>.mapNotNull(mapper: (T) -> U?): Stream<U> {
    @Suppress("UNCHECKED_CAST")
    return this.map(mapper).filter(Objects::nonNull) as Stream<U>
}
