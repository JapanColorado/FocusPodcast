package dev.russell.focuspodcast.core.service.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import dev.russell.focuspodcast.core.service.playback.NotificationCustomActionResolver.State;
import dev.russell.focuspodcast.event.playback.SleepTimerMode;
import dev.russell.focuspodcast.model.feed.AdSegment;

/** Plain JUnit tests for the extra notification / media session buttons. */
public class NotificationCustomActionResolverTest {

    private static final NotificationCustomAction CHAPTER = NotificationCustomAction.NEXT_CHAPTER;
    private static final NotificationCustomAction SPEED = NotificationCustomAction.CYCLE_SPEED;
    private static final NotificationCustomAction SLEEP = NotificationCustomAction.SLEEP_TIMER_EXTEND;
    private static final NotificationCustomAction AD = NotificationCustomAction.SKIP_AD;

    private static State state(boolean nextChapter, boolean inAd, SleepTimerMode sleep, boolean presets) {
        return new State(nextChapter, inAd, sleep, presets);
    }

    private static final State EVERYTHING = state(true, true, null, true);
    private static final State NOTHING_CONDITIONAL = state(false, false, null, true);

    // --- resolve ---

    @Test
    public void nothingSelectedShowsNothing() {
        assertTrue(NotificationCustomActionResolver.resolve(Collections.emptyList(), EVERYTHING).isEmpty());
    }

    @Test
    public void keepsTheUsersOrder() {
        assertEquals(Arrays.asList(SPEED, CHAPTER),
                NotificationCustomActionResolver.resolve(Arrays.asList(SPEED, CHAPTER), EVERYTHING));
        assertEquals(Arrays.asList(CHAPTER, SPEED),
                NotificationCustomActionResolver.resolve(Arrays.asList(CHAPTER, SPEED), EVERYTHING));
    }

    @Test
    public void nextChapterOnlyWhenThereIsOne() {
        assertEquals(Collections.singletonList(SPEED),
                NotificationCustomActionResolver.resolve(Arrays.asList(CHAPTER, SPEED), NOTHING_CONDITIONAL));
        assertEquals(Arrays.asList(CHAPTER, SPEED), NotificationCustomActionResolver.resolve(
                Arrays.asList(CHAPTER, SPEED), state(true, false, null, true)));
    }

    @Test
    public void skipAdOnlyInsideASegment() {
        assertTrue(NotificationCustomActionResolver.resolve(
                Collections.singletonList(AD), NOTHING_CONDITIONAL).isEmpty());
        assertEquals(Collections.singletonList(AD), NotificationCustomActionResolver.resolve(
                Collections.singletonList(AD), state(false, true, null, true)));
    }

    @Test
    public void sleepExtendWithNoTimerOrADurationTimer() {
        List<NotificationCustomAction> sleep = Collections.singletonList(SLEEP);
        assertEquals(sleep, NotificationCustomActionResolver.resolve(sleep, state(false, false, null, true)));
        assertEquals(sleep, NotificationCustomActionResolver.resolve(sleep,
                state(false, false, SleepTimerMode.DURATION, true)));
    }

    @Test
    public void sleepExtendHiddenForBoundaryTimers() {
        List<NotificationCustomAction> sleep = Collections.singletonList(SLEEP);
        assertTrue(NotificationCustomActionResolver.resolve(sleep,
                state(false, false, SleepTimerMode.END_OF_EPISODE, true)).isEmpty());
        assertTrue(NotificationCustomActionResolver.resolve(sleep,
                state(false, false, SleepTimerMode.END_OF_CHAPTER, true)).isEmpty());
    }

    @Test
    public void speedNeedsPresets() {
        assertTrue(NotificationCustomActionResolver.resolve(
                Collections.singletonList(SPEED), state(true, true, null, false)).isEmpty());
    }

