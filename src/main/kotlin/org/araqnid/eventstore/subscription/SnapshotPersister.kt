package org.araqnid.eventstore.subscription

import org.araqnid.eventstore.Position

interface SnapshotPersister {
    fun load(): Position?
    fun save(): Position
}
