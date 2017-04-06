package org.araqnid.eventstore

data class TestPosition(val version: Long) : Position, Comparable<TestPosition> {
    companion object {
        val codec = positionCodecOfComparable(TestPosition::class.java,
                { (version) -> version.toString() },
                { v -> TestPosition(v.toLong()) })
    }
    override fun compareTo(other: TestPosition): Int = version.compareTo(other.version)
}
