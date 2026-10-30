package dev.russell.yagi.core.service.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import dev.russell.yagi.event.playback.SleepTimerMode;
import dev.russell.yagi.event.playback.SleepTimerUpdatedEvent;

/** Plain JUnit tests for {@link SleepTimerUpdatedEvent} and {@link SleepTimerMode}. */
public class SleepTimerUpdatedEventTest {

    @Test
    public void durationEventsKeepTheirMeaning() {
        SleepTimerUpdatedEvent enabled = SleepTimerUpdatedEvent.justEnabled(60_000);
        assertTrue(enabled.wasJustEnabled());
        assertFalse(enabled.isOver());
        assertFalse(enabled.isCancelled());
        assertEquals(60_000, enabled.getTimeLeft());
        assertEquals(SleepTimerMode.DURATION, enabled.getMode());

        SleepTimerUpdatedEvent updated = SleepTimerUpdatedEvent.updated(5_000);
        assertFalse(updated.wasJustEnabled());
        assertFalse(updated.isOver());
        assertEquals(5_000, updated.getTimeLeft());

        assertTrue(SleepTimerUpdatedEvent.updated(0).isOver());
        assertTrue("an overrun clamps to over", SleepTimerUpdatedEvent.updated(-400).isOver());
        assertTrue("a duration timer is never unknown", SleepTimerUpdatedEvent.updated(-1).isOver());
        assertTrue(SleepTimerUpdatedEvent.cancelled().isCancelled());
        assertFalse(SleepTimerUpdatedEvent.cancelled().isOver());
    }

    @Test
    public void boundaryEventsCarryTheirMode() {
        SleepTimerUpdatedEvent enabled =
                SleepTimerUpdatedEvent.justEnabled(30_000, SleepTimerMode.END_OF_CHAPTER);
        assertTrue(enabled.wasJustEnabled());
        assertEquals(SleepTimerMode.END_OF_CHAPTER, enabled.getMode());
        assertTrue(SleepTimerUpdatedEvent.updated(0, SleepTimerMode.END_OF_EPISODE).isOver());
    }

    @Test
    public void unknownTimeLeftIsNeitherOverNorClamped() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.updated(
                SleepTimerUpdatedEvent.TIME_LEFT_UNKNOWN, SleepTimerMode.END_OF_EPISODE);
        assertFalse(event.isTimeLeftKnown());
        assertFalse(event.isOver());
        assertEquals(SleepTimerUpdatedEvent.TIME_LEFT_UNKNOWN, event.getTimeLeft());
        assertTrue(SleepTimerUpdatedEvent.updated(1, SleepTimerMode.END_OF_EPISODE).isTimeLeftKnown());
    }

    @Test
    public void modeNamesRoundTripAndFallBackToDuration() {
        for (SleepTimerMode mode : SleepTimerMode.values()) {
            assertEquals(mode, SleepTimerMode.fromName(mode.name()));
        }
        assertEquals(SleepTimerMode.DURATION, SleepTimerMode.fromName(null));
        assertEquals(SleepTimerMode.DURATION, SleepTimerMode.fromName("SOMETHING_ELSE"));
    }
}
