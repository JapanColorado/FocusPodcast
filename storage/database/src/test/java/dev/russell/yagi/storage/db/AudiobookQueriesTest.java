package dev.russell.yagi.storage.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import dev.russell.yagi.model.feed.Feed;
import dev.russell.yagi.model.feed.FeedFilter;
import dev.russell.yagi.model.feed.FeedItem;
import dev.russell.yagi.model.feed.FeedItemFilter;
import dev.russell.yagi.model.feed.FeedPreferences;
import dev.russell.yagi.model.feed.VolumeAdaptionSetting;
import dev.russell.yagi.storage.db.mapper.FeedItemCursorMapper;

/**
 * Audiobook feeds stay out of the podcast-wide episode lists (All Episodes and its count, the new
 * items and their count) and the all-audiobook-items query returns only them. Also covers the
 * total count with an empty filter, which used to count unsubscribed feeds too.
 */
@RunWith(RobolectricTestRunner.class)
public class AudiobookQueriesTest {

    private Db db;
    private Feed podcast;
    private Feed audiobook;
    private Feed unsubscribed;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        Db.init(context);
        db = Db.getInstance();
        podcast = feed("https://example.com/podcast.xml", "Podcast", true, false, FeedItem.NEW, FeedItem.PLAYED);
        audiobook = feed("FocusPodcastLocal:content://book", "Book", true, true,
                FeedItem.NEW, FeedItem.UNPLAYED, FeedItem.PLAYED);
        unsubscribed = feed("https://example.com/preview.xml", "Preview", false, false, FeedItem.UNPLAYED);
        db.setCompleteFeed(podcast, audiobook, unsubscribed);
    }

    @After
    public void tearDown() {
        Db.tearDownTests();
        ApplicationProvider.getApplicationContext().deleteDatabase(Db.DATABASE_NAME);
    }

    @Test
    public void allEpisodesListsOnlySubscribedPodcasts() {
        List<Long> feedIds = feedIdsOf(db.getRecentlyPublishedItemsCursor(0, 100, FeedItemFilter.unfiltered()));
        assertEquals(2, feedIds.size());
        for (long id : feedIds) {
            assertEquals(podcast.getId(), id);
        }
    }

    @Test
    public void totalCountWithoutFilterMatchesTheList() {
        try (Cursor cursor = db.getTotalEpisodeCountCursor(FeedItemFilter.unfiltered())) {
            assertTrue(cursor.moveToFirst());
            assertEquals(2, cursor.getInt(0));
        }
    }

    @Test
    public void totalCountWithFilterExcludesAudiobooks() {
        try (Cursor cursor = db.getTotalEpisodeCountCursor(new FeedItemFilter(FeedItemFilter.UNPLAYED))) {
            assertTrue(cursor.moveToFirst());
            assertEquals(1, cursor.getInt(0));
        }
    }

    @Test
    public void newItemsExcludeAudiobooks() {
        List<Long> feedIds = feedIdsOf(db.getNewItemsCursor(0, 100));
        assertEquals(1, feedIds.size());
        assertEquals(podcast.getId(), (long) feedIds.get(0));
        assertEquals(1, db.getNumberOfNewItems());
    }

    @Test
    public void audiobookItemsQueryReturnsEveryTrackOfSubscribedBooksOnly() {
        List<Long> feedIds = feedIdsOf(db.getAudiobookItemsCursor());
        assertEquals(3, feedIds.size());
        for (long id : feedIds) {
            assertEquals(audiobook.getId(), id);
        }
    }

    private static List<Long> feedIdsOf(Cursor cursor) {
        List<Long> ids = new ArrayList<>();
        try (Cursor c = cursor) {
            while (c.moveToNext()) {
                ids.add(FeedItemCursorMapper.convert(c).getFeedId());
            }
        }
        return ids;
    }

    private static Feed feed(String url, String title, boolean subscribed, boolean isAudiobook, int... states) {
        Feed feed = new Feed(url, null, title);
        feed.setSubscribed(subscribed);
        FeedPreferences preferences = new FeedPreferences(0, false, true,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, null, null,
                new FeedFilter(), FeedPreferences.SPEED_USE_GLOBAL, 0, 0, false, new HashSet<>(),
                false, 0, false, false);
        preferences.setAudiobook(isAudiobook);
        feed.setPreferences(preferences);
        ArrayList<FeedItem> items = new ArrayList<>();
        for (int i = 0; i < states.length; i++) {
            items.add(new FeedItem(0, title + i, "guid-" + title + i, url + "#" + i,
                    new Date(1_600_000_000_000L + i), states[i], feed));
        }
        feed.setItems(items);
        return feed;
    }
}
