package dev.russell.focuspodcast.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VideoStreamCheckTest {

    @Test
    public void requiredBandwidthFollowsSizeDurationAndSpeed() {
        // The Marginal Revolution "Tariffs" episode: 24.45 GB over 53:48.
        long oneX = VideoStreamCheck.requiredKbps(24_453_096_355L, 3_227_669, 1f);
        assertEquals(60_608, oneX, 100);
        assertEquals(2 * oneX, VideoStreamCheck.requiredKbps(24_453_096_355L, 3_227_669, 2f), 2);
        // slower than 1x still has to arrive at the file's own rate to start without waiting
        assertEquals(oneX, VideoStreamCheck.requiredKbps(24_453_096_355L, 3_227_669, 0.5f));
        assertEquals(0, VideoStreamCheck.requiredKbps(0, 3_227_669, 1f));
        assertEquals(0, VideoStreamCheck.requiredKbps(1000, 0, 1f));
    }

    @Test
    public void tooSlowOnlyWhenBothAreKnown() {
        assertTrue(VideoStreamCheck.isLikelyTooSlow(120_000, 9_352));
        assertFalse(VideoStreamCheck.isLikelyTooSlow(2_000, 9_352));
        assertFalse(VideoStreamCheck.isLikelyTooSlow(0, 9_352));
        assertFalse(VideoStreamCheck.isLikelyTooSlow(120_000, 0));
    }
}
