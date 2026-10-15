package allen.town.podcast.core.feed;

import static org.junit.Assert.assertEquals;

import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedMedia;
import org.junit.Test;

public class FeedMediaItemIdTest {

    @Test
    public void itemIdComesFromTheAttachedEpisode() {
        FeedItem item = new FeedItem();
        item.setId(42);
        FeedMedia media = new FeedMedia(item, "https://example.com/a.mp3", 0, "audio/mpeg");
        assertEquals(42, media.getItemId());
    }

    @Test
    public void detachedMediaHasNoItemId() {
        FeedMedia media = new FeedMedia(null, "https://example.com/a.mp3", 0, "audio/mpeg");
        assertEquals(0, media.getItemId());
    }
}
