package allen.town.podcast.core.storage;

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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import allen.town.podcast.core.pref.PlaybackPreferences;
import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.pref.UsageStatistics;
import allen.town.podcast.core.sync.SynchronizationCredentials;
import allen.town.podcast.core.sync.SynchronizationSettings;
import allen.town.podcast.model.download.DownloadError;
import allen.town.podcast.model.download.DownloadStatus;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedFilter;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.model.feed.FeedPreferences;
import allen.town.podcast.model.feed.VolumeAdaptionSetting;
import allen.town.podcast.storage.db.Db;

/**
 * {@link DBWriter#deletePlayedDownloads} and the dismissable feed warnings, end to end through
 * {@link DBWriter} and {@link DBReader} against a Robolectric SQLite database. Set up like
 * {@link DBReaderWriterTest}.
 */
@RunWith(RobolectricTestRunner.class)
public class DownloadsCleanupAndWarningsTest {

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

    // ------------------------------------------------------- delete played

    @Test
    public void deletePlayedDownloadsKeepsUnplayedAndFavorites() throws Exception {
        Feed feed = feed();
        FeedItem played = episode(feed, "Played");
        FeedItem unplayed = episode(feed, "Unplayed");
        FeedItem favorite = episode(feed, "Favorite");
        feed.setItems(new ArrayList<>(Arrays.asList(played, unplayed, favorite)));
        DBWriter.addNewFeed(context, feed).get();

        File playedFile = markDownloaded(played);
        File unplayedFile = markDownloaded(unplayed);
        File favoriteFile = markDownloaded(favorite);
        DBWriter.markItemPlayed(FeedItem.PLAYED, played.getId(), favorite.getId()).get();
        DBWriter.addFavoriteItem(favorite).get();

        int deleted = DBWriter.deletePlayedDownloads(context).get();

        assertEquals(1, deleted);
        assertFalse("the played episode's file is deleted", playedFile.exists());
        assertTrue(unplayedFile.exists());
        assertTrue(favoriteFile.exists());
        List<String> remaining = new ArrayList<>();
        for (FeedItem item : DBReader.getDownloadedItems()) {
            remaining.add(item.getTitle());
        }
        assertEquals(2, remaining.size());
        assertTrue(remaining.containsAll(Arrays.asList("Unplayed", "Favorite")));
    }

    @Test
    public void deletePlayedDownloadsWithNothingToDeleteReturnsZero() throws Exception {
        assertEquals(0, (int) DBWriter.deletePlayedDownloads(context).get());
    }

    // ------------------------------------------------------- feed warnings

    @Test
    public void dismissedWarningIsNotLoggedAgainUntilReset() throws Exception {
        Feed feed = feed();
        DBWriter.addNewFeed(context, feed).get();

        DownloadStatus first = warning(feed, "dup2:a|b");
        DBWriter.addDownloadStatus(first).get();
        assertEquals(1, DBReader.getDownloadLog().size());
        DownloadStatus logged = DBReader.getDownloadLog().get(0);
        assertEquals("dup2:a|b", logged.getWarningKey());

        DBWriter.dismissFeedWarning(logged).get();
        assertTrue("dismissing removes the row", DBReader.getDownloadLog().isEmpty());
        assertEquals(1, DBReader.getDismissedFeedWarningCount(feed.getId()));

        DBWriter.addDownloadStatus(warning(feed, "dup2:a|b")).get();
        assertTrue("the next refresh does not log it again", DBReader.getDownloadLog().isEmpty());

        DBWriter.resetDismissedWarnings(feed.getId()).get();
        assertEquals(0, DBReader.getDismissedFeedWarningCount(feed.getId()));
        DBWriter.addDownloadStatus(warning(feed, "dup2:a|b")).get();
        assertEquals(1, DBReader.getDownloadLog().size());
    }

    @Test
    public void undoPutsTheWarningBack() throws Exception {
        Feed feed = feed();
        DBWriter.addNewFeed(context, feed).get();
        DBWriter.addDownloadStatus(warning(feed, "idchg:7")).get();
        DownloadStatus logged = DBReader.getDownloadLog().get(0);

        DBWriter.dismissFeedWarning(logged).get();
        DBWriter.undoDismissFeedWarning(logged).get();

        assertEquals(0, DBReader.getDismissedFeedWarningCount(feed.getId()));
        List<DownloadStatus> log = DBReader.getDownloadLog();
        assertEquals(1, log.size());
        assertEquals("idchg:7", log.get(0).getWarningKey());
    }

    @Test
    public void realFailuresCannotBeDismissed() throws Exception {
        Feed feed = feed();
        DBWriter.addNewFeed(context, feed).get();
        DownloadStatus failure = new DownloadStatus(feed, "Feed", DownloadError.ERROR_CONNECTION_ERROR,
                false, "timeout", false);
        DBWriter.addDownloadStatus(failure).get();
        DownloadStatus logged = DBReader.getDownloadLog().get(0);

        DBWriter.dismissFeedWarning(logged).get();

        assertEquals(1, DBReader.getDownloadLog().size());
        assertEquals(0, DBReader.getDismissedFeedWarningCount(feed.getId()));
    }

    @Test
    public void deletingTheFeedForgetsItsDismissals() throws Exception {
        Feed feed = feed();
        DBWriter.addNewFeed(context, feed).get();
        DBWriter.addDownloadStatus(warning(feed, "dup2:a|b")).get();
        DBWriter.dismissFeedWarning(DBReader.getDownloadLog().get(0)).get();
        assertEquals(1, DBReader.getDismissedFeedWarningCount(feed.getId()));

        DBWriter.deleteFeed(context, feed.getId()).get();

        assertEquals(0, DBReader.getDismissedFeedWarningCount(feed.getId()));
    }

    // ------------------------------------------------------- helpers

    private File markDownloaded(FeedItem item) throws Exception {
        FeedMedia media = item.getMedia();
        assertNotNull(media);
        File file = new File(context.getFilesDir(), item.getTitle() + ".mp3");
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("could not create " + file);
        }
        media.setFile_url(file.getAbsolutePath());
        media.setDownloaded(true);
        DBWriter.setFeedMediaDownloadState(media).get();
        return file;
    }

    private static DownloadStatus warning(Feed feed, String key) {
        DownloadStatus status = new DownloadStatus(feed, "Episode title",
                DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE, false, "details", false);
        status.setWarningKey(key);
        return status;
    }

    private static Feed feed() {
        Feed feed = new Feed("https://w.example/feed.xml", null, "Warnings");
        feed.setSubscribed(true);
        feed.setPreferences(new FeedPreferences(0, true, true,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                null, null, new FeedFilter(), FeedPreferences.SPEED_USE_GLOBAL,
                0, 0, false, new HashSet<>(), false, false, false, false));
        return feed;
    }

    private static FeedItem episode(Feed feed, String title) {
        FeedItem item = new FeedItem(0, title, "guid-" + title, "https://w.example/" + title,
                new Date(1_600_000_000_000L), FeedItem.UNPLAYED, feed);
        item.setMedia(new FeedMedia(item, "https://w.example/" + title + ".mp3", 1000L, "audio/mpeg"));
        return item;
    }
}
