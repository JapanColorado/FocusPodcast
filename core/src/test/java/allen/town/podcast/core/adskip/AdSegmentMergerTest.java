package allen.town.podcast.core.adskip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import allen.town.podcast.model.feed.AdSegment;

/** Precedence and ordering rules for the three sources of ad segments. */
public class AdSegmentMergerTest {

    private static AdSegment segment(long start, long end, AdSegment.Source source, float conf) {
        return new AdSegment(1L, start, end, source, conf);
    }

    private static void assertSortedAndDisjoint(List<AdSegment> segments) {
        for (int i = 1; i < segments.size(); i++) {
            assertTrue("segments must be sorted and disjoint: " + segments,
                    segments.get(i).getStartMs() >= segments.get(i - 1).getEndMs());
        }
    }

    @Test
    public void chapterBeatsDetectedOnOverlap() {
        List<AdSegment> merged = AdSegmentMerger.merge(
                Collections.singletonList(segment(50_000, 70_000, AdSegment.Source.DETECTED, 0.8f)),
                Collections.singletonList(segment(60_000, 90_000, AdSegment.Source.CHAPTER, 1f)),
                null);
        assertSortedAndDisjoint(merged);
        assertEquals(2, merged.size());
        assertEquals(50_000, merged.get(0).getStartMs());
        assertEquals(60_000, merged.get(0).getEndMs());
        assertEquals(AdSegment.Source.DETECTED, merged.get(0).getSource());
        assertEquals(60_000, merged.get(1).getStartMs());
        assertEquals(90_000, merged.get(1).getEndMs());
        assertEquals(AdSegment.Source.CHAPTER, merged.get(1).getSource());
    }

    @Test
    public void fullyCoveredDetectedSegmentDisappears() {
        List<AdSegment> merged = AdSegmentMerger.merge(
                Collections.singletonList(segment(60_000, 80_000, AdSegment.Source.DETECTED, 0.9f)),
                Collections.singletonList(segment(50_000, 90_000, AdSegment.Source.CHAPTER, 1f)),
                null);
        assertEquals(1, merged.size());
        assertEquals(AdSegment.Source.CHAPTER, merged.get(0).getSource());
    }

    @Test
    public void manualBeatsChapter() {
        List<AdSegment> merged = AdSegmentMerger.merge(
                null,
                Collections.singletonList(segment(0, 60_000, AdSegment.Source.CHAPTER, 1f)),
                Collections.singletonList(segment(30_000, 90_000, AdSegment.Source.MANUAL, 1f)));
        assertSortedAndDisjoint(merged);
        assertEquals(2, merged.size());
        assertEquals(AdSegment.Source.CHAPTER, merged.get(0).getSource());
        assertEquals(0, merged.get(0).getStartMs());
        assertEquals(30_000, merged.get(0).getEndMs());
        assertEquals(AdSegment.Source.MANUAL, merged.get(1).getSource());
    }

    @Test
    public void aHigherRankedSegmentInTheMiddleSplitsALowerRankedOne() {
        List<AdSegment> merged = AdSegmentMerger.merge(
                Collections.singletonList(segment(0, 100_000, AdSegment.Source.DETECTED, 0.7f)),
                Collections.singletonList(segment(40_000, 60_000, AdSegment.Source.CHAPTER, 1f)),
                null);
        assertSortedAndDisjoint(merged);
        assertEquals(3, merged.size());
        assertEquals(0, merged.get(0).getStartMs());
        assertEquals(40_000, merged.get(0).getEndMs());
        assertEquals(40_000, merged.get(1).getStartMs());
        assertEquals(60_000, merged.get(1).getEndMs());
        assertEquals(AdSegment.Source.CHAPTER, merged.get(1).getSource());
        assertEquals(60_000, merged.get(2).getStartMs());
        assertEquals(100_000, merged.get(2).getEndMs());
        assertEquals(0.7f, merged.get(2).getConfidence(), 1e-6);
    }

    @Test
    public void slicesShorterThanTheRemnantThresholdAreDropped() {
        List<AdSegment> merged = AdSegmentMerger.merge(
                Collections.singletonList(segment(59_500, 90_000, AdSegment.Source.DETECTED, 0.7f)),
                Collections.singletonList(segment(60_000, 90_000, AdSegment.Source.CHAPTER, 1f)),
                null);
        assertEquals(1, merged.size());
        assertEquals(AdSegment.Source.CHAPTER, merged.get(0).getSource());
    }

    @Test
    public void overlappingSegmentsOfTheSameSourceAreUnioned() {
        List<AdSegment> merged = AdSegmentMerger.merge(Arrays.asList(
                segment(0, 30_000, AdSegment.Source.DETECTED, 0.6f),
                segment(20_000, 50_000, AdSegment.Source.DETECTED, 0.9f)));
        assertEquals(1, merged.size());
        assertEquals(0, merged.get(0).getStartMs());
        assertEquals(50_000, merged.get(0).getEndMs());
        assertEquals("the union keeps the strongest confidence",
                0.9f, merged.get(0).getConfidence(), 1e-6);
    }

    @Test
    public void outputIsSortedByStartTime() {
        List<AdSegment> merged = AdSegmentMerger.merge(Arrays.asList(
                segment(500_000, 520_000, AdSegment.Source.DETECTED, 0.5f),
                segment(10_000, 20_000, AdSegment.Source.MANUAL, 1f),
                segment(100_000, 120_000, AdSegment.Source.CHAPTER, 1f)));
        assertSortedAndDisjoint(merged);
        assertEquals(3, merged.size());
        assertEquals(10_000, merged.get(0).getStartMs());
        assertEquals(100_000, merged.get(1).getStartMs());
        assertEquals(500_000, merged.get(2).getStartMs());
    }

    @Test
    public void emptyAndNullInputsAreSafe() {
        assertTrue(AdSegmentMerger.merge(null).isEmpty());
        assertTrue(AdSegmentMerger.merge(Collections.<AdSegment>emptyList()).isEmpty());
        assertTrue(AdSegmentMerger.merge(null, null, null).isEmpty());
    }
}
