package allen.town.podcast.core.service.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import allen.town.podcast.model.feed.AdSegment;

/**
 * Plain JUnit tests for the pure decision logic of {@link PlaybackServiceAdSkipper}: which
 * segment (if any) a given playback position should skip, and where a skip lands.
 */
public class PlaybackServiceAdSkipperTest {

    private static final float MEDIUM_CONFIDENCE = 0.60f;
    private static final long ITEM_ID = 42;

    private final Set<Long> suppressed = new HashSet<>();

    private static AdSegment segment(long id, long startMs, long endMs, AdSegment.Source source,
                                     float confidence) {
        return new AdSegment(id, ITEM_ID, startMs, endMs, source, confidence, true);
    }

    private static AdSegment detected(long id, long startMs, long endMs, float confidence) {
        return segment(id, startMs, endMs, AdSegment.Source.DETECTED, confidence);
    }

    private static List<AdSegment> list(AdSegment... segments) {
        return new ArrayList<>(Arrays.asList(segments));
    }

    // ------------------------------------------------------------ entering a segment

    @Test
    public void playingIntoASegmentSkipsIt() {
        AdSegment ad = detected(1, 60_000, 120_000, 0.9f);
        AdSegment found = PlaybackServiceAdSkipper.findSegmentToSkip(
                list(ad), suppressed, 61_000, 1f, MEDIUM_CONFIDENCE);
        assertSame(ad, found);
    }

    @Test
    public void aPositionExactlyAtTheStartSkips() {
        AdSegment ad = detected(1, 60_000, 120_000, 0.9f);
        assertSame(ad, PlaybackServiceAdSkipper.findSegmentToSkip(
                list(ad), suppressed, 60_000, 1f, MEDIUM_CONFIDENCE));
    }

    @Test
    public void theEntryWindowGrowsWithThePlaybackSpeed() {
        AdSegment ad = detected(1, 60_000, 120_000, 0.9f);
        // 4.5 s in: outside the window at 1x, inside it at 2x.
        assertNull(PlaybackServiceAdSkipper.findSegmentToSkip(
                list(ad), new HashSet<>(), 64_500, 1f, MEDIUM_CONFIDENCE));
        assertSame(ad, PlaybackServiceAdSkipper.findSegmentToSkip(
                list(ad), new HashSet<>(), 64_500, 2f, MEDIUM_CONFIDENCE));
    }

    @Test
    public void anUnknownSpeedIsTreatedAsNormalSpeed() {
        AdSegment ad = detected(1, 60_000, 120_000, 0.9f);
        assertSame(ad, PlaybackServiceAdSkipper.findSegmentToSkip(
                list(ad), suppressed, 62_000, 0f, MEDIUM_CONFIDENCE));
    }

    // ---------------------------------------------------------- seeking into a segment

    @Test
    public void seekingIntoTheMiddleOfASegmentSuppressesItInsteadOfSkipping() {
        AdSegment ad = detected(1, 60_000, 120_000, 0.9f);
        assertNull(PlaybackServiceAdSkipper.findSegmentToSkip(
                list(ad), suppressed, 90_000, 1f, MEDIUM_CONFIDENCE));
        assertTrue(suppressed.contains(1L));

        // ... and it stays suppressed even once playback reaches its start window again.
        assertNull(PlaybackServiceAdSkipper.findSegmentToSkip(
                list(ad), suppressed, 60_500, 1f, MEDIUM_CONFIDENCE));
    }

    @Test
    public void anAlreadySuppressedSegmentIsNeverSkipped() {
        AdSegment ad = detected(1, 60_000, 120_000, 0.9f);
        suppressed.add(1L);
        assertNull(PlaybackServiceAdSkipper.findSegmentToSkip(
                list(ad), suppressed, 60_500, 1f, MEDIUM_CONFIDENCE));
    }

    // ------------------------------------------------------------- filters

    @Test
    public void aDisabledSegmentIsIgnoredAndNotEvenSuppressed() {
        AdSegment ad = new AdSegment(1, ITEM_ID, 60_000, 120_000,
                AdSegment.Source.DETECTED, 0.9f, false);
        assertNull(PlaybackServiceAdSkipper.findSegmentToSkip(
                list(ad), suppressed, 60_500, 1f, MEDIUM_CONFIDENCE));
        assertNull(PlaybackServiceAdSkipper.findSegmentToSkip(
                list(ad), suppressed, 90_000, 1f, MEDIUM_CONFIDENCE));
        assertFalse(suppressed.contains(1L));
    }

