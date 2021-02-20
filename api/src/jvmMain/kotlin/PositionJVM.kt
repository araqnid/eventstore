package org.araqnid.eventstore

fun <T> positionCodecOfComparable(clazz: Class<T>, encoder: (T) -> String, decoder: (String) -> T): PositionCodec where T : Position, T: Comparable<T> {
    return object : PositionCodec {
        override fun encode(position: Position): String = encoder(clazz.cast(position))
        override fun decode(encoded: String): Position = decoder(encoded)

        override fun comparePositions(left: Position, right: Position): Int {
            val leftT = clazz.cast(left)
            val rightT = clazz.cast(right)
            return leftT.compareTo(rightT)
        }
    }
}

fun <T : Position> positionCodecFromComparator(clazz: Class<T>, encoder: (T) -> String, decoder: (String) -> T, comparator: Comparator<in T>): PositionCodec {
    return object : PositionCodec {
        override fun encode(position: Position): String = encoder(clazz.cast(position))
        override fun decode(encoded: String): Position = decoder(encoded)

        override fun comparePositions(left: Position, right: Position): Int {
            val leftT = clazz.cast(left)
            val rightT = clazz.cast(right)
            return comparator.compare(leftT, rightT)
        }
    }
}
