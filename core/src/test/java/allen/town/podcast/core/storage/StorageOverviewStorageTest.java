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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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
 * {@link DBReader#getDownloadedMediaWithFeed()} and {@link DBWriter#deleteStorageBucket} against a
 * Robolectric SQLite database. Set up like {@link DownloadsCleanupAndWarningsTest}.
 */
@RunWith(RobolectricTestRunner.class)
public class StorageOverviewStorageTest {

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
    public void readsDownloadedMediaWithEpisodeAndFeedInfoInOneList() throws Exception {
        Feed show = feed("https://s.example/feed.xml", "Show", true, false);
        show.setImageUrl("https://s.example/cover.png");
        FeedItem played = episode(show, "Played");
        FeedItem favorite = episode(show, "Favorite");
        FeedItem streamed = episode(show, "Streamed");
        show.setItems(new ArrayList<>(Arrays.asList(played, favorite, streamed)));
        DBWriter.addNewFeed(context, show).get();
        show.setCustomTitle("My Show");
        DBWriter.setFeedCustomTitle(show).get();

        Feed book = feed("https://b.example/feed.xml", "Book", false, true);
        FeedItem chapter = episode(book, "Chapter");
        book.setItems(new ArrayList<>(Arrays.asList(chapter)));
        DBWriter.addNewFeed(context, book).get();

        File playedFile = markDownloaded(played, 10);
        markDownloaded(favorite, 20);
        markDownloaded(chapter, 30);
        DBWriter.markItemPlayed(FeedItem.PLAYED, played.getId()).get();
        DBWriter.addFavoriteItem(favorite).get();

        List<DownloadedMedia> rows = DBReader.getDownloadedMediaWithFeed();

        assertEquals("the streamed episode is not downloaded", 3, rows.size());
        Map<Long, DownloadedMedia> byMedia = new HashMap<>();
        for (DownloadedMedia row : rows) {
            byMedia.put(row.mediaId, row);
        }
        DownloadedMedia playedRow = byMedia.get(mediaId(played));
        assertNotNull(playedRow);
        assertTrue(playedRow.played);
        assertFalse(playedRow.favorite);
        assertEquals(playedFile.getAbsolutePath(), playedRow.fileUrl);
        assertEquals(show.getId(), playedRow.feedId);
        assertEquals("the custom title wins", "My Show", playedRow.feedTitle);
        assertEquals("https://s.example/cover.png", playedRow.feedImageUrl);
        assertTrue(playedRow.feedSubscribed);
        assertFalse(playedRow.feedAudiobook);

        DownloadedMedia favoriteRow = byMedia.get(mediaId(favorite));
        assertNotNull(favoriteRow);
        assertFalse(favoriteRow.played);
        assertTrue(favoriteRow.favorite);

        DownloadedMedia chapterRow = byMedia.get(mediaId(chapter));
        assertNotNull(chapterRow);
        assertEquals("Book", chapterRow.feedTitle);
        assertFalse(chapterRow.feedSubscribed);
        assertTrue(chapterRow.feedAudiobook);

        StorageUsage.Overview overview = StorageUsage.aggregate(StorageUsage.measure(rows), true, 0);
        assertEquals(60, overview.bytes);
        assertEquals(StorageUsage.UNSUBSCRIBED_BUCKET, overview.feeds.get(0).bucketId);
        assertEquals(show.getId(), overview.feeds.get(1).bucketId);
    }

    @Test
    public void deletePlayedOfOneFeedKeepsFavouritesAndOtherFeeds() throws Exception {
        Feed show = feed("https://s.example/feed.xml", "Show", true, false);
        FeedItem played = episode(show, "Played");
        FeedItem playedFavorite = episode(show, "PlayedFavorite");
        FeedItem unplayed = episode(show, "Unplayed");
        show.setItems(new ArrayList<>(Arrays.asList(played, playedFavorite, unplayed)));
        DBWriter.addNewFeed(context, show).get();
        Feed other = feed("https://o.example/feed.xml", "Other", true, false);
        FeedItem otherPlayed = episode(other, "OtherPlayed");
        other.setItems(new ArrayList<>(Arrays.asList(otherPlayed)));
        DBWriter.addNewFeed(context, other).get();

        File playedFile = markDownloaded(played, 1);
        File favoriteFile = markDownloaded(playedFavorite, 1);
        File unplayedFile = markDownloaded(unplayed, 1);
        File otherFile = markDownloaded(otherPlayed, 1);
        DBWriter.markItemPlayed(FeedItem.PLAYED, played.getId(), playedFavorite.getId(),
                otherPlayed.getId()).get();
        DBWriter.addFavoriteItem(playedFavorite).get();

        int deleted = DBWriter.deleteStorageBucket(context, show.getId(), true).get();

        assertEquals(1, deleted);
        assertFalse(playedFile.exists());
        assertTrue(favoriteFile.exists());
        assertTrue(unplayedFile.exists());
        assertTrue(otherFile.exists());
        assertEquals(3, DBReader.getDownloadedMediaWithFeed().size());
    }

    @Test
    public void deleteAllOfOneFeedIncludesFavourites() throws Exception {
        Feed show = feed("https://s.example/feed.xml", "Show", true, false);
        FeedItem favorite = episode(show, "Favorite");
        FeedItem unplayed = episode(show, "Unplayed");
        show.setItems(new ArrayList<>(Arrays.asList(favorite, unplayed)));
        DBWriter.addNewFeed(context, show).get();
        File favoriteFile = markDownloaded(favorite, 1);
        File unplayedFile = markDownloaded(unplayed, 1);
        DBWriter.addFavoriteItem(favorite).get();

        int deleted = DBWriter.deleteStorageBucket(context, show.getId(), false).get();

        assertEquals(2, deleted);
        assertFalse(favoriteFile.exists());
        assertFalse(unplayedFile.exists());
        assertTrue(DBReader.getDownloadedMediaWithFeed().isEmpty());
    }

    // ------------------------------------------------------- helpers

    private static long mediaId(FeedItem item) {
        FeedMedia media = item.getMedia();
        assertNotNull(media);
        return media.getId();
    }

    private File markDownloaded(FeedItem item, int bytes) throws Exception {
        FeedMedia media = item.getMedia();
        assertNotNull(media);
        File file = new File(context.getFilesDir(), item.getFeed().getTitle() + "-" + item.getTitle() + ".mp3");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[bytes]);
        } catch (IOException e) {
            throw new IOException("could not create " + file, e);
        }
        media.setFile_url(file.getAbsolutePath());
        media.setDownloaded(true);
        DBWriter.setFeedMediaDownloadState(media).get();
        return file;
    }

    private static Feed feed(String url, String title, boolean subscribed, boolean audiobook) {
        Feed feed = new Feed(url, null, title);
        feed.setSubscribed(subscribed);
        FeedPreferences preferences = new FeedPreferences(0, true, true,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                null, null, new FeedFilter(), FeedPreferences.SPEED_USE_GLOBAL,
                0, 0, false, new HashSet<>(), false, 0, false, false);
        preferences.setAudiobook(audiobook);
        feed.setPreferences(preferences);
        return feed;
    }

    private static FeedItem episode(Feed feed, String title) {
        FeedItem item = new FeedItem(0, title, "guid-" + title, "https://w.example/" + title,
                new Date(1_600_000_000_000L), FeedItem.UNPLAYED, feed);
        item.setMedia(new FeedMedia(item, "https://w.example/" + title + ".mp3", 1000L, "audio/mpeg"));
        return item;
    }
}
