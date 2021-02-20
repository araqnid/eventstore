package org.araqnid.eventstore

actual val Throwable.isWrongExpectedVersionException: Boolean
    get() = this is WrongExpectedVersionException
