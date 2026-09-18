package allen.town.podcast.core.adskip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import allen.town.podcast.model.feed.AdSegment;

/**
 * End-to-end check of the detector against a synthetic episode built by {@link SyntheticEpisode}:
 * twenty minutes of host speech with a one minute ad at 0:30 and another at 10:00.
 *
 * <p>The episode is rendered once for the whole class because twenty minutes of audio costs a few
 * seconds of FFT.
 */
public class AdDetectorTest {

    private static final long FEED_ITEM_ID = 4242L;
    private static final long EPISODE_MS = 20 * 60 * 1000L;
    private static final long[][] ADS = {{30_000L, 90_000L}, {600_000L, 660_000L}};

    private static List<FeatureFrame> adEpisode;
    private static List<FeatureFrame> speechOnlyEpisode;

    @BeforeClass
    public static void renderEpisodes() {
        adEpisode = SyntheticEpisode.render(EPISODE_MS, ADS, 20260917L);
        speechOnlyEpisode = SyntheticEpisode.render(10 * 60 * 1000L, new long[0][], 991L);
    }

    private static double intersectionOverUnion(long aStart, long aEnd, long bStart, long bEnd) {
        long intersection = Math.max(0, Math.min(aEnd, bEnd) - Math.max(aStart, bStart));
        long union = Math.max(aEnd, bEnd) - Math.min(aStart, bStart);
        return union <= 0 ? 0 : (double) intersection / union;
    }

    private static boolean overlaps(AdSegment segment, long[] range) {
        return segment.getEndMs() > range[0] && segment.getStartMs() < range[1];
    }

    private static String describe(List<AdSegment> segments) {
        StringBuilder sb = new StringBuilder();
        for (AdSegment s : segments) {
            sb.append(String.format(Locale.US, "%n  %.1f-%.1fs conf=%.2f",
                    s.getStartMs() / 1000.0, s.getEndMs() / 1000.0, s.getConfidence()));
        }
        return sb.toString();
    }

    @Test
    public void findsBothInsertedAds() {
        List<AdSegment> found = new AdDetector().detect(adEpisode, EPISODE_MS, FEED_ITEM_ID);
        String report = describe(found);

        for (long[] ad : ADS) {
            List<AdSegment> hits = new ArrayList<>();
            for (AdSegment segment : found) {
                if (overlaps(segment, ad)) {
                    hits.add(segment);
                }
            }
            assertTrue("no segment overlaps the ad at " + ad[0] + "ms:" + report, !hits.isEmpty());

            long start = Long.MAX_VALUE;
            long end = Long.MIN_VALUE;
            float bestConfidence = 0;
            for (AdSegment segment : hits) {
                start = Math.min(start, segment.getStartMs());
                end = Math.max(end, segment.getEndMs());
                bestConfidence = Math.max(bestConfidence, segment.getConfidence());
            }
            double iou = intersectionOverUnion(start, end, ad[0], ad[1]);
            assertTrue(String.format(Locale.US,
                            "ad at %.0fs: IoU was %.2f, expected >= 0.70%s",
                            ad[0] / 1000.0, iou, report),
                    iou >= 0.70);
            assertTrue(String.format(Locale.US,
                            "ad at %.0fs: confidence was %.2f, expected >= 0.50%s",
                            ad[0] / 1000.0, bestConfidence, report),
                    bestConfidence >= 0.50f);
        }
    }

    @Test
    public void doesNotFlagLongStretchesOfHostSpeech() {
        List<AdSegment> found = new AdDetector().detect(adEpisode, EPISODE_MS, FEED_ITEM_ID);
        String report = describe(found);
        long falsePositiveMs = 0;
        for (AdSegment segment : found) {
            if (overlaps(segment, ADS[0]) || overlaps(segment, ADS[1])) {
                continue;
            }
            assertTrue(String.format(Locale.US,
                            "false positive of %.1fs at %.1fs, expected under 20s%s",
                            segment.getDurationMs() / 1000.0, segment.getStartMs() / 1000.0,
                            report),
                    segment.getDurationMs() <= 20_000);
            falsePositiveMs += segment.getDurationMs();
        }
        assertTrue("too much false positive time: " + falsePositiveMs + "ms" + report,
                falsePositiveMs <= 40_000);
    }

    @Test
    public void everySegmentIsSortedInsideTheEpisodeAndAboveTheFloor() {
        AdDetector detector = new AdDetector();
        List<AdSegment> found = detector.detect(adEpisode, EPISODE_MS, FEED_ITEM_ID);
        long previousEnd = 0;
        long total = 0;
        for (AdSegment segment : found) {
            assertEquals(AdSegment.Source.DETECTED, segment.getSource());
            assertEquals(FEED_ITEM_ID, segment.getFeedItemId());
            assertTrue("segments must be sorted", segment.getStartMs() >= previousEnd);
            assertTrue("segments must stay inside the episode", segment.getEndMs() <= EPISODE_MS);
            assertTrue("confidence floor", segment.getConfidence()
                    >= detector.config().minConfidence);
            previousEnd = segment.getEndMs();
            total += segment.getDurationMs();
        }
        assertTrue("flagged time must respect the cap",
                total <= detector.config().maxFlaggedFraction * EPISODE_MS);
    }

    @Test
    public void speechOnlyEpisodeProducesNoConfidentSegment() {
        List<AdSegment> found =
                new AdDetector().detect(speechOnlyEpisode, 10 * 60 * 1000L, FEED_ITEM_ID);
        for (AdSegment segment : found) {
            assertTrue("speech-only episode should not yield a confident ad: " + segment
                            + describe(found),
                    segment.getConfidence() < 0.6f);
        }
    }

    @Test
    public void findsAShortPreRollAndAnOffAnchorMidRoll() {
        // A harder layout than the main fixture: a 25 s pre-roll, which sits low on the duration
        // prior's ramp, and a 45 s mid-roll deliberately placed away from the 1/3 and 2/3 marks so
        // the position prior cannot help.
        long durationMs = 30 * 60 * 1000L;
        long[][] ads = {{15_000L, 40_000L}, {742_000L, 787_000L}};
        List<FeatureFrame> frames = SyntheticEpisode.render(durationMs, ads, 31337L);
        List<AdSegment> found = new AdDetector().detect(frames, durationMs, FEED_ITEM_ID);
        String report = describe(found);
        for (long[] ad : ads) {
            boolean matched = false;
            for (AdSegment segment : found) {
                if (overlaps(segment, ad)
                        && intersectionOverUnion(segment.getStartMs(), segment.getEndMs(),
                                ad[0], ad[1]) >= 0.70
                        && segment.getConfidence() >= 0.50f) {
                    matched = true;
                }
            }
            assertTrue("missed the ad at " + ad[0] + "ms" + report, matched);
        }
    }

    @Test
    public void detectionIsDeterministic() {
        List<AdSegment> first = new AdDetector().detect(adEpisode, EPISODE_MS, FEED_ITEM_ID);
        List<AdSegment> second = new AdDetector().detect(adEpisode, EPISODE_MS, FEED_ITEM_ID);
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getStartMs(), second.get(i).getStartMs());
            assertEquals(first.get(i).getEndMs(), second.get(i).getEndMs());
            assertEquals(first.get(i).getConfidence(), second.get(i).getConfidence(), 0f);
        }
    }

    @Test
    public void tooShortAProfileReturnsNothing() {
        assertTrue(new AdDetector()
                .detect(Collections.<FeatureFrame>emptyList(), EPISODE_MS, 1L).isEmpty());
        assertTrue(new AdDetector().detect(adEpisode.subList(0, 4), 2000L, 1L).isEmpty());
    }
}
