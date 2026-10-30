package dev.russell.yagi.core.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.preference.PreferenceManager;
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
import java.util.Map;
import java.util.concurrent.Future;

import dev.russell.yagi.core.pref.PlaybackPreferences;
import dev.russell.yagi.core.pref.Prefs;
import dev.russell.yagi.core.pref.UsageStatistics;
import dev.russell.yagi.core.sync.SynchronizationCredentials;
import dev.russell.yagi.core.sync.SynchronizationSettings;
import dev.russell.yagi.model.feed.AudiobookProgress;
import dev.russell.yagi.model.feed.Feed;
import dev.russell.yagi.model.feed.FeedFilter;
import dev.russell.yagi.model.feed.FeedItem;
import dev.russell.yagi.model.feed.FeedMedia;
import dev.russell.yagi.model.feed.FeedPreferences;
import dev.russell.yagi.model.feed.SortOrder;
import dev.russell.yagi.model.feed.VolumeAdaptionSetting;
import dev.russell.yagi.storage.db.Db;

/**
 * The audiobook data the UI builds on: the podcast/audiobook split of the subscription lists, the
 * progress of each book, and the flag toggle with its side effects.
 */
@RunWith(RobolectricTestRunner.class)
public class AudiobookStorageTest {

    private Application context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        Prefs.resetForTests();
        Prefs.init(context);
        PlaybackPreferences.init(context);
        UsageStatistics.init(context);
        SynchronizationCredentials.init(context);
        SynchronizationSettings.init(context);
        Db.init(context);
    }

    @After
    public void tearDown() {
        DBWriter.tearDownTests();
        Db.tearDownTests();
        context.deleteDatabase(Db.DATABASE_NAME);
        Prefs.resetForTests();
    }

    @Test
    public void navDrawerDataSplitsPodcastsFromAudiobooks() throws Exception {
        Feed podcast = store(feed("Podcast", "https://example.com/p.xml", false));
        Feed book = store(feed("Book", Feed.PREFIX_LOCAL_FOLDER + "content://book", true));

        assertEquals(2, feedIds(DBReader.getNavDrawerData(true)).size());
        assertEquals(2, feedIds(DBReader.getNavDrawerData(true, FeedKind.ALL)).size());
        List<Long> podcasts = feedIds(DBReader.getNavDrawerData(true, FeedKind.PODCASTS));
        assertEquals(1, podcasts.size());
        assertEquals(podcast.getId(), (long) podcasts.get(0));
        List<Long> books = feedIds(DBReader.getNavDrawerData(true, FeedKind.AUDIOBOOKS));
        assertEquals(1, books.size());
        assertEquals(book.getId(), (long) books.get(0));

        List<Feed> audiobookFeeds = DBReader.getAudiobookFeeds();
        assertEquals(1, audiobookFeeds.size());
        assertEquals(book.getId(), audiobookFeeds.get(0).getId());
    }

    @Test
    public void progressIsComputedPerBook() throws Exception {
        Feed book = feed("Book", Feed.PREFIX_LOCAL_FOLDER + "content://book", true);
        List<FeedItem> items = new ArrayList<>();
        items.add(track(book, "Ch 1.mp3", 1, FeedItem.PLAYED, 60_000, 0));
        items.add(track(book, "Ch 2.mp3", 2, FeedItem.UNPLAYED, 60_000, 30_000));
        items.add(track(book, "Ch 10.mp3", 10, FeedItem.UNPLAYED, 60_000, 0));
        book.setItems(items);
        store(book);
        store(feed("Podcast", "https://example.com/p.xml", false));

        Map<Long, AudiobookProgress> progress = DBReader.getAudiobookProgress();
        assertEquals(1, progress.size());
        AudiobookProgress bookProgress = progress.get(book.getId());
        assertNotNull(bookProgress);
        assertEquals(3, bookProgress.getTrackCount());
        assertEquals(180_000, bookProgress.getTotalDuration());
        assertEquals(90_000, bookProgress.getListenedDuration());
        assertEquals(50, bookProgress.getPercent());
        assertNotNull(bookProgress.getResumeItem());
        assertEquals("Ch 2.mp3", bookProgress.getResumeItem().getLink());
        assertNotNull(bookProgress.getResumeItem().getMedia());
        assertEquals(book.getId(), bookProgress.getResumeItem().getFeed().getId());
    }

    @Test
    public void turningTheFlagOnSetsTrackOrderAndClearsNewItems() throws Exception {
        Feed feed = feed("Soon a book", "https://example.com/b.xml", false);
        List<FeedItem> items = new ArrayList<>();
        items.add(track(feed, "a", 0, FeedItem.NEW, 1000, 0));
        items.add(track(feed, "b", 0, FeedItem.PLAYED, 1000, 0));
        feed.setItems(items);
        store(feed);

        await(DBWriter.setFeedAudiobook(feed.getId(), true));

        Feed reloaded = DBReader.getFeed(feed.getId());
        assertNotNull(reloaded);
        assertTrue(reloaded.isAudiobook());
        assertEquals(SortOrder.TRACK_ORDER, reloaded.getSortOrder());
        for (FeedItem item : DBReader.getFeedItemList(reloaded)) {
            assertFalse(item.isNew());
        }

        await(DBWriter.setFeedAudiobook(feed.getId(), false));
        reloaded = DBReader.getFeed(feed.getId());
        assertNotNull(reloaded);
        assertFalse(reloaded.isAudiobook());
        assertEquals("turning it off keeps the order", SortOrder.TRACK_ORDER, reloaded.getSortOrder());
    }

    @Test
    public void turningTheFlagOnKeepsAChosenSortOrder() throws Exception {
        Feed feed = feed("Sorted", "https://example.com/s.xml", false);
        feed.setSortOrder(SortOrder.DATE_OLD_NEW);
        store(feed);

        await(DBWriter.setFeedAudiobook(feed.getId(), true));

        Feed reloaded = DBReader.getFeed(feed.getId());
        assertNotNull(reloaded);
        assertEquals(SortOrder.DATE_OLD_NEW, reloaded.getSortOrder());
    }

    @Test
    public void newTracksOfAnAudiobookAreNeverNew() throws Exception {
        Feed book = store(feed("Book", "https://example.com/book.xml", true));
        Feed update = new Feed(book.getDownload_url(), null, "Book");
        update.setId(book.getId());
        List<FeedItem> items = new ArrayList<>();
        items.add(track(update, "later", 0, FeedItem.UNPLAYED, 1000, 0));
        items.get(0).setPubDate(new Date(System.currentTimeMillis()));
        items.get(0).setNew();
        update.setItems(items);

        DBTasks.updateFeed(context, update, false);

        List<FeedItem> stored = DBReader.getFeedItemList(DBReader.getFeed(book.getId()));
        assertEquals(1, stored.size());
        assertFalse(stored.get(0).isNew());
    }

    private static List<Long> feedIds(NavDrawerData data) {
        List<Long> ids = new ArrayList<>();
        for (NavDrawerData.DrawerItem item : data.items) {
            if (item.type == NavDrawerData.DrawerItem.Type.FEED) {
                ids.add(((NavDrawerData.FeedDrawerItem) item).feed.getId());
            }
        }
        return ids;
    }

    private Feed store(Feed feed) throws Exception {
        await(DBWriter.addNewFeed(context, feed));
        return feed;
    }

    private static void await(Future<?> future) throws Exception {
        future.get();
    }

    private static Feed feed(String title, String url, boolean audiobook) {
        Feed feed = new Feed(url, null, title);
        feed.setSubscribed(true);
        feed.setItems(new ArrayList<>());
        FeedPreferences preferences = new FeedPreferences(0, false, true,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, null, null,
                new FeedFilter(), FeedPreferences.SPEED_USE_GLOBAL, 0, 0, false, new HashSet<>(),
                false, 0, false, false);
        preferences.setAudiobook(audiobook);
        feed.setPreferences(preferences);
        return feed;
    }

    private static FeedItem track(Feed feed, String path, int trackNumber, int state, int duration, int position) {
        FeedItem item = new FeedItem(0, path, "guid-" + feed.getTitle() + path, path,
                new Date(1_600_000_000_000L), state, feed);
        item.setTrackNumber(trackNumber);
        FeedMedia media = new FeedMedia(0, item, duration, position, 0, "audio/mpeg",
                "content://" + path, "content://" + path, false, null, 0, position > 0 ? 1000 : 0);
        item.setMedia(media);
        return item;
    }
}
