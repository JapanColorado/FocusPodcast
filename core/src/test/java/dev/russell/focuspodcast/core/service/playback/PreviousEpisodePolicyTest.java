package dev.russell.focuspodcast.core.service.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import dev.russell.focuspodcast.core.service.playback.PreviousEpisodePolicy.Action;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedMedia;

/** Plain JUnit tests for what the "previous" button does. */
public class PreviousEpisodePolicyTest {

    private static final long THRESHOLD = PreviousEpisodePolicy.RESTART_THRESHOLD_MS;

    private static FeedItem itemWithMedia(long mediaId) {
        FeedItem item = new FeedItem();
        item.setMedia(new FeedMedia(mediaId, item, 60_000, 0, 0, "audio/mpeg", null,
                "https://example.com/" + mediaId + ".mp3", false, null, 0, 0));
        return item;
    }

    @Test
    public void restartsWhenPastThreshold() {
        assertEquals(Action.RESTART, PreviousEpisodePolicy.decide(THRESHOLD + 1, true));
        assertEquals(Action.RESTART, PreviousEpisodePolicy.decide(60_000, false));
        assertTrue(PreviousEpisodePolicy.isPastRestartThreshold(THRESHOLD + 1));
    }

    @Test
    public void playsPreviousWithinThreshold() {
        assertEquals(Action.PLAY_PREVIOUS, PreviousEpisodePolicy.decide(0, true));
        assertEquals(Action.PLAY_PREVIOUS, PreviousEpisodePolicy.decide(THRESHOLD, true));
        assertFalse(PreviousEpisodePolicy.isPastRestartThreshold(THRESHOLD));
    }

    @Test
    public void restartsWithinThresholdWhenNoPreviousEpisode() {
        assertEquals(Action.RESTART, PreviousEpisodePolicy.decide(1000, false));
    }

    @Test
    public void picksNewestEntryThatIsNotPlaying() {
        FeedItem newest = itemWithMedia(1);
        FeedItem older = itemWithMedia(2);
        assertSame(newest.getMedia(),
                PreviousEpisodePolicy.pickPrevious(Arrays.asList(newest, older), 3));
    }

    @Test
    public void skipsTheEpisodePlayingNow() {
        FeedItem current = itemWithMedia(1);
        FeedItem older = itemWithMedia(2);
        assertSame(older.getMedia(),
                PreviousEpisodePolicy.pickPrevious(Arrays.asList(current, older), 1));
    }

    @Test
    public void skipsEntriesWithoutMedia() {
        FeedItem noMedia = new FeedItem();
        FeedItem older = itemWithMedia(2);
        assertSame(older.getMedia(),
                PreviousEpisodePolicy.pickPrevious(Arrays.asList(noMedia, older), 1));
    }

    @Test
    public void nothingWhenHistoryHoldsOnlyTheCurrentEpisode() {
        assertNull(PreviousEpisodePolicy.pickPrevious(
                Collections.singletonList(itemWithMedia(1)), 1));
        assertNull(PreviousEpisodePolicy.pickPrevious(Collections.emptyList(), 1));
    }
}
