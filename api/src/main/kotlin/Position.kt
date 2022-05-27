package org.araqnid.eventstore

import kotlin.reflect.KClass
import kotlin.reflect.cast

interface Position

interface PositionCodec {
    fun encode(position: Position): String
    fun decode(encoded: String): Position
    fun comparePositions(left: Position, right: Position): Int
}

fun <T> positionCodecOfComparable(clazz: KClass<T>, encoder: (T) -> String, decoder: (String) -> T): PositionCodec where T : Position, T: Comparable<T> {
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
        override fun encode(position: Position): String = encoder(position as T)
        override fun decode(encoded: String): Position = decoder(encoded)

        override fun comparePositions(left: Position, right: Position): Int {
            val leftT = left as T
            val rightT = right as T
            return leftT.compareTo(rightT)
        }
    }
}

fun <T : Position> positionCodecFromComparator(clazz: KClass<T>, encoder: (T) -> String, decoder: (String) -> T, comparator: Comparator<in T>): PositionCodec {
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

inline fun <reified T : Position> positionCodecFromComparator(crossinline encoder: (T) -> String, crossinline decoder: (String) -> T, comparator: Comparator<in T>): PositionCodec {
    return object : PositionCodec {
        override fun encode(position: Position): String = encoder(position as T)
        override fun decode(encoded: String): Position = decoder(encoded)

        override fun comparePositions(left: Position, right: Position): Int {
            val leftT = left as T
            val rightT = right as T
            return comparator.compare(leftT, rightT)
        }
    }
}
