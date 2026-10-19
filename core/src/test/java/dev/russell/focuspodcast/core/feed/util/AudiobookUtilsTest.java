package dev.russell.focuspodcast.core.feed.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import dev.russell.focuspodcast.model.feed.AudiobookProgress;
import dev.russell.focuspodcast.model.feed.Feed;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedMedia;
import dev.russell.focuspodcast.model.feed.SortOrder;

/** Next-track selection and progress of audiobooks. Plain JUnit. */
public class AudiobookUtilsTest {

    private final Feed book = new Feed(Feed.PREFIX_LOCAL_FOLDER + "content://tree", null, "Book");

    private FeedItem track(long id, String path, int state, int duration, int position, long lastPlayed) {
        FeedItem item = new FeedItem(id, path, "guid" + id, path, new Date(id), state, book);
        FeedMedia media = new FeedMedia(id, item, duration, position, 0, "audio/mpeg",
                "content://" + id, "content://" + id, false, null, 0, lastPlayed);
        item.setMedia(media);
        return item;
    }

    @Test
    public void nextTrackIsTheFollowingOneInBookOrderWhateverItsState() {
        FeedItem ch1 = track(1, "Ch 1.mp3", FeedItem.PLAYED, 1000, 0, 0);
        FeedItem ch2 = track(2, "Ch 2.mp3", FeedItem.PLAYED, 1000, 0, 0);
        FeedItem ch10 = track(3, "Ch 10.mp3", FeedItem.UNPLAYED, 1000, 0, 0);
        List<FeedItem> shuffled = Arrays.asList(ch10, ch1, ch2);

        assertSame(ch2, AudiobookUtils.nextTrack(shuffled, SortOrder.TRACK_ORDER, ch1.getId()));
        assertSame(ch10, AudiobookUtils.nextTrack(shuffled, SortOrder.TRACK_ORDER, ch2.getId()));
    }

    @Test
    public void endOfTheBookHasNoNextTrack() {
        FeedItem ch1 = track(1, "Ch 1.mp3", FeedItem.UNPLAYED, 1000, 0, 0);
        FeedItem ch2 = track(2, "Ch 2.mp3", FeedItem.UNPLAYED, 1000, 0, 0);
        assertNull(AudiobookUtils.nextTrack(Arrays.asList(ch2, ch1), SortOrder.TRACK_ORDER, ch2.getId()));
    }

    @Test
    public void unknownCurrentItemHasNoNextTrack() {
        FeedItem ch1 = track(1, "Ch 1.mp3", FeedItem.UNPLAYED, 1000, 0, 0);
        assertNull(AudiobookUtils.nextTrack(Collections.singletonList(ch1), SortOrder.TRACK_ORDER, 99));
    }

    @Test
    public void nextTrackFollowsTheChosenOrder() {
        FeedItem ch1 = track(1, "Ch 1.mp3", FeedItem.UNPLAYED, 1000, 0, 0);
        FeedItem ch2 = track(2, "Ch 2.mp3", FeedItem.UNPLAYED, 1000, 0, 0);
        assertSame(ch1, AudiobookUtils.nextTrack(Arrays.asList(ch1, ch2), SortOrder.TRACK_ORDER_REVERSED,
                ch2.getId()));
    }

    @Test
    public void playbackOrderDefaultsToTrackOrder() {
        assertEquals(SortOrder.TRACK_ORDER, AudiobookUtils.playbackOrder(null));
        Feed feed = new Feed("https://example.com/a.xml", null, "A");
        assertEquals(SortOrder.TRACK_ORDER, AudiobookUtils.playbackOrder(feed));
        feed.setSortOrder(SortOrder.DATE_OLD_NEW);
        assertEquals(SortOrder.DATE_OLD_NEW, AudiobookUtils.playbackOrder(feed));
    }

    @Test
    public void progressCountsPlayedTracksFullyAndOthersByPosition() {
        List<FeedItem> items = Arrays.asList(
                track(1, "Ch 1.mp3", FeedItem.PLAYED, 1000, 0, 0),
                track(2, "Ch 2.mp3", FeedItem.UNPLAYED, 2000, 500, 10),
                track(3, "Ch 3.mp3", FeedItem.UNPLAYED, 1000, 0, 0));
        AudiobookProgress progress = AudiobookUtils.computeProgress(7, items);

        assertEquals(7, progress.getFeedId());
        assertEquals(3, progress.getTrackCount());
        assertEquals(1, progress.getPlayedTrackCount());
        assertEquals(4000, progress.getTotalDuration());
        assertEquals(1500, progress.getListenedDuration());
        assertEquals(2500, progress.getRemainingDuration());
        assertEquals(37, progress.getPercent());
        assertSame(items.get(1), progress.getResumeItem());
        assertFalse(progress.isFinished());
    }

    @Test
    public void resumeIsTheMostRecentlyPlayedTrackInProgress() {
        List<FeedItem> items = Arrays.asList(
                track(1, "Ch 1.mp3", FeedItem.UNPLAYED, 1000, 100, 50),
                track(2, "Ch 2.mp3", FeedItem.UNPLAYED, 1000, 200, 90),
                track(3, "Ch 3.mp3", FeedItem.UNPLAYED, 1000, 0, 0));
        assertSame(items.get(1), AudiobookUtils.computeProgress(1, items).getResumeItem());
    }

    @Test
    public void resumeIsTheFirstUnplayedTrackWhenNothingIsInProgress() {
        List<FeedItem> items = Arrays.asList(
                track(3, "Ch 10.mp3", FeedItem.UNPLAYED, 1000, 0, 0),
                track(1, "Ch 1.mp3", FeedItem.PLAYED, 1000, 0, 0),
                track(2, "Ch 2.mp3", FeedItem.UNPLAYED, 1000, 0, 0));
        assertSame(items.get(2), AudiobookUtils.computeProgress(1, items).getResumeItem());
    }

    @Test
    public void finishedBookHasNoResumeTrack() {
        List<FeedItem> items = Arrays.asList(
                track(1, "Ch 1.mp3", FeedItem.PLAYED, 1000, 0, 0),
                track(2, "Ch 2.mp3", FeedItem.PLAYED, 1000, 0, 0));
        AudiobookProgress progress = AudiobookUtils.computeProgress(1, items);
        assertNull(progress.getResumeItem());
        assertTrue(progress.isFinished());
        assertEquals(100, progress.getPercent());
    }

    @Test
    public void unknownDurationsGiveZeroPercent() {
        AudiobookProgress progress = AudiobookUtils.computeProgress(1,
                Collections.singletonList(track(1, "a.mp3", FeedItem.UNPLAYED, 0, 300, 1)));
        assertEquals(0, progress.getPercent());
        assertEquals(0, progress.getListenedDuration());
    }

    @Test
    public void lastPlayedIsTheLatestOfAnyTrack() {
        AudiobookProgress progress = AudiobookUtils.computeProgress(1, Arrays.asList(
                track(1, "Ch 1.mp3", FeedItem.PLAYED, 1000, 0, 500),
                track(2, "Ch 2.mp3", FeedItem.UNPLAYED, 1000, 100, 300),
                track(3, "Ch 3.mp3", FeedItem.UNPLAYED, 1000, 0, 0)));
        assertEquals(500, progress.getLastPlayedTime());

        AudiobookProgress untouched = AudiobookUtils.computeProgress(1, Arrays.asList(
                track(1, "Ch 1.mp3", FeedItem.UNPLAYED, 1000, 0, 0),
                track(2, "Ch 2.mp3", FeedItem.NEW, 1000, 0, 0)));
        assertEquals(0, untouched.getLastPlayedTime());
    }
}
