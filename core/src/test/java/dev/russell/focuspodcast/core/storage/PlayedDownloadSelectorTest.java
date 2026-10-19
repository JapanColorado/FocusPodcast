package dev.russell.focuspodcast.core.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedMedia;

public class PlayedDownloadSelectorTest {

    private static long nextId = 1;

    @Test
    public void selectsDownloadedPlayedEpisodes() {
        FeedItem played = episode("played", true, true, "/files/a.mp3");
        FeedItem unplayed = episode("unplayed", true, false, "/files/b.mp3");
        List<FeedItem> selected = PlayedDownloadSelector.select(Arrays.asList(played, unplayed), 0);
        assertEquals(Collections.singletonList(played), selected);
    }

    @Test
    public void skipsEpisodesThatAreNotDownloaded() {
        FeedItem streamed = episode("streamed", false, true, null);
        assertTrue(PlayedDownloadSelector.select(Collections.singletonList(streamed), 0).isEmpty());
    }

    @Test
    public void skipsEpisodesWithoutMedia() {
        FeedItem noMedia = new FeedItem(nextId++, "note", "guid-note", "https://example.com/note",
                new Date(), FeedItem.PLAYED, null);
        assertTrue(PlayedDownloadSelector.select(Collections.singletonList(noMedia), 0).isEmpty());
    }

    @Test
    public void keepsFavorites() {
        FeedItem favorite = episode("favorite", true, true, "/files/c.mp3");
        favorite.addTag(FeedItem.TAG_FAVORITE);
        assertTrue(PlayedDownloadSelector.select(Collections.singletonList(favorite), 0).isEmpty());
    }

    @Test
    public void keepsLocalFolderFiles() {
        FeedItem local = episode("local", true, true, "content://com.example/tree/1/document/2");
        assertTrue(PlayedDownloadSelector.select(Collections.singletonList(local), 0).isEmpty());
    }

    @Test
    public void keepsTheEpisodeLoadedInThePlayer() {
        FeedItem playing = episode("playing", true, true, "/files/d.mp3");
        FeedItem other = episode("other", true, true, "/files/e.mp3");
        long playingMediaId = playing.getMedia().getId();
        List<FeedItem> selected = PlayedDownloadSelector.select(
                new ArrayList<>(Arrays.asList(playing, other)), playingMediaId);
        assertEquals(Collections.singletonList(other), selected);
    }

    private static FeedItem episode(String title, boolean downloaded, boolean played, String fileUrl) {
        FeedItem item = new FeedItem(nextId++, title, "guid-" + title, "https://example.com/" + title,
                new Date(), FeedItem.UNPLAYED, null);
        FeedMedia media = new FeedMedia(item, "https://example.com/" + title + ".mp3", 1000L, "audio/mpeg");
        media.setId(nextId++);
        media.setFile_url(fileUrl);
        media.setDownloaded(downloaded);
        item.setMedia(media);
        // after setDownloaded, which marks a new episode unplayed
        item.setPlayed(played);
        return item;
    }
}
