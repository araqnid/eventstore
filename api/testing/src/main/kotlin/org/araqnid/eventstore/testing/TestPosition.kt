package org.araqnid.eventstore.testing

import org.araqnid.eventstore.Position
import org.araqnid.eventstore.positionCodecOfComparable

data class TestPosition(val version: Long) : Position, Comparable<TestPosition> {
    companion object {
        val codec = positionCodecOfComparable(
                { (version) -> version.toString() },
                { v -> TestPosition(v.toLong()) })
    }
    override fun compareTo(other: TestPosition): Int = version.compareTo(other.version)
}
