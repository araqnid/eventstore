package org.araqnid.eventstore.filesystem.flatpack

import org.araqnid.eventstore.testing.EventSourceApiComplianceTest
import org.araqnid.eventstore.testutil.NIOTemporaryFolder
import org.junit.Rule
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

class FlatPackFilesystemEventSourceComplianceTest : EventSourceApiComplianceTest() {
    @get:Rule
    val temporaryFolder = NIOTemporaryFolder()

    /**
     * filesystem event store loses ordering of events written at the same instant, so make sure that never happens
     */
    private object AutoTickingClock : Clock() {
        private val ticks = AtomicLong()

        override fun getZone() = ZoneId.systemDefault()

        override fun withZone(zone: ZoneId) = this

        override fun instant() = Instant.ofEpochSecond(ticks.getAndIncrement())
    }

    override val eventSource by lazy { FlatPackFilesystemEventSource(AutoTickingClock, temporaryFolder.root) }
}
