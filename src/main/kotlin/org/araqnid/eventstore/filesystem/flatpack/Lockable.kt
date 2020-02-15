package org.araqnid.eventstore.filesystem.flatpack

import com.google.errorprone.annotations.CheckReturnValue
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

class Lockable {
    private val lock: ReadWriteLock = ReentrantReadWriteLock()

    private inner class Resource(private val lock: Lock) : AutoCloseable {
        init {
            lock.lock()
        }

        override fun close() {
            lock.unlock()
        }
    }

    @CheckReturnValue
    fun acquireRead(): AutoCloseable = Resource(lock.readLock())

    @CheckReturnValue
    fun acquireWrite(): AutoCloseable = Resource(lock.writeLock())
}
