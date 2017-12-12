package org.araqnid.eventstore.subscription

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.timgroup.clocks.testing.ManualClock
import org.araqnid.eventstore.TestPosition
import org.junit.Test
import java.time.Clock
import java.time.Duration

class SnapshotTriggerTest {
    private val clock = ManualClock.initiallyAt(Clock.systemDefaultZone())
    private val trigger = SnapshotTrigger(TestPosition.codec, Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofMinutes(2), clock)

    @Test fun no_snapshot_in_default_state() {
        assertThat(trigger.writeNewSnapshot(), equalTo(false))
        assertThat(trigger.writeInitialSnapshot(), equalTo(false))
    }

    @Test fun no_snapshot_immediately_after_event_received() {
        trigger.eventReceived(TestPosition(1L))
        assertThat(trigger.writeNewSnapshot(), equalTo(false))
    }

    @Test fun write_initial_snapshot_immediately_after_event_received() {
        trigger.eventReceived(TestPosition(1L))
        assertThat(trigger.writeInitialSnapshot(), equalTo(true))
    }

    @Test fun write_initial_snapshot_after_event_received_beyond_loaded_snapshot() {
        trigger.snapshotLoaded(TestPosition(1L))
        trigger.eventReceived(TestPosition(2L))
        assertThat(trigger.writeInitialSnapshot(), equalTo(true))
    }

    @Test fun no_initial_snapshot_when_no_event_received_beyond_loaded_snapshot() {
        trigger.snapshotLoaded(TestPosition(1L))
        assertThat(trigger.writeInitialSnapshot(), equalTo(false))
    }

    @Test fun write_snapshot_after_event_received_and_quiet_period_has_passed() {
        trigger.eventReceived(TestPosition(1L))
        clock.bumpSeconds(40)
        assertThat(trigger.writeNewSnapshot(), equalTo(true))
    }

    @Test fun no_snapshot_when_another_event_restarts_the_quiet_period() {
        trigger.eventReceived(TestPosition(1L))
        clock.bumpSeconds(20)
        trigger.eventReceived(TestPosition(2L))
        clock.bumpSeconds(20)
        assertThat(trigger.writeNewSnapshot(), equalTo(false))
    }

    @Test fun snapshot_eventually_required_even_when_events_keep_arriving() {
        // after 2 minutes
        trigger.eventReceived(TestPosition(1L))
        clock.bumpSeconds(20) // PT20S
        trigger.eventReceived(TestPosition(2L))
        clock.bumpSeconds(20) // PT40S
        trigger.eventReceived(TestPosition(2L))
        clock.bumpSeconds(20) // PT1M
        trigger.eventReceived(TestPosition(2L))
        clock.bumpSeconds(20) // PT1M20S
        trigger.eventReceived(TestPosition(2L))
        clock.bumpSeconds(20) // PT1M40S
        trigger.eventReceived(TestPosition(2L))
        clock.bumpSeconds(20) // PT2M
        assertThat(trigger.writeNewSnapshot(), equalTo(true))
    }

    @Test fun no_snapshot_after_writing_snapshot_at_event_position() {
        trigger.eventReceived(TestPosition(1L))
        clock.bumpSeconds(40)
        trigger.snapshotWritten(TestPosition(1L))
        clock.bump(Duration.ofMinutes(10))
        assertThat(trigger.writeNewSnapshot(), equalTo(false))
    }

    @Test fun no_snapshot_immediately_after_new_event_after_snapshot_write() {
        trigger.eventReceived(TestPosition(1L))
        clock.bumpSeconds(40)
        trigger.snapshotWritten(TestPosition(1L))
        clock.bump(Duration.ofMinutes(10))
        trigger.eventReceived(TestPosition(2))
        assertThat(trigger.writeNewSnapshot(), equalTo(false))
    }

    @Test fun write_new_event_after_quiet_period() {
        trigger.eventReceived(TestPosition(1L))
        clock.bumpSeconds(40)
        trigger.snapshotWritten(TestPosition(1L))
        clock.bump(Duration.ofMinutes(10))
        trigger.eventReceived(TestPosition(2))
        clock.bumpSeconds(40)
        assertThat(trigger.writeNewSnapshot(), equalTo(true))
    }


    @Test fun no_snapshot_immediately_after_writing_snapshot_before_event_position() {
        trigger.eventReceived(TestPosition(1L))
        clock.bumpSeconds(40)
        trigger.snapshotWritten(TestPosition(0L))
        assertThat(trigger.writeNewSnapshot(), equalTo(false))
    }

    @Test fun still_need_snapshot_after_writing_snapshot_before_event_position_but_after_minimum_interval() {
        trigger.eventReceived(TestPosition(1L))
        clock.bumpSeconds(40)
        trigger.snapshotWritten(TestPosition(0L))
        clock.bump(Duration.ofMinutes(6))
        assertThat(trigger.writeNewSnapshot(), equalTo(true))
    }
}