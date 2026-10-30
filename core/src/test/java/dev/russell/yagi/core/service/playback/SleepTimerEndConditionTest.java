package dev.russell.yagi.core.service.playback;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import dev.russell.yagi.event.playback.SleepTimerMode;
import dev.russell.yagi.model.feed.Chapter;

/** Plain JUnit tests for {@link SleepTimerEndCondition}. */
public class SleepTimerEndConditionTest {

    private static final long DURATION = 600_000;
    /** Chapters at 0, 2:00, 5:00 and 8:00. */
    private static final long[] CHAPTERS = {120_000, 300_000, 480_000};

    private long now = 1_000_000;

    private SleepTimerEndCondition chapter() {
        return new SleepTimerEndCondition(SleepTimerMode.END_OF_CHAPTER);
    }

    private SleepTimerEndCondition episode() {
        return new SleepTimerEndCondition(SleepTimerMode.END_OF_EPISODE);
    }

    /** One ticker step: {@code wallMs} of wall-clock time later, at {@code position}. */
    private long tick(SleepTimerEndCondition condition, long wallMs, long position, float speed) {
        now += wallMs;
        return condition.update(now, position, DURATION, speed, CHAPTERS);
    }

    // ------------------------------------------------------------ end of episode

    @Test
    public void endOfEpisodeCountsDownToTheDuration() {
        SleepTimerEndCondition condition = episode();
        assertEquals(DURATION - 100_000, tick(condition, 0, 100_000, 1f));
        assertEquals(DURATION - 101_000, tick(condition, 1000, 101_000, 1f));
        assertTrue(condition.stopsAtEpisodeEnd());
    }

    @Test
    public void endOfEpisodeIgnoresChapters() {
        SleepTimerEndCondition condition = episode();
        assertEquals(DURATION - 119_000, tick(condition, 0, 119_000, 1f));
        tick(condition, 1000, 120_000, 1f);
        assertFalse(condition.isDue());
        assertEquals(DURATION - 121_000, tick(condition, 1000, 121_000, 1f));
    }

    @Test
    public void endOfEpisodeIsNeverDueItself() {
        // the episode completes normally; the service then does not start the next one
        SleepTimerEndCondition condition = episode();
        tick(condition, 0, DURATION - 500, 1f);
        assertEquals(0, tick(condition, 1000, DURATION, 1f));
        assertFalse(condition.isDue());
    }

    @Test
    public void unknownDurationLeavesTheTimeUnknown() {
        SleepTimerEndCondition condition = episode();
        assertEquals(SleepTimerEndCondition.UNKNOWN, condition.update(now, 5000, 0, 1f, null));
        assertEquals(SleepTimerEndCondition.UNKNOWN, condition.update(now, 5000, -1, 1f, null));
        assertFalse(condition.isDue());
    }

    @Test
    public void unknownPositionLeavesTheTimeUnknown() {
        SleepTimerEndCondition condition = chapter();
        assertEquals(SleepTimerEndCondition.UNKNOWN, condition.update(now, -1, DURATION, 1f, CHAPTERS));
        assertFalse(condition.isDue());
    }

    // ------------------------------------------------------------ end of chapter

    @Test
    public void endOfChapterTargetsTheNextChapterStart() {
        SleepTimerEndCondition condition = chapter();
        assertEquals(20_000, tick(condition, 0, 100_000, 1f));
        assertFalse(condition.stopsAtEpisodeEnd());
        assertFalse(condition.isDue());
    }

    @Test
    public void beforeTheFirstChapterTheTargetIsTheFirstChapter() {
        SleepTimerEndCondition condition = chapter();
        assertEquals(120_000, condition.update(now, 0, DURATION, 1f, CHAPTERS));
    }

    @Test
    public void exactlyAtAChapterStartTargetsTheFollowingOne() {
        SleepTimerEndCondition condition = chapter();
        assertEquals(180_000, tick(condition, 0, 120_000, 1f));
    }

    @Test
    public void reachingTheBoundaryIsDue() {
        SleepTimerEndCondition condition = chapter();
        tick(condition, 0, 119_000, 1f);
        assertFalse(condition.isDue());
        assertEquals(0, tick(condition, 1000, 120_000, 1f));
        assertTrue(condition.isDue());
    }

    @Test
    public void justBeforeTheBoundaryWithinTheToleranceIsDue() {
        SleepTimerEndCondition condition = chapter();
        tick(condition, 0, 119_000, 1f);
        tick(condition, 900, 120_000 - SleepTimerEndCondition.DUE_TOLERANCE_MS + 10, 1f);
        assertTrue(condition.isDue());
    }

    @Test
    public void aTickThatOvershootsTheBoundaryStillStops() {
        // the next chapter has started, but the target stays the boundary that was crossed
        SleepTimerEndCondition condition = chapter();
        tick(condition, 0, 119_400, 1f);
        assertEquals(0, tick(condition, 1000, 120_400, 1f));
        assertTrue(condition.isDue());
    }

    @Test
    public void lastChapterEndsWithTheEpisode() {
        SleepTimerEndCondition condition = chapter();
        assertEquals(DURATION - 500_000, tick(condition, 0, 500_000, 1f));
        assertTrue(condition.stopsAtEpisodeEnd());
        assertEquals(0, tick(condition, 1000, DURATION, 1f));
        assertFalse("the episode end goes through normal completion", condition.isDue());
    }

    @Test
    public void noChaptersMeansTheEndOfTheEpisode() {
        SleepTimerEndCondition condition = chapter();
        assertEquals(DURATION - 10_000, condition.update(now, 10_000, DURATION, 1f, null));
        assertTrue(condition.stopsAtEpisodeEnd());
        assertFalse(condition.isDue());
    }

