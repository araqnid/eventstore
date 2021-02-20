package org.araqnid.eventstore

import org.araqnid.eventstore.testing.EventSourceApiComplianceTest

class LocalEventSourceTest : EventSourceApiComplianceTest() {
    val localEventSource = LocalEventSource()

    override val eventSource: EventSource
        get() = localEventSource
}
