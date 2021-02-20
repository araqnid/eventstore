package org.araqnid.eventstore

import java.util.concurrent.CopyOnWriteArrayList

internal actual fun createLocalEventSourceContent(): MutableList<ResolvedEvent> = CopyOnWriteArrayList()
