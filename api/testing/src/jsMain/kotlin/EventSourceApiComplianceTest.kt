package org.araqnid.eventstore.testing

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.araqnid.eventstore.EventSource
import kotlin.test.Test

actual abstract class EventSourceApiComplianceTest {
    actual abstract val eventSource: EventSource

    private val impls by lazy { ComplianceTestImplementations(eventSource) }

    @Test fun read_events_written_to_stream() = GlobalScope.promise {
        impls.read_events_written_to_stream()
    }

    @Test fun read_and_write_to_streams_independently() = GlobalScope.promise {
        impls.read_and_write_to_streams_independently()
    }

    @Test fun write_events_specifying_expected_version_number() = GlobalScope.promise {
        impls.write_events_specifying_expected_version_number()
    }

    @Test fun write_events_specifying_expected_empty_version_number() = GlobalScope.promise {
        impls.write_events_specifying_expected_empty_version_number()
    }

    @Test fun fails_if_expected_event_number_not_satisfied_yet() = GlobalScope.promise {
        impls.fails_if_expected_event_number_not_satisfied_yet()
    }

    @Test fun fails_if_expected_event_number_already_passed() = GlobalScope.promise {
        impls.fails_if_expected_event_number_already_passed()
    }

    @Test fun read_stream_after_specific_event_number() = GlobalScope.promise {
        impls.read_stream_after_specific_event_number()
    }

    @Test fun read_empty_after_end_of_stream() = GlobalScope.promise {
        impls.read_empty_after_end_of_stream()
    }

    @Test fun read_all_events() = GlobalScope.promise {
        impls.read_all_events()
    }

    @Test fun read_all_events_from_position() = GlobalScope.promise {
        impls.read_all_events_from_position()
    }

    @Test fun read_empty_after_end_of_store() = GlobalScope.promise {
        impls.read_empty_after_end_of_store()
    }
}
