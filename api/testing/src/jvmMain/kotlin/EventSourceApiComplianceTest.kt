package org.araqnid.eventstore.testing

import kotlinx.coroutines.runBlocking
import org.araqnid.eventstore.EventSource
import org.junit.Test

actual abstract class EventSourceApiComplianceTest {
    actual abstract val eventSource: EventSource

    private val impls by lazy { ComplianceTestImplementations(eventSource) }

    @Test fun read_events_written_to_stream() = runBlocking {
        impls.read_events_written_to_stream()
    }

    @Test fun read_and_write_to_streams_independently() = runBlocking {
        impls.read_and_write_to_streams_independently()
    }

    @Test fun write_events_specifying_expected_version_number() = runBlocking {
        impls.write_events_specifying_expected_version_number()
    }

    @Test fun write_events_specifying_expected_empty_version_number() = runBlocking {
        impls.write_events_specifying_expected_empty_version_number()
    }

    @Test fun fails_if_expected_event_number_not_satisfied_yet() = runBlocking {
        impls.fails_if_expected_event_number_not_satisfied_yet()
    }

    @Test fun fails_if_expected_event_number_already_passed() = runBlocking {
        impls.fails_if_expected_event_number_already_passed()
    }

    @Test fun read_stream_after_specific_event_number() = runBlocking {
        impls.read_stream_after_specific_event_number()
    }

    @Test fun read_empty_after_end_of_stream() = runBlocking {
        impls.read_empty_after_end_of_stream()
    }

    @Test fun read_all_events() = runBlocking {
        impls.read_all_events()
    }

    @Test fun read_all_events_from_position() = runBlocking {
        impls.read_all_events_from_position()
    }

    @Test fun read_empty_after_end_of_store() = runBlocking {
        impls.read_empty_after_end_of_store()
    }
}
