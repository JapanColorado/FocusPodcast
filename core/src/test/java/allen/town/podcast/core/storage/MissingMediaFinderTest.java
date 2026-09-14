package allen.town.podcast.core.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedMedia;

/**
 * Tests for {@link DBTasks#findMissingMediaFiles(List, DBTasks.MediaFileChecker)}, the pure
 * selection logic behind the missing-media reconciliation.
 * <p>
 * Plain JUnit: {@link Feed#isLocalFeed()} goes through {@code android.text.TextUtils}, which is
 * a stub in JVM unit tests, so the test feeds override it directly.
 */
public class MissingMediaFinderTest {

    private static final DBTasks.MediaFileChecker NOTHING_EXISTS = media -> false;
    private static final DBTasks.MediaFileChecker EVERYTHING_EXISTS = media -> true;

    private static FeedItem item(Feed feed, String fileUrl, boolean downloaded) {
        FeedItem item = new FeedItem();
        item.setFeed(feed);
        FeedMedia media = new FeedMedia(fileUrl.hashCode(), item, 0, 0, 0, "audio/mpeg",
                fileUrl, "http://example.com/" + fileUrl.hashCode() + ".mp3", downloaded, null, 0, 0);
        item.setMedia(media);
        return item;
    }

    private static Feed feed(final boolean local) {
        return new Feed("http://example.com/feed.xml", null) {
            @Override
            public boolean isLocalFeed() {
                return local;
            }
        };
    }

    @Test
    public void emptyListYieldsNothing() {
        assertTrue(DBTasks.findMissingMediaFiles(Collections.emptyList(), NOTHING_EXISTS).isEmpty());
    }

    @Test
    public void presentFilesAreNotReported() {
        FeedItem item = item(feed(false), "/data/a.mp3", true);
        assertTrue(DBTasks.findMissingMediaFiles(Collections.singletonList(item), EVERYTHING_EXISTS).isEmpty());
    }

    @Test
    public void missingDownloadedFileIsReported() {
        FeedItem item = item(feed(false), "/data/a.mp3", true);
        List<FeedMedia> missing = DBTasks.findMissingMediaFiles(Collections.singletonList(item), NOTHING_EXISTS);
        assertEquals(1, missing.size());
        assertSame(item.getMedia(), missing.get(0));
    }

    @Test
    public void notDownloadedMediaIsSkipped() {
        // a partially downloaded file has a file_url but is not flagged as downloaded
        FeedItem item = item(feed(false), "/data/partial.mp3", false);
        assertTrue(DBTasks.findMissingMediaFiles(Collections.singletonList(item), NOTHING_EXISTS).isEmpty());
    }

    @Test
    public void itemsWithoutMediaAreSkipped() {
        FeedItem item = new FeedItem();
        item.setFeed(feed(false));
        assertTrue(DBTasks.findMissingMediaFiles(Collections.singletonList(item), NOTHING_EXISTS).isEmpty());
    }

    @Test
    public void localFeedItemsAreSkipped() {
        FeedItem item = item(feed(true), "content://tree/primary%3APodcasts/document/a.mp3", true);
        assertTrue(DBTasks.findMissingMediaFiles(Collections.singletonList(item), NOTHING_EXISTS).isEmpty());
    }

    @Test
    public void itemsWithoutFeedAreStillChecked() {
        FeedItem item = item(feed(false), "/data/a.mp3", true);
        item.setFeed(null);
        assertEquals(1, DBTasks.findMissingMediaFiles(Collections.singletonList(item), NOTHING_EXISTS).size());
    }

    @Test
    public void onlyMissingOnesAreReportedFromMixedList() {
        Feed feed = feed(false);
        FeedItem present = item(feed, "/data/present.mp3", true);
        FeedItem gone = item(feed, "/data/gone.mp3", true);
        FeedItem notDownloaded = item(feed, "/data/never.mp3", false);
        FeedItem local = item(feed(true), "content://tree/x/document/a.mp3", true);

        DBTasks.MediaFileChecker checker = media -> "/data/present.mp3".equals(media.getFile_url());
        List<FeedMedia> missing = DBTasks.findMissingMediaFiles(
                Arrays.asList(present, gone, notDownloaded, local), checker);

        assertEquals(1, missing.size());
        assertSame(gone.getMedia(), missing.get(0));
    }
}
