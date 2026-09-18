package allen.town.podcast.core.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import allen.town.podcast.core.pref.PlaybackPreferences;
import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.pref.UsageStatistics;
import allen.town.podcast.core.sync.SynchronizationCredentials;
import allen.town.podcast.core.sync.SynchronizationSettings;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedFilter;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.model.feed.FeedPreferences;
import allen.town.podcast.model.feed.VolumeAdaptionSetting;
import allen.town.podcast.storage.db.Db;

/**
 * End-to-end round trips through {@link DBWriter} and {@link DBReader} against a real (Robolectric)
 * SQLite database. Every write goes through the returned {@link Future} so the single-threaded
 * database executor has finished before anything is asserted.
 *
 * <p>Core is initialised piece by piece rather than through
 * {@code ClientConfig.initialize}: that flag is process-wide and irreversible, and
 * {@code ClientConfigTest} needs to observe the uninitialised state.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class DBReaderWriterTest {

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

    // ------------------------------------------------------- feeds

    @Test
    public void addNewFeedMakesItVisibleOnlyAfterSubscribing() throws Exception {
        Feed feed = feed("A podcast", "https://a.example/feed.xml");
        await(DBWriter.addNewFeed(context, feed));

        assertEquals(1, DBReader.getAllFeedList().size());
        assertTrue("an unsubscribed feed must not show up in the feed list",
                DBReader.getFeedList().isEmpty());
        assertEquals(0, DBReader.getSubscribedFeedsCount());

        await(DBWriter.subscribeFeed(feed, context));

        List<Feed> subscribed = DBReader.getFeedList();
        assertEquals(1, subscribed.size());
        assertEquals("A podcast", subscribed.get(0).getFeedTitle());
        assertTrue(subscribed.get(0).isSubscribed());
        assertEquals(1, DBReader.getSubscribedFeedsCount());
        assertEquals(Arrays.asList("https://a.example/feed.xml"), DBReader.getFeedListDownloadUrls());
    }

    @Test
    public void feedListIsSortedByTitle() throws Exception {
        Feed zulu = subscribedFeed("Zulu", "https://z.example/feed.xml");
        Feed alpha = subscribedFeed("Alpha", "https://a.example/feed.xml");
        await(DBWriter.addNewFeed(context, zulu, alpha));
        await(DBWriter.subscribeFeed(zulu, context));
        await(DBWriter.subscribeFeed(alpha, context));

        List<Feed> feeds = DBReader.getFeedList();
        assertEquals(Arrays.asList("Alpha", "Zulu"),
                Arrays.asList(feeds.get(0).getFeedTitle(), feeds.get(1).getFeedTitle()));
    }

    @Test
    public void getFeedReadsBackTheFeedByIdAndByUrl() throws Exception {
        Feed feed = subscribedFeed("Lookup", "https://l.example/feed.xml");
        await(DBWriter.addNewFeed(context, feed));

        Feed byId = DBReader.getFeed(feed.getId());
        assertNotNull(byId);
        assertEquals("Lookup", byId.getFeedTitle());

        Feed byUrl = DBReader.getFeed("https://l.example/feed.xml", false);
        assertNotNull(byUrl);
        assertEquals(feed.getId(), byUrl.getId());

        assertNull(DBReader.getFeed(999999L));
    }

    @Test
    public void deleteFeedRemovesTheFeedItsItemsAndItsMedia() throws Exception {
        Feed feed = subscribedFeed("Doomed", "https://d.example/feed.xml");
        FeedItem withMedia = item(feed, "Doomed episode");
        withMedia.setMedia(new FeedMedia(withMedia, "https://d.example/1.mp3", 1000L, "audio/mpeg"));
        FeedItem withoutMedia = item(feed, "Doomed note");
        feed.setItems(new ArrayList<>(Arrays.asList(withMedia, withoutMedia)));
        await(DBWriter.addNewFeed(context, feed));

        assertEquals(2, DBReader.getFeedItemList(feed).size());
        long mediaId = withMedia.getMedia().getId();
        assertNotNull(DBReader.getFeedMedia(mediaId));

        await(DBWriter.deleteFeed(context, feed.getId()));

        assertTrue(DBReader.getAllFeedList().isEmpty());
        assertNull(DBReader.getFeedItem(withMedia.getId()));
        assertNull(DBReader.getFeedItem(withoutMedia.getId()));
        assertNull(DBReader.getFeedMedia(mediaId));
    }

    @Test
    public void deletingAnUnknownFeedIsHarmless() throws Exception {
        Feed feed = subscribedFeed("Survivor", "https://s.example/feed.xml");
        await(DBWriter.addNewFeed(context, feed));

        await(DBWriter.deleteFeed(context, 999999L));

        assertEquals(1, DBReader.getAllFeedList().size());
    }

    @Test
    public void feedCustomTitleAndItunesIdRoundTrip() throws Exception {
        Feed feed = subscribedFeed("Original", "https://o.example/feed.xml");
        await(DBWriter.addNewFeed(context, feed));

        feed.setCustomTitle("Renamed");
        await(DBWriter.setFeedCustomTitle(feed));
        feed.setItunesId("itunes-77");
        await(DBWriter.setFeedItunesId(feed));

        Feed reloaded = DBReader.getFeed(feed.getId());
        assertNotNull(reloaded);
        assertEquals("Renamed", reloaded.getTitle());
        assertEquals("itunes-77", reloaded.getItunesId());
    }

    @Test
    public void feedPreferencesRoundTrip() throws Exception {
        Feed feed = subscribedFeed("Prefs", "https://p.example/feed.xml");
        await(DBWriter.addNewFeed(context, feed));

        FeedPreferences prefs = feed.getPreferences();
        prefs.setKeepUpdated(false);
        prefs.setFeedPlaybackSpeed(1.25f);
        await(DBWriter.setFeedPreferences(prefs));

        Feed reloaded = DBReader.getFeed(feed.getId());
        assertNotNull(reloaded);
        assertFalse(reloaded.getPreferences().getKeepUpdated());
        assertEquals(1.25f, reloaded.getPreferences().getFeedPlaybackSpeed(), 0.0001f);
    }

    // ------------------------------------------------------- feed items

    @Test
    public void setFeedItemAndGetFeedItemRoundTrip() throws Exception {
        Feed feed = subscribedFeed("Items", "https://i.example/feed.xml");
        FeedItem item = item(feed, "First episode");
        feed.setItems(new ArrayList<>(Arrays.asList(item)));
        await(DBWriter.addNewFeed(context, feed));

        FeedItem read = DBReader.getFeedItem(item.getId());
        assertNotNull(read);
        assertEquals("First episode", read.getTitle());
        assertEquals(feed.getId(), read.getFeedId());
        assertTrue(read.isNew());

        item.setPlayed(true);
        await(DBWriter.setFeedItem(item));

        FeedItem afterWrite = DBReader.getFeedItem(item.getId());
        assertNotNull(afterWrite);
        assertTrue(afterWrite.isPlayed());
    }

    @Test
    public void markItemPlayedRoundTrips() throws Exception {
        Feed feed = subscribedFeed("Played", "https://pl.example/feed.xml");
        FeedItem item = item(feed, "An episode");
        feed.setItems(new ArrayList<>(Arrays.asList(item)));
        await(DBWriter.addNewFeed(context, feed));

        await(DBWriter.markItemPlayed(FeedItem.PLAYED, item.getId()));
        assertTrue(DBReader.getFeedItem(item.getId()).isPlayed());

        await(DBWriter.markItemPlayed(FeedItem.UNPLAYED, item.getId()));
        FeedItem unplayed = DBReader.getFeedItem(item.getId());
        assertFalse(unplayed.isPlayed());
        assertFalse(unplayed.isNew());
    }

    @Test
    public void itemDescriptionIsLoadedOnDemand() throws Exception {
        Feed feed = subscribedFeed("Descriptions", "https://de.example/feed.xml");
        FeedItem item = item(feed, "An episode");
        item.setDescriptionIfLonger("The show notes");
        feed.setItems(new ArrayList<>(Arrays.asList(item)));
        await(DBWriter.addNewFeed(context, feed));

        FeedItem read = DBReader.getFeedItem(item.getId());
        assertNotNull(read);
        DBReader.loadDescriptionOfFeedItem(read);
        assertEquals("The show notes", read.getDescription());
    }

    @Test
    public void filteredItemListsHonourTheFilter() throws Exception {
        Feed feed = subscribedFeed("Filtered", "https://f.example/feed.xml");
        FeedItem played = item(feed, "Played episode");
        FeedItem unplayed = item(feed, "Unplayed episode");
        feed.setItems(new ArrayList<>(Arrays.asList(played, unplayed)));
        await(DBWriter.addNewFeed(context, feed));
        await(DBWriter.markItemPlayed(FeedItem.PLAYED, played.getId()));

        assertEquals(2, DBReader.getFeedItemList(feed).size());
        List<FeedItem> onlyPlayed = DBReader.getFeedItemList(feed,
                new allen.town.podcast.model.feed.FeedItemFilter(
                        allen.town.podcast.model.feed.FeedItemFilter.PLAYED));
        assertEquals(1, onlyPlayed.size());
        assertEquals("Played episode", onlyPlayed.get(0).getTitle());
    }

    @Test
    public void newItemsListSkipsFeedsThatWereOnlyPreviewed() throws Exception {
        Feed subscribed = subscribedFeed("Subscribed", "https://s.example/feed.xml");
        subscribed.setItems(new ArrayList<>(Arrays.asList(item(subscribed, "Wanted episode"))));
        Feed previewed = feed("Previewed", "https://pv.example/feed.xml");
        previewed.setItems(new ArrayList<>(Arrays.asList(item(previewed, "Unwanted episode"))));
        await(DBWriter.addNewFeed(context, subscribed, previewed));

        assertEquals("only subscribed feeds may feed the new list (and auto-download)",
                Arrays.asList("Wanted episode"), titles(DBReader.getNewItemsList(0, 100)));

        await(DBWriter.subscribeFeed(previewed, context));
        assertEquals(2, DBReader.getNewItemsList(0, 100).size());
    }

    // ------------------------------------------------------------- the queue

    @Test
    public void queueAddRemoveAndClearRoundTrip() throws Exception {
        Feed feed = subscribedFeed("Queue", "https://q.example/feed.xml");
        FeedItem first = item(feed, "First");
        FeedItem second = item(feed, "Second");
        feed.setItems(new ArrayList<>(Arrays.asList(first, second)));
        await(DBWriter.addNewFeed(context, feed));

        assertTrue(DBReader.getQueue().isEmpty());

        await(DBWriter.addQueueItem(context, false, first.getId(), second.getId()));
        assertEquals(Arrays.asList("First", "Second"), titles(DBReader.getQueue()));
        assertEquals(2, DBReader.getQueueIDList().size());

        await(DBWriter.removeQueueItem(context, false, first));
        assertEquals(Arrays.asList("Second"), titles(DBReader.getQueue()));

        await(DBWriter.clearQueue());
        assertTrue(DBReader.getQueue().isEmpty());
    }

    @Test
    public void enqueueingTheSameItemTwiceDoesNotDuplicateIt() throws Exception {
        Feed feed = subscribedFeed("Queue", "https://q.example/feed.xml");
        FeedItem item = item(feed, "Only once");
        feed.setItems(new ArrayList<>(Arrays.asList(item)));
        await(DBWriter.addNewFeed(context, feed));

        await(DBWriter.addQueueItem(context, false, item.getId()));
        await(DBWriter.addQueueItem(context, false, item.getId()));

        assertEquals(1, DBReader.getQueue().size());
    }

    @Test
    public void queueItemsCanBeMovedToTheTopBottomAndByIndex() throws Exception {
        Feed feed = subscribedFeed("Queue", "https://q.example/feed.xml");
        FeedItem a = item(feed, "A");
        FeedItem b = item(feed, "B");
        FeedItem c = item(feed, "C");
        feed.setItems(new ArrayList<>(Arrays.asList(a, b, c)));
        await(DBWriter.addNewFeed(context, feed));
        await(DBWriter.addQueueItem(context, false, a.getId(), b.getId(), c.getId()));
        assertEquals(Arrays.asList("A", "B", "C"), titles(DBReader.getQueue()));

        await(DBWriter.moveQueueItemToTop(c.getId(), false));
        assertEquals(Arrays.asList("C", "A", "B"), titles(DBReader.getQueue()));

        await(DBWriter.moveQueueItemToBottom(c.getId(), false));
        assertEquals(Arrays.asList("A", "B", "C"), titles(DBReader.getQueue()));

        await(DBWriter.moveQueueItem(0, 2, false));
        assertEquals(Arrays.asList("B", "C", "A"), titles(DBReader.getQueue()));
    }

    @Test
    public void movingAnItemThatIsNotQueuedLeavesTheQueueAlone() throws Exception {
        Feed feed = subscribedFeed("Queue", "https://q.example/feed.xml");
        FeedItem queued = item(feed, "Queued");
        FeedItem loose = item(feed, "Not queued");
        feed.setItems(new ArrayList<>(Arrays.asList(queued, loose)));
        await(DBWriter.addNewFeed(context, feed));
        await(DBWriter.addQueueItem(context, false, queued.getId()));

        await(DBWriter.moveQueueItemToTop(loose.getId(), false));

        assertEquals(Arrays.asList("Queued"), titles(DBReader.getQueue()));
    }

    @Test
    public void getNextInQueueWalksTheQueueOrder() throws Exception {
        Feed feed = subscribedFeed("Queue", "https://q.example/feed.xml");
        FeedItem a = item(feed, "A");
        FeedItem b = item(feed, "B");
        feed.setItems(new ArrayList<>(Arrays.asList(a, b)));
        await(DBWriter.addNewFeed(context, feed));
        await(DBWriter.addQueueItem(context, false, a.getId(), b.getId()));

        FeedItem next = DBReader.getNextInQueue(DBReader.getQueue().get(0));
        assertNotNull(next);
        assertEquals("B", next.getTitle());
        assertNull(DBReader.getNextInQueue(DBReader.getQueue().get(1)));
    }

    @Test
    public void deletingAQueuedItemAlsoRemovesItFromTheQueue() throws Exception {
        Feed feed = subscribedFeed("Queue", "https://q.example/feed.xml");
        FeedItem kept = item(feed, "Kept");
        FeedItem removed = item(feed, "Removed");
        feed.setItems(new ArrayList<>(Arrays.asList(kept, removed)));
        await(DBWriter.addNewFeed(context, feed));
        await(DBWriter.addQueueItem(context, false, kept.getId(), removed.getId()));

        await(DBWriter.deleteFeedItems(context, Arrays.asList(removed)));

        assertEquals(Arrays.asList("Kept"), titles(DBReader.getQueue()));
        assertNull(DBReader.getFeedItem(removed.getId()));
    }

    // --------------------------------------------------------------- favorites

    @Test
    public void favoritesRoundTrip() throws Exception {
        Feed feed = subscribedFeed("Favorites", "https://fa.example/feed.xml");
        FeedItem item = item(feed, "Beloved");
        feed.setItems(new ArrayList<>(Arrays.asList(item)));
        await(DBWriter.addNewFeed(context, feed));

        assertTrue(DBReader.getFavoriteItemsList(0, 10).isEmpty());

        await(DBWriter.addFavoriteItem(item));
        assertEquals(Arrays.asList("Beloved"), titles(DBReader.getFavoriteItemsList(0, 10)));

        await(DBWriter.removeFavoriteItem(item));
        assertTrue(DBReader.getFavoriteItemsList(0, 10).isEmpty());
    }

    // ------------------------------------------------------------------ media

    @Test
    public void mediaDownloadStateAndPlaybackInformationRoundTrip() throws Exception {
        Feed feed = subscribedFeed("Media", "https://m.example/feed.xml");
        FeedItem item = item(feed, "An episode");
        FeedMedia media = new FeedMedia(item, "https://m.example/1.mp3", 2048L, "audio/mpeg");
        item.setMedia(media);
        feed.setItems(new ArrayList<>(Arrays.asList(item)));
        await(DBWriter.addNewFeed(context, feed));

        media.setDownloaded(true);
        media.setFile_url("/tmp/an-episode.mp3");
        await(DBWriter.setFeedMediaDownloadState(media));

        FeedMedia afterDownload = DBReader.getFeedMedia(media.getId());
        assertNotNull(afterDownload);
        assertTrue(afterDownload.isDownloaded());
        assertEquals("/tmp/an-episode.mp3", afterDownload.getFile_url());
        assertEquals(1, DBReader.getNumberOfDownloadedEpisodes());
        assertEquals(Arrays.asList("An episode"), titles(DBReader.getDownloadedItems()));

        media.setPosition(4321);
        media.setDuration(98765);
        await(DBWriter.setFeedMediaPlaybackInformation(media));

        FeedMedia afterPlayback = DBReader.getFeedMedia(media.getId());
        assertNotNull(afterPlayback);
        assertEquals(4321, afterPlayback.getPosition());
        assertEquals(98765, afterPlayback.getDuration());
        // The download state survived the playback write and vice versa.
        assertTrue(afterPlayback.isDownloaded());
    }

    @Test
    public void setItemListWritesEveryItem() throws Exception {
        Feed feed = subscribedFeed("Bulk", "https://b.example/feed.xml");
        FeedItem one = item(feed, "One");
        FeedItem two = item(feed, "Two");
        feed.setItems(new ArrayList<>(Arrays.asList(one, two)));
        await(DBWriter.addNewFeed(context, feed));

        one.setPlayed(true);
        two.setPlayed(true);
        await(DBWriter.setItemList(Arrays.asList(one, two)));

        assertTrue(DBReader.getFeedItem(one.getId()).isPlayed());
        assertTrue(DBReader.getFeedItem(two.getId()).isPlayed());
        assertEquals(2, DBReader.getPlayedItems().size());
    }

    @Test
    public void updateFeedDownloadUrlRewritesTheUrl() throws Exception {
        Feed feed = subscribedFeed("Moved", "https://old.example/feed.xml");
        await(DBWriter.addNewFeed(context, feed));
        await(DBWriter.subscribeFeed(feed, context));

        await(DBWriter.updateFeedDownloadURL(
                "https://old.example/feed.xml", "https://new.example/feed.xml"));

        assertEquals(Arrays.asList("https://new.example/feed.xml"), DBReader.getFeedListDownloadUrls());
    }

    // --------------------------------------------------------------- statistics

    @Test
    public void resetStatisticsForOneFeedLeavesTheOthersAlone() throws Exception {
        Feed keep = subscribedFeed("Keep", "https://k.example/feed.xml");
        FeedItem keepItem = item(keep, "Keep episode");
        FeedMedia keepMedia = new FeedMedia(keepItem, "https://k.example/1.mp3", 10L, "audio/mpeg");
        keepItem.setMedia(keepMedia);
        keep.setItems(new ArrayList<>(Arrays.asList(keepItem)));
        Feed reset = subscribedFeed("Reset", "https://r.example/feed.xml");
        FeedItem resetItem = item(reset, "Reset episode");
        FeedMedia resetMedia = new FeedMedia(resetItem, "https://r.example/1.mp3", 10L, "audio/mpeg");
        resetItem.setMedia(resetMedia);
        reset.setItems(new ArrayList<>(Arrays.asList(resetItem)));
        await(DBWriter.addNewFeed(context, keep, reset));
        for (FeedMedia media : Arrays.asList(keepMedia, resetMedia)) {
            media.setDuration(60_000);
            media.setPlayedDuration(30_000);
            media.setLastPlayedTime(System.currentTimeMillis());
            media.setPosition(1234);
            await(DBWriter.setFeedMediaPlaybackInformation(media));
        }
        assertEquals(2, DBReader.getStatistics(false, 0, Long.MAX_VALUE).feedTime.size());

        await(DBWriter.resetStatistics(reset.getId()));

        DBReader.StatisticsResult stats = DBReader.getStatistics(false, 0, Long.MAX_VALUE);
        assertEquals(1, stats.feedTime.size());
        assertEquals("Keep", stats.feedTime.get(0).feed.getTitle());
        assertEquals(30, stats.feedTime.get(0).timePlayed);
        FeedMedia afterReset = DBReader.getFeedMedia(resetMedia.getId());
        assertNotNull(afterReset);
        assertEquals(0, afterReset.getPlayedDuration());
        assertEquals("the playback position is not part of the statistics", 1234, afterReset.getPosition());

        await(DBWriter.resetStatistics());
        assertTrue(DBReader.getStatistics(false, 0, Long.MAX_VALUE).feedTime.isEmpty());
    }

    @Test
    public void clearPlaybackHistoryForOneFeedDropsItFromHistoryAndStatistics() throws Exception {
        Feed keep = subscribedFeed("Keep", "https://k.example/feed.xml");
        FeedItem keepItem = item(keep, "Keep episode");
        FeedMedia keepMedia = new FeedMedia(keepItem, "https://k.example/1.mp3", 10L, "audio/mpeg");
        keepItem.setMedia(keepMedia);
        keep.setItems(new ArrayList<>(Arrays.asList(keepItem)));
        Feed clear = subscribedFeed("Clear", "https://c.example/feed.xml");
        FeedItem clearItem = item(clear, "Clear episode");
        FeedMedia clearMedia = new FeedMedia(clearItem, "https://c.example/1.mp3", 10L, "audio/mpeg");
        clearItem.setMedia(clearMedia);
        clear.setItems(new ArrayList<>(Arrays.asList(clearItem)));
        await(DBWriter.addNewFeed(context, keep, clear));
        for (FeedMedia media : Arrays.asList(keepMedia, clearMedia)) {
            media.setDuration(60_000);
            media.setPlayedDuration(30_000);
            media.setLastPlayedTime(System.currentTimeMillis());
            media.setPosition(1234);
            await(DBWriter.setFeedMediaPlaybackInformation(media));
            await(DBWriter.addItemToPlaybackHistory(media));
        }
        assertEquals(2, DBReader.getPlaybackHistory(0, 100).size());

        await(DBWriter.clearPlaybackHistory(clear.getId()));

        assertEquals(Arrays.asList("Keep episode"), titles(DBReader.getPlaybackHistory(0, 100)));
        DBReader.StatisticsResult stats = DBReader.getStatistics(false, 0, Long.MAX_VALUE);
        assertEquals(1, stats.feedTime.size());
        assertEquals("Keep", stats.feedTime.get(0).feed.getTitle());
        FeedMedia afterClear = DBReader.getFeedMedia(clearMedia.getId());
        assertNotNull(afterClear);
        assertNull(afterClear.getPlaybackCompletionDate());
        assertEquals(0, afterClear.getPlayedDuration());
        assertEquals(0, afterClear.getLastPlayedTime());
        assertEquals("the playback position is kept", 1234, afterClear.getPosition());
    }

    // --------------------------------------------------------------- helpers

    private static void await(Future<?> future) throws InterruptedException, ExecutionException {
        future.get();
    }

    private static List<String> titles(List<FeedItem> items) {
        List<String> titles = new ArrayList<>(items.size());
        for (FeedItem item : items) {
            titles.add(item.getTitle());
        }
        return titles;
    }

    private static Feed feed(String title, String url) {
        Feed feed = new Feed(url, null, title);
        feed.setDescription(title + " description");
        feed.setPreferences(new FeedPreferences(0, true, true,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                null, null, new FeedFilter(), FeedPreferences.SPEED_USE_GLOBAL,
                0, 0, false, new HashSet<>(), false, false, false, false));
        return feed;
    }

    private static Feed subscribedFeed(String title, String url) {
        Feed feed = feed(title, url);
        feed.setSubscribed(true);
        return feed;
    }

    private static FeedItem item(Feed feed, String title) {
        return new FeedItem(0, title, "guid-" + title, "https://example.com/" + title,
                new Date(1_600_000_000_000L), FeedItem.NEW, feed);
    }
}
