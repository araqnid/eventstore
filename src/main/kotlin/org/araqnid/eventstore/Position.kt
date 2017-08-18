package org.araqnid.eventstore

interface Position

interface PositionCodec {
    fun encode(position: Position): String
    fun decode(encoded: String): Position
    fun comparePositions(left: Position, right: Position): Int
}

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

inline fun <reified T> positionCodecOfComparable(crossinline encoder: (T) -> String, crossinline decoder: (String) -> T): PositionCodec where T : Position, T: Comparable<T> {
    return object : PositionCodec {
        override fun encode(position: Position): String = encoder(T::class.java.cast(position))
        override fun decode(encoded: String): Position = decoder(encoded)

        override fun comparePositions(left: Position, right: Position): Int {
            val leftT = T::class.java.cast(left)
            val rightT = T::class.java.cast(right)
            return leftT.compareTo(rightT)
        }
    }
}

fun <T : Position> positionCodecFromComparator(clazz: Class<T>, encoder: (T) -> String, decoder: (String) -> T, comparator: Comparator<T>): PositionCodec {
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

inline fun <reified T : Position> positionCodecFromComparator(crossinline encoder: (T) -> String, crossinline decoder: (String) -> T, comparator: Comparator<T>): PositionCodec {
    return object : PositionCodec {
        override fun encode(position: Position): String = encoder(T::class.java.cast(position))
        override fun decode(encoded: String): Position = decoder(encoded)

        override fun comparePositions(left: Position, right: Position): Int {
            val leftT = T::class.java.cast(left)
            val rightT = T::class.java.cast(right)
            return comparator.compare(leftT, rightT)
        }
    }
}
