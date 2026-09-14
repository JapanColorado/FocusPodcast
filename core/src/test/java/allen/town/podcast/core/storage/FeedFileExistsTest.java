package allen.town.podcast.core.storage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedMedia;

/**
 * Tests for {@link allen.town.podcast.model.feed.FeedFile#fileExists()} and
 * {@link allen.town.podcast.model.feed.FeedFile#isContentUri()}.
 */
public class FeedFileExistsTest {

    private static FeedMedia media(String fileUrl, boolean downloaded) {
        return new FeedMedia(1, new FeedItem(), 0, 0, 0, "audio/mpeg", fileUrl,
                "http://example.com/episode.mp3", downloaded, null, 0, 0);
    }

    @Test
    public void nullFileUrlDoesNotExist() {
        FeedMedia media = media(null, true);
        assertFalse(media.isContentUri());
        assertFalse(media.fileExists());
        // the constructor coerces the flag when there is no file_url
        assertFalse(media.isDownloaded());
    }

    @Test
    public void contentUriIsAssumedToExist() {
        FeedMedia media = media("content://com.android.externalstorage.documents/tree/primary%3APodcasts", false);
        assertTrue(media.isContentUri());
        assertTrue(media.fileExists());
    }

    @Test
    public void missingPathDoesNotExist() {
        FeedMedia media = media("/definitely/not/here/episode.mp3", true);
        assertFalse(media.isContentUri());
        assertFalse(media.fileExists());
    }

    @Test
    public void existingPathExists() throws IOException {
        File tmp = File.createTempFile("feedfile", ".mp3");
        try {
            FeedMedia media = media(tmp.getAbsolutePath(), true);
            assertFalse(media.isContentUri());
            assertTrue(media.fileExists());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    @Test
    public void clearingFileUrlClearsDownloadedFlag() {
        FeedMedia media = media("/tmp/episode.mp3", true);
        assertTrue(media.isDownloaded());
        media.setFile_url(null);
        assertFalse(media.isDownloaded());
    }
}
