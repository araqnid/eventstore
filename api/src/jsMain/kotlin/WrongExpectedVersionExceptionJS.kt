package org.araqnid.eventstore

actual val Throwable.isWrongExpectedVersionException: Boolean
    get() = "WrongExpectedVersionException" in this.toString()
