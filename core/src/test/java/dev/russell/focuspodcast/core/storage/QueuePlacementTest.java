package dev.russell.focuspodcast.core.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

import dev.russell.focuspodcast.core.pref.PlaybackPreferences;
import dev.russell.focuspodcast.core.pref.Prefs;
import dev.russell.focuspodcast.core.pref.UsageStatistics;
import dev.russell.focuspodcast.core.sync.SynchronizationCredentials;
import dev.russell.focuspodcast.core.sync.SynchronizationSettings;
import dev.russell.focuspodcast.model.feed.Feed;
import dev.russell.focuspodcast.model.feed.FeedFilter;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedMedia;
import dev.russell.focuspodcast.model.feed.FeedPreferences;
import dev.russell.focuspodcast.model.feed.SortOrder;
import dev.russell.focuspodcast.model.feed.VolumeAdaptionSetting;
import dev.russell.focuspodcast.playback.base.PlayerStatus;
import dev.russell.focuspodcast.storage.db.Db;

/**
 * {@link DBWriter#playNext} and {@link DBWriter#playLast} against a real (Robolectric) database.
 * Set up the same way as {@link DBReaderWriterTest}.
 */
@RunWith(RobolectricTestRunner.class)
public class QueuePlacementTest {

    private Application context;
    private Feed feed;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        Prefs.resetForTests();
        Prefs.init(context);
        PlaybackPreferences.init(context);
        UsageStatistics.init(context);
        SynchronizationCredentials.init(context);
        SynchronizationSettings.init(context);
        Db.init(context);

