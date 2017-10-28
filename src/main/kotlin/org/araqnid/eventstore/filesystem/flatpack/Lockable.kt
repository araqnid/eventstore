package org.araqnid.eventstore.filesystem.flatpack

import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.annotation.CheckReturnValue

class Lockable {
    private val lock: ReadWriteLock = ReentrantReadWriteLock()

    inner class Resource(private val lock: Lock) : AutoCloseable {
        init {
            lock.lock()
        }

        override fun close() {
            lock.unlock()
        }
    }

    @CheckReturnValue
    fun acquireRead() = Resource(lock.readLock())

    @CheckReturnValue
    fun acquireWrite() = Resource(lock.writeLock())
}