    @Test
    public void neverMoreThanTheMaximumAndNoDuplicates() {
        List<NotificationCustomAction> visible = NotificationCustomActionResolver.resolve(
                Arrays.asList(AD, AD, CHAPTER, SPEED, SLEEP), EVERYTHING);
        assertEquals(Arrays.asList(AD, CHAPTER), visible);
        assertEquals(NotificationCustomAction.MAX_SELECTED, visible.size());
    }

    // --- next chapter ---

    @Test
    public void nextChapterIsTheFirstStartAfterThePosition() {
        long[] starts = {60_000, 120_000, 300_000};
        assertEquals(60_000, NotificationCustomActionResolver.nextChapterStart(starts, 0));
        assertEquals(120_000, NotificationCustomActionResolver.nextChapterStart(starts, 60_000));
        assertEquals(300_000, NotificationCustomActionResolver.nextChapterStart(starts, 200_000));
    }

    @Test
    public void nextChapterSkipsAStartJustAhead() {
        // After seeking to a chapter start the player may report a position just before it.
        long[] starts = {60_000, 120_000};
        long justBefore = 60_000 - NotificationCustomActionResolver.NEXT_CHAPTER_TOLERANCE_MS / 2;
        assertEquals(120_000, NotificationCustomActionResolver.nextChapterStart(starts, justBefore));
        long wellBefore = 60_000 - NotificationCustomActionResolver.NEXT_CHAPTER_TOLERANCE_MS - 1;
        assertEquals(60_000, NotificationCustomActionResolver.nextChapterStart(starts, wellBefore));
    }

    @Test
    public void noNextChapterInTheLastOneOrWithoutChapters() {
        long[] starts = {60_000, 120_000};
        assertEquals(NotificationCustomActionResolver.NO_CHAPTER,
                NotificationCustomActionResolver.nextChapterStart(starts, 130_000));
        assertEquals(NotificationCustomActionResolver.NO_CHAPTER,
                NotificationCustomActionResolver.nextChapterStart(null, 0));
        assertEquals(NotificationCustomActionResolver.NO_CHAPTER,
                NotificationCustomActionResolver.nextChapterStart(new long[0], 0));
    }

    @Test
    public void noNextChapterForAnUnknownPosition() {
        assertEquals(NotificationCustomActionResolver.NO_CHAPTER,
                NotificationCustomActionResolver.nextChapterStart(new long[]{60_000}, -1));
    }

    // --- speed cycling ---

    private static final List<Float> PRESETS = Arrays.asList(0.8f, 1.0f, 1.2f, 1.5f, 2.0f);

    @Test
    public void stepsToTheNextPreset() {
        assertEquals(1.2f, NotificationCustomActionResolver.nextSpeed(PRESETS, 1.0f), 0.0001f);
        assertEquals(2.0f, NotificationCustomActionResolver.nextSpeed(PRESETS, 1.5f), 0.0001f);
    }

    @Test
    public void wrapsAroundAfterTheHighest() {
        assertEquals(0.8f, NotificationCustomActionResolver.nextSpeed(PRESETS, 2.0f), 0.0001f);
        assertEquals(0.8f, NotificationCustomActionResolver.nextSpeed(PRESETS, 3.0f), 0.0001f);
    }

    @Test
    public void aSpeedThatIsNoPresetStepsToTheNextHigher() {
        assertEquals(1.2f, NotificationCustomActionResolver.nextSpeed(PRESETS, 1.1f), 0.0001f);
        assertEquals(0.8f, NotificationCustomActionResolver.nextSpeed(PRESETS, 0.5f), 0.0001f);
    }

    @Test
    public void toleratesRoundingOfTheCurrentSpeed() {
        assertEquals(1.5f, NotificationCustomActionResolver.nextSpeed(PRESETS, 1.2000001f), 0.0001f);
        assertEquals(1.5f, NotificationCustomActionResolver.nextSpeed(PRESETS, 1.1999999f), 0.0001f);
    }

    @Test
    public void presetOrderDoesNotMatter() {
        List<Float> unsorted = Arrays.asList(2.0f, 1.0f, 1.5f);
        assertEquals(1.5f, NotificationCustomActionResolver.nextSpeed(unsorted, 1.0f), 0.0001f);
        assertEquals(1.0f, NotificationCustomActionResolver.nextSpeed(unsorted, 2.0f), 0.0001f);
    }

    @Test
    public void withoutPresetsTheSpeedStays() {
        assertEquals(1.3f, NotificationCustomActionResolver.nextSpeed(Collections.emptyList(), 1.3f), 0.0001f);
        assertEquals(1.3f, NotificationCustomActionResolver.nextSpeed(null, 1.3f), 0.0001f);
    }

    // --- stored selection ---

    @Test
    public void selectionRoundTrips() {
        List<NotificationCustomAction> selection = Arrays.asList(AD, CHAPTER);
        assertEquals(selection, NotificationCustomAction.parse(NotificationCustomAction.serialize(selection)));
        assertEquals("", NotificationCustomAction.serialize(Collections.emptyList()));
    }

    @Test
    public void parseDropsUnknownDuplicateAndExtraEntries() {
        assertTrue(NotificationCustomAction.parse(null).isEmpty());
        assertTrue(NotificationCustomAction.parse("").isEmpty());
        assertEquals(Arrays.asList(SPEED, SLEEP),
                NotificationCustomAction.parse("bogus,cycle_speed,cycle_speed, sleep_extend,skip_ad"));
    }

    @Test
    public void sessionActionsMapBack() {
        for (NotificationCustomAction action : NotificationCustomAction.values()) {
            assertSame(action, NotificationCustomAction.fromSessionAction(action.getSessionAction()));
        }
        assertNull(NotificationCustomAction.fromSessionAction(PlaybackServiceMediaSession.CUSTOM_ACTION_REWIND));
        assertNull(NotificationCustomAction.fromSessionAction(null));
    }

    // --- ad segment lookup for the skip button ---

    private static AdSegment segment(long id, long start, long end, AdSegment.Source source, float confidence) {
        return new AdSegment(id, 1, start, end, source, confidence, true);
    }

    @Test
    public void skipButtonFindsTheSegmentRegardlessOfEntryPoint() {
        AdSegment ad = segment(1, 60_000, 120_000, AdSegment.Source.CHAPTER, 1f);
        List<AdSegment> segments = Collections.singletonList(ad);
        assertSame(ad, PlaybackServiceAdSkipper.findSegmentAt(segments, 60_000, 0.5f));
        assertSame(ad, PlaybackServiceAdSkipper.findSegmentAt(segments, 100_000, 0.5f));
        assertNull(PlaybackServiceAdSkipper.findSegmentAt(segments, 120_000, 0.5f));
        assertNull(PlaybackServiceAdSkipper.findSegmentAt(segments, 59_999, 0.5f));
    }

    @Test
    public void skipButtonIgnoresDisabledAndUnconfidentSegments() {
        AdSegment disabled = segment(1, 0, 10_000, AdSegment.Source.MANUAL, 1f);
        disabled.setEnabled(false);
        AdSegment weak = segment(2, 20_000, 30_000, AdSegment.Source.DETECTED, 0.4f);
        AdSegment strong = segment(3, 40_000, 50_000, AdSegment.Source.DETECTED, 0.9f);
        List<AdSegment> segments = Arrays.asList(disabled, weak, strong);
        assertNull(PlaybackServiceAdSkipper.findSegmentAt(segments, 5_000, 0.5f));
        assertNull(PlaybackServiceAdSkipper.findSegmentAt(segments, 25_000, 0.5f));
        assertSame(weak, PlaybackServiceAdSkipper.findSegmentAt(segments, 25_000, 0.3f));
        assertSame(strong, PlaybackServiceAdSkipper.findSegmentAt(segments, 45_000, 0.5f));
    }
}