        feed = new Feed("https://q.example/feed.xml", null, "Queue");
        feed.setSubscribed(true);
        feed.setPreferences(new FeedPreferences(0, true, true,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                null, null, new FeedFilter(), FeedPreferences.SPEED_USE_GLOBAL,
                0, 0, false, new HashSet<>(), false, 0, false, false));
        List<FeedItem> items = new ArrayList<>();
        for (String title : Arrays.asList("A", "B", "C", "D", "E")) {
            FeedItem item = new FeedItem(0, title, "guid-" + title, "https://example.com/" + title,
                    new Date(1_600_000_000_000L), FeedItem.UNPLAYED, feed);
            item.setMedia(new FeedMedia(item, "https://example.com/" + title + ".mp3", 1000L, "audio/mpeg"));
            items.add(item);
        }
        feed.setItems(items);
        await(DBWriter.addNewFeed(context, feed));
        PlaybackPreferences.writeNoMediaPlaying();
    }

    @After
    public void tearDown() {
        DBWriter.tearDownTests();
        Db.tearDownTests();
        context.deleteDatabase(Db.DATABASE_NAME);
        Prefs.resetForTests();
    }

    @Test
    public void playNextWithNothingPlayingGoesToTheFront() throws Exception {
        await(DBWriter.addQueueItem(context, false, id("A"), id("B")));

        await(DBWriter.playNext(context, false, id("C")));

        assertEquals(Arrays.asList("C", "A", "B"), queueTitles());
    }

    @Test
    public void playNextIgnoresTheEnqueueLocationPreference() throws Exception {
        Prefs.setEnqueueLocation(Prefs.EnqueueLocation.BACK);
        await(DBWriter.addQueueItem(context, false, id("A"), id("B")));

        await(DBWriter.playNext(context, false, id("C")));

        assertEquals(Arrays.asList("C", "A", "B"), queueTitles());
    }

    @Test
    public void playNextGoesRightAfterTheCurrentEpisode() throws Exception {
        await(DBWriter.addQueueItem(context, false, id("A"), id("B"), id("C")));
        play("B");

        await(DBWriter.playNext(context, false, id("D")));

        assertEquals(Arrays.asList("A", "B", "D", "C"), queueTitles());
    }

    @Test
    public void playNextGoesToTheFrontWhenTheCurrentEpisodeIsNotQueued() throws Exception {
        await(DBWriter.addQueueItem(context, false, id("A"), id("B")));
        play("E");

        await(DBWriter.playNext(context, false, id("D")));

        assertEquals(Arrays.asList("D", "A", "B"), queueTitles());
    }

    @Test
    public void playNextMovesAnAlreadyQueuedEpisodeWithoutDuplicatingIt() throws Exception {
        await(DBWriter.addQueueItem(context, false, id("A"), id("B"), id("C"), id("D")));
        play("A");

        await(DBWriter.playNext(context, false, id("D")));

        assertEquals(Arrays.asList("A", "D", "B", "C"), queueTitles());
    }

    @Test
    public void playNextKeepsTheOrderOfABatch() throws Exception {
        await(DBWriter.addQueueItem(context, false, id("A"), id("B"), id("C")));
        play("A");

        // a mix of new (E, D) and already queued (C) episodes, with a duplicate id
        await(DBWriter.playNext(context, false, id("E"), id("C"), id("D"), id("E")));

        assertEquals(Arrays.asList("A", "E", "C", "D", "B"), queueTitles());
    }

    @Test
    public void playNextSkipsTheEpisodeThatIsPlaying() throws Exception {
        await(DBWriter.addQueueItem(context, false, id("A"), id("B")));
        play("B");

        await(DBWriter.playNext(context, false, id("B")));
        await(DBWriter.playLast(context, false, id("B")));

        assertEquals(Arrays.asList("A", "B"), queueTitles());
    }

    @Test
    public void playLastAppendsANewEpisode() throws Exception {
        Prefs.setEnqueueLocation(Prefs.EnqueueLocation.FRONT);
        await(DBWriter.addQueueItem(context, false, id("A"), id("B")));

        await(DBWriter.playLast(context, false, id("C"), id("D")));

        assertEquals(Arrays.asList("A", "B", "C", "D"), queueTitles());
        assertTrue(DBReader.getFeedItem(id("C")).isTagged(FeedItem.TAG_QUEUE));
    }

    @Test
    public void playLastMovesAnAlreadyQueuedEpisode() throws Exception {
        await(DBWriter.addQueueItem(context, false, id("A"), id("B"), id("C")));

        await(DBWriter.playLast(context, false, id("A")));

        assertEquals(Arrays.asList("B", "C", "A"), queueTitles());
        assertEquals(3, DBReader.getQueueIDList().size());
    }

    @Test
    public void aKeptSortedQueueWinsOverThePlacement() throws Exception {
        Prefs.setPlaylistKeepSorted(true);
        Prefs.setQueueKeepSortedOrder(SortOrder.EPISODE_TITLE_A_Z);
        assertTrue(DBWriter.isQueueOrderAutomatic());
        await(DBWriter.addQueueItem(context, false, id("B"), id("D")));
        play("B");

        await(DBWriter.playNext(context, false, id("E"), id("A")));
        assertEquals(Arrays.asList("A", "B", "D", "E"), queueTitles());

        await(DBWriter.playLast(context, false, id("A")));
        assertEquals(Arrays.asList("A", "B", "D", "E"), queueTitles());
    }

    @Test
    public void aRandomKeepSortedOrderDoesNotOverrideThePlacement() throws Exception {
        Prefs.setPlaylistKeepSorted(true);
        Prefs.setQueueKeepSortedOrder(SortOrder.RANDOM);
        assertFalse(DBWriter.isQueueOrderAutomatic());
        await(DBWriter.addQueueItem(context, false, id("A"), id("B")));

        await(DBWriter.playNext(context, false, id("C")));

        assertEquals(Arrays.asList("C", "A", "B"), queueTitles());
    }

    // --------------------------------------------------------------- helpers

    private long id(String title) {
        for (FeedItem item : feed.getItems()) {
            if (item.getTitle().equals(title)) {
                return item.getId();
            }
        }
        throw new IllegalArgumentException(title);
    }

    private void play(String title) {
        FeedMedia media = DBReader.getFeedItem(id(title)).getMedia();
        PlaybackPreferences.writeMediaPlaying(media, PlayerStatus.PLAYING);
    }

    private static List<String> queueTitles() {
        List<String> titles = new ArrayList<>();
        for (FeedItem item : DBReader.getQueue()) {
            titles.add(item.getTitle());
        }
        return titles;
    }

    private static void await(Future<?> future) throws InterruptedException, ExecutionException {
        future.get();
    }
}
