package dev.russell.focuspodcast.core.service.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import dev.russell.focuspodcast.model.feed.FeedMedia;

/** The pieces of audio-only downloading that do not need a server. */
public class AudioOnlyDownloaderTest {

    @Test
    public void totalSizeComesFromContentRange() {
        assertEquals(24453096355L, AudioOnlyDownloader.totalSize("bytes 0-65535/24453096355"));
        assertEquals(-1, AudioOnlyDownloader.totalSize("bytes 0-65535/*"));
        assertEquals(-1, AudioOnlyDownloader.totalSize(null));
        assertEquals(-1, AudioOnlyDownloader.totalSize("garbage"));
    }

    @Test
    public void audioOnlyFilesAreRecognisedByName() {
        assertTrue(FeedMedia.isAudioOnlyFile("/media/Show/Tariffs.1234." + FeedMedia.AUDIO_ONLY_FILE_EXTENSION));
        // the name a clash with a download in progress produces (DownloadRequestCreator.findUnusedFile)
        assertTrue(FeedMedia.isAudioOnlyFile("/media/Show/Tariffs.1234.audio-1.m4a"));
        assertFalse(FeedMedia.isAudioOnlyFile("/media/Show/Tariffs.1234.mp4"));
        assertFalse(FeedMedia.isAudioOnlyFile("/media/Show/Tariffs.1234.m4a"));
        assertFalse(FeedMedia.isAudioOnlyFile("/media/Show/Tariffs.audio.m4a"));
        assertFalse(FeedMedia.isAudioOnlyFile(null));
    }
}
