package org.araqnid.eventstore

internal inline fun <T : AutoCloseable, R> T.useResource(block: (T) -> R): R {
    var closed = false
    try {
        return block(this)
    } catch (e: Exception) {
        closed = true
        try {
            this.close()
        } catch (closeException: Exception) {
            e.addSuppressed(closeException)
        }
        throw e
    } finally {
        if (!closed) {
            this.close()
        }
    }
}
