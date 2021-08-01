package org.araqnid.eventstore.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.araqnid.eventstore.EventSource
import kotlin.js.Promise
import kotlin.test.Test

actual abstract class EventSourceApiComplianceTest {
    actual abstract val eventSource: EventSource

    private val impls by lazy { ComplianceTestImplementations(eventSource) }

    @Test
    fun read_events_written_to_stream() =
        runTest(impls::read_events_written_to_stream)

    @Test
    fun read_and_write_to_streams_independently() =
        runTest(impls::read_and_write_to_streams_independently)

    @Test
    fun write_events_specifying_expected_version_number() =
        runTest(impls::write_events_specifying_expected_version_number)

    @Test
    fun write_events_specifying_expected_empty_version_number() =
        runTest(impls::write_events_specifying_expected_empty_version_number)

    @Test
    fun fails_if_expected_event_number_not_satisfied_yet() =
        runTest(impls::fails_if_expected_event_number_not_satisfied_yet)

    @Test
    fun fails_if_expected_event_number_already_passed() =
        runTest(impls::fails_if_expected_event_number_already_passed)

    @Test
    fun read_stream_after_specific_event_number() =
        runTest(impls::read_stream_after_specific_event_number)

    @Test
    fun read_empty_after_end_of_stream() =
        runTest(impls::read_empty_after_end_of_stream)

    @Test
    fun read_all_events() =
        runTest(impls::read_all_events)

    @Test
    fun read_all_events_from_position() =
        runTest(impls::read_all_events_from_position)

    @Test
    fun read_empty_after_end_of_store() =
        runTest(impls::read_empty_after_end_of_store)
}

private fun runTest(testBody: suspend () -> Unit): Promise<Unit> {
    return Promise { resolve, reject ->
        val job = Job()
        val scope = CoroutineScope(job)
        scope.launch {
            testBody()
        }
        job.invokeOnCompletion { ex: Throwable? ->
            if (ex == null)
                resolve(Unit)
            else
                reject(ex)
        }
        job.complete()
    }
}