    @Test
    public void aDetectedSegmentBelowTheThresholdIsIgnored() {
        AdSegment ad = detected(1, 60_000, 120_000, 0.5f);
        assertNull(PlaybackServiceAdSkipper.findSegmentToSkip(
                list(ad), suppressed, 60_500, 1f, MEDIUM_CONFIDENCE));
        assertFalse(suppressed.contains(1L));

        // The same segment skips once the user raises the sensitivity.
        assertSame(ad, PlaybackServiceAdSkipper.findSegmentToSkip(
                list(ad), suppressed, 60_500, 1f, 0.45f));
    }

    @Test
    public void chapterAndManualSegmentsSkipRegardlessOfTheThreshold() {
        AdSegment chapter = segment(1, 60_000, 120_000, AdSegment.Source.CHAPTER, 0f);
        assertSame(chapter, PlaybackServiceAdSkipper.findSegmentToSkip(
                list(chapter), new HashSet<>(), 60_500, 1f, 0.99f));

        AdSegment manual = segment(2, 60_000, 120_000, AdSegment.Source.MANUAL, 0f);
        assertSame(manual, PlaybackServiceAdSkipper.findSegmentToSkip(
                list(manual), new HashSet<>(), 60_500, 1f, 0.99f));
    }

    @Test
    public void aPositionOutsideEverySegmentSkipsNothing() {
        assertNull(PlaybackServiceAdSkipper.findSegmentToSkip(
                list(detected(1, 60_000, 120_000, 0.9f)), suppressed, 10_000, 1f,
                MEDIUM_CONFIDENCE));
        assertNull(PlaybackServiceAdSkipper.findSegmentToSkip(
                Collections.emptyList(), suppressed, 10_000, 1f, MEDIUM_CONFIDENCE));
    }

    @Test
    public void theSegmentContainingThePositionIsPickedOutOfSeveral() {
        AdSegment first = detected(1, 10_000, 20_000, 0.9f);
        AdSegment second = detected(2, 60_000, 120_000, 0.9f);
        assertSame(second, PlaybackServiceAdSkipper.findSegmentToSkip(
                list(first, second), suppressed, 60_500, 1f, MEDIUM_CONFIDENCE));
    }

    // ------------------------------------------------------------- the skip target

    @Test
    public void theTargetLandsJustInsideTheEndOfTheSegment() {
        assertEquals(119_500,
                PlaybackServiceAdSkipper.skipTarget(detected(1, 60_000, 120_000, 0.9f), 3_600_000));
    }

    @Test
    public void aVeryShortSegmentStillJumpsPastItsStart() {
        // end - 500 would be before start + 1000, so the floor wins.
        assertEquals(61_000,
                PlaybackServiceAdSkipper.skipTarget(detected(1, 60_000, 60_800, 0.9f), 3_600_000));
    }

    @Test
    public void theTargetNeverGetsWithinASecondOfTheEndOfTheEpisode() {
        assertEquals(119_000,
                PlaybackServiceAdSkipper.skipTarget(detected(1, 60_000, 120_000, 0.9f), 120_000));
    }

    @Test
    public void anUnknownDurationDoesNotClampTheTarget() {
        assertEquals(119_500,
                PlaybackServiceAdSkipper.skipTarget(detected(1, 60_000, 120_000, 0.9f), 0));
    }

    @Test
    public void aSegmentReachingTheEndOfTheEpisodeIsRecognised() {
        assertTrue(PlaybackServiceAdSkipper.runsToEndOfEpisode(
                detected(1, 60_000, 120_000, 0.9f), 120_000));
        assertTrue(PlaybackServiceAdSkipper.runsToEndOfEpisode(
                detected(1, 60_000, 119_500, 0.9f), 120_000));
        assertFalse(PlaybackServiceAdSkipper.runsToEndOfEpisode(
                detected(1, 60_000, 110_000, 0.9f), 120_000));
        // An unknown duration can never be "the end".
        assertFalse(PlaybackServiceAdSkipper.runsToEndOfEpisode(
                detected(1, 60_000, 120_000, 0.9f), 0));
    }
}
