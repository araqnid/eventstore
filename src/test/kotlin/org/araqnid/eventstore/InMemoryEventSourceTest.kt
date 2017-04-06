package org.araqnid.eventstore

import java.time.Clock

class InMemoryEventSourceTest : EventSourceApiComplianceTest() {
    val clock: Clock = Clock.systemDefaultZone()
    val inMemoryEventSource = InMemoryEventSource(clock)

    override val eventSource: EventSource
        get() = inMemoryEventSource
}