package allen.town.podcast.core.service.download.handler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import allen.town.podcast.model.download.DownloadStatus;

/**
 * Tests for {@link MediaDownloadedHandler#isCompleteFile(boolean, long, long)}, the check that
 * decides whether a finished download may be flagged as downloaded.
 */
public class MediaFileVerifierTest {

    @Test
    public void missingFileIsNotComplete() {
        assertFalse(MediaDownloadedHandler.isCompleteFile(false, 0, 1000));
        assertFalse(MediaDownloadedHandler.isCompleteFile(false, 0, DownloadStatus.SIZE_UNKNOWN));
    }

    @Test
    public void emptyFileIsNotComplete() {
        assertFalse(MediaDownloadedHandler.isCompleteFile(true, 0, 1000));
        assertFalse(MediaDownloadedHandler.isCompleteFile(true, 0, DownloadStatus.SIZE_UNKNOWN));
    }

    @Test
    public void truncatedFileIsNotComplete() {
        assertFalse(MediaDownloadedHandler.isCompleteFile(true, 999, 1000));
        assertFalse(MediaDownloadedHandler.isCompleteFile(true, 1, 1000));
    }

    @Test
    public void exactSizeIsComplete() {
        assertTrue(MediaDownloadedHandler.isCompleteFile(true, 1000, 1000));
    }

    @Test
    public void largerThanAnnouncedIsComplete() {
        // servers may announce a compressed size
        assertTrue(MediaDownloadedHandler.isCompleteFile(true, 1500, 1000));
    }

    @Test
    public void unknownSizeAcceptsAnyNonEmptyFile() {
        assertTrue(MediaDownloadedHandler.isCompleteFile(true, 1, DownloadStatus.SIZE_UNKNOWN));
        assertTrue(MediaDownloadedHandler.isCompleteFile(true, 1, 0));
    }
}