    @Test
    public void chaptersLoadedLaterReanchor() {
        SleepTimerEndCondition condition = chapter();
        assertEquals(DURATION - 10_000, condition.update(now, 10_000, DURATION, 1f, null));
        now += 1000;
        assertEquals(109_000, condition.update(now, 11_000, DURATION, 1f, CHAPTERS));
        assertFalse(condition.stopsAtEpisodeEnd());
    }

    @Test
    public void unchangedChapterArraysDoNotReanchor() {
        // a fresh array with the same starts on every tick must keep the crossed target
        SleepTimerEndCondition condition = chapter();
        condition.update(now, 119_400, DURATION, 1f, CHAPTERS.clone());
        now += 1000;
        assertEquals(0, condition.update(now, 120_400, DURATION, 1f, CHAPTERS.clone()));
        assertTrue(condition.isDue());
    }

    // ------------------------------------------------------------ seeking

    @Test
    public void seekingForwardIntoALaterChapterTargetsItsEnd() {
        SleepTimerEndCondition condition = chapter();
        tick(condition, 0, 100_000, 1f);
        assertEquals(10_000, tick(condition, 1000, 290_000, 1f));
        assertFalse(condition.isDue());
    }

    @Test
    public void seekingPastTheBoundaryDoesNotStopImmediately() {
        SleepTimerEndCondition condition = chapter();
        tick(condition, 0, 110_000, 1f);
        // fast forward 30 s across the 2:00 boundary
        assertEquals(160_000, tick(condition, 1000, 140_000, 1f));
        assertFalse(condition.isDue());
    }

    @Test
    public void seekingBackIntoAnEarlierChapterTargetsItsEnd() {
        SleepTimerEndCondition condition = chapter();
        tick(condition, 0, 310_000, 1f);
        assertEquals(20_000, tick(condition, 1000, 100_000, 1f));
    }

    @Test
    public void aShortRewindInTheSameChapterKeepsTheTarget() {
        SleepTimerEndCondition condition = chapter();
        tick(condition, 0, 115_000, 1f);
        assertEquals(15_000, tick(condition, 1000, 105_000, 1f));
    }

    @Test
    public void seekingIntoTheLastChapterTargetsTheEpisodeEnd() {
        SleepTimerEndCondition condition = chapter();
        tick(condition, 0, 100_000, 1f);
        assertEquals(DURATION - 500_000, tick(condition, 1000, 500_000, 1f));
        assertTrue(condition.stopsAtEpisodeEnd());
    }

    @Test
    public void resetAnchorFollowsTheNewPosition() {
        // e.g. a seek while paused: the gap in wall time hides it, the reset on resume does not
        SleepTimerEndCondition condition = chapter();
        tick(condition, 0, 110_000, 1f);
        condition.resetAnchor();
        assertEquals(160_000, tick(condition, 600_000, 140_000, 1f));
        assertFalse(condition.isDue());
    }

    // ------------------------------------------------------------ speed

    @Test
    public void fastPlaybackIsNotMistakenForASeek() {
        SleepTimerEndCondition condition = chapter();
        tick(condition, 0, 117_000, 3f);
        // three seconds of content per wall second crosses the boundary in one tick
        assertEquals(0, tick(condition, 1000, 120_000, 3f));
        assertTrue(condition.isDue());
    }

    @Test
    public void aSpeedChangeKeepsTheTarget() {
        SleepTimerEndCondition condition = chapter();
        tick(condition, 0, 100_000, 1f);
        assertEquals(18_000, tick(condition, 1000, 102_000, 2f));
        assertEquals(15_500, tick(condition, 1000, 104_500, 2.5f));
    }

    @Test
    public void wallClockIsContentTimeDividedBySpeed() {
        assertEquals(10_000, SleepTimerEndCondition.toWallClock(10_000, 1f));
        assertEquals(5_000, SleepTimerEndCondition.toWallClock(10_000, 2f));
        assertEquals(6_667, SleepTimerEndCondition.toWallClock(10_000, 1.5f));
        assertEquals(20_000, SleepTimerEndCondition.toWallClock(10_000, 0.5f));
        assertEquals(10_000, SleepTimerEndCondition.toWallClock(10_000, 0f));
        assertEquals(SleepTimerEndCondition.UNKNOWN,
                SleepTimerEndCondition.toWallClock(SleepTimerEndCondition.UNKNOWN, 2f));
    }

    // ------------------------------------------------------------ chapter list

    @Test
    public void chapterStartsAreSortedUniqueAndSkipTheStart() {
        List<Chapter> chapters = new ArrayList<>(Arrays.asList(
                new Chapter(300_000, "c", null, null),
                new Chapter(0, "a", null, null),
                new Chapter(120_000, "b", null, null),
                new Chapter(120_000, "b again", null, null)));
        assertArrayEquals(new long[] {120_000, 300_000}, SleepTimerEndCondition.chapterStarts(chapters));
        assertTrue(SleepTimerEndCondition.hasChapterBoundaries(chapters));
    }

    @Test
    public void noUsableChaptersMeansNoBoundaries() {
        assertNull(SleepTimerEndCondition.chapterStarts(null));
        assertNull(SleepTimerEndCondition.chapterStarts(Collections.emptyList()));
        List<Chapter> onlyStart = Collections.singletonList(new Chapter(0, "a", null, null));
        assertNull(SleepTimerEndCondition.chapterStarts(onlyStart));
        assertFalse(SleepTimerEndCondition.hasChapterBoundaries(onlyStart));
    }

    @Test(expected = IllegalArgumentException.class)
    public void durationIsNotABoundaryMode() {
        new SleepTimerEndCondition(SleepTimerMode.DURATION);
    }
}
