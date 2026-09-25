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
import java.util.Collections;
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
import allen.town.podcast.model.feed.AdSegment;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedFilter;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedPreferences;
import allen.town.podcast.model.feed.VolumeAdaptionSetting;
import allen.town.podcast.storage.db.Db;

/**
 * Round trips for the ad auto-skip storage layer: the {@code ad_segments} table through
 * {@link DBWriter} and {@link DBReader}, and the per-feed ad-skip override.
 *
 * <p>The setup mirrors {@link DBReaderWriterTest}: core is initialised piece by piece rather than
 * through {@code ClientConfig.initialize}, and every write is awaited through its {@link Future} so
 * the single-threaded database executor has finished before anything is asserted.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class AdSegmentStorageTest {

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
    public void adSegmentsRoundTripInStartOrder() throws Exception {
        FeedItem item = storedItem("Sponsored episode");

        await(DBWriter.replaceAdSegments(item.getId(), AdSegment.Source.DETECTED, Arrays.asList(
                new AdSegment(item.getId(), 90_000, 120_000, AdSegment.Source.DETECTED, 0.8f),
                new AdSegment(item.getId(), 10_000, 40_000, AdSegment.Source.DETECTED, 0.9f))));

        List<AdSegment> stored = DBReader.loadAdSegmentsOfFeedItem(item.getId());
        assertEquals(2, stored.size());
        assertEquals(10_000, stored.get(0).getStartMs());
        assertEquals(40_000, stored.get(0).getEndMs());
        assertEquals(0.9f, stored.get(0).getConfidence(), 0.0001f);
        assertEquals(AdSegment.Source.DETECTED, stored.get(0).getSource());
        assertTrue(stored.get(0).isEnabled());
        assertEquals(item.getId(), stored.get(0).getFeedItemId());
        assertEquals(90_000, stored.get(1).getStartMs());
        assertTrue("every stored segment must have a row id", stored.get(0).getId() > 0);
    }

    @Test
    public void replacingOneSourceLeavesTheOtherSourcesAlone() throws Exception {
        FeedItem item = storedItem("Mixed sources");
        await(DBWriter.replaceAdSegments(item.getId(), AdSegment.Source.DETECTED,
                Collections.singletonList(
                        new AdSegment(item.getId(), 10_000, 20_000, AdSegment.Source.DETECTED, 0.7f))));
        await(DBWriter.addAdSegment(
                new AdSegment(item.getId(), 50_000, 60_000, AdSegment.Source.MANUAL, 1f)));

        // The detector runs again and finds something different.
        await(DBWriter.replaceAdSegments(item.getId(), AdSegment.Source.DETECTED,
                Collections.singletonList(
                        new AdSegment(item.getId(), 15_000, 25_000, AdSegment.Source.DETECTED, 0.95f))));

        List<AdSegment> stored = DBReader.loadAdSegmentsOfFeedItem(item.getId());
        assertEquals(2, stored.size());
        assertEquals(15_000, stored.get(0).getStartMs());
        assertEquals(AdSegment.Source.DETECTED, stored.get(0).getSource());
        assertEquals("the manual segment must survive a detector re-run",
                AdSegment.Source.MANUAL, stored.get(1).getSource());
        assertEquals(50_000, stored.get(1).getStartMs());
    }

    @Test
    public void manualSegmentCanBeToggledAndDeleted() throws Exception {
        FeedItem item = storedItem("Toggled episode");
        await(DBWriter.addAdSegment(
                new AdSegment(item.getId(), 30_000, 45_000, AdSegment.Source.MANUAL, 1f)));

        AdSegment stored = onlySegment(item.getId());
        assertTrue(stored.isEnabled());

        await(DBWriter.setAdSegmentEnabled(stored.getId(), item.getId(), false));
        assertFalse("the segment must read back disabled", onlySegment(item.getId()).isEnabled());

        await(DBWriter.setAdSegmentEnabled(stored.getId(), item.getId(), true));
        assertTrue(onlySegment(item.getId()).isEnabled());

        await(DBWriter.deleteAdSegment(stored.getId(), item.getId()));
        assertTrue(DBReader.loadAdSegmentsOfFeedItem(item.getId()).isEmpty());
    }

    @Test
    public void episodesWithoutSegmentsReadBackAsAnEmptyList() throws Exception {
        FeedItem item = storedItem("Ad free");
        assertTrue(DBReader.loadAdSegmentsOfFeedItem(item.getId()).isEmpty());
    }

    @Test
    public void deletingAnEpisodeDeletesItsAdSegments() throws Exception {
        Feed feed = subscribedFeed("Cascade", "https://c.example/feed.xml");
        FeedItem kept = item(feed, "Kept");
        FeedItem removed = item(feed, "Removed");
        feed.setItems(new ArrayList<>(Arrays.asList(kept, removed)));
        await(DBWriter.addNewFeed(context, feed));

        await(DBWriter.replaceAdSegments(kept.getId(), AdSegment.Source.DETECTED,
                Collections.singletonList(
                        new AdSegment(kept.getId(), 1_000, 2_000, AdSegment.Source.DETECTED, 0.6f))));
        await(DBWriter.replaceAdSegments(removed.getId(), AdSegment.Source.DETECTED,
                Collections.singletonList(
                        new AdSegment(removed.getId(), 3_000, 4_000, AdSegment.Source.DETECTED, 0.6f))));

        Db adapter = Db.getInstance();
        adapter.open();
        adapter.removeFeedItems(Collections.singletonList(removed));
        adapter.close();

        assertTrue("segments must not outlive their episode",
                DBReader.loadAdSegmentsOfFeedItem(removed.getId()).isEmpty());
        assertEquals("an untouched episode keeps its segments",
                1, DBReader.loadAdSegmentsOfFeedItem(kept.getId()).size());
    }

    @Test
    public void feedPreferencesPersistAdSkipOverride() throws Exception {
        Feed feed = subscribedFeed("Prefs", "https://p.example/feed.xml");
        await(DBWriter.addNewFeed(context, feed));

        Feed reloaded = DBReader.getFeed(feed.getId());
        assertNotNull(reloaded);
        assertNull("a new feed must follow the global default",
                reloaded.getPreferences().getAdSkipOverride());

        FeedPreferences preferences = reloaded.getPreferences();
        preferences.setAdSkipOverride(Boolean.FALSE);
        await(DBWriter.setFeedPreferences(preferences));
        assertEquals(Boolean.FALSE, DBReader.getFeedPreferences(feed.getId()).getAdSkipOverride());

        preferences.setAdSkipOverride(Boolean.TRUE);
        await(DBWriter.setFeedPreferences(preferences));
        assertEquals(Boolean.TRUE, DBReader.getFeed(feed.getId()).getPreferences().getAdSkipOverride());

        preferences.setAdSkipOverride(null);
        await(DBWriter.setFeedPreferences(preferences));
        assertNull(DBReader.getFeedPreferences(feed.getId()).getAdSkipOverride());
    }

    // --------------------------------------------------------------- helpers

    private AdSegment onlySegment(long feedItemId) {
        List<AdSegment> segments = DBReader.loadAdSegmentsOfFeedItem(feedItemId);
        assertEquals(1, segments.size());
        return segments.get(0);
    }

    private FeedItem storedItem(String title) throws Exception {
        Feed feed = subscribedFeed(title + " feed", "https://example.com/" + title + ".xml");
        FeedItem item = item(feed, title);
        feed.setItems(new ArrayList<>(Collections.singletonList(item)));
        await(DBWriter.addNewFeed(context, feed));
        assertTrue(item.getId() > 0);
        return item;
    }

    private static void await(Future<?> future) throws InterruptedException, ExecutionException {
        future.get();
    }

    private static Feed subscribedFeed(String title, String url) {
        Feed feed = new Feed(url, null, title);
        feed.setDescription(title + " description");
        feed.setPreferences(new FeedPreferences(0, true, true,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                null, null, new FeedFilter(), FeedPreferences.SPEED_USE_GLOBAL,
                0, 0, false, new HashSet<>(), false, false, false, false));
        feed.setSubscribed(true);
        return feed;
    }

    private static FeedItem item(Feed feed, String title) {
        return new FeedItem(0, title, "guid-" + title, "https://example.com/" + title,
                new Date(1_600_000_000_000L), FeedItem.NEW, feed);
    }
}
