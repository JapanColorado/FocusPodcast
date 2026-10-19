package dev.russell.focuspodcast.core.storage;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedMedia;

/**
 * Picks the downloads that "Delete played" on the downloads screen removes: downloaded, played
 * episodes whose file the app owns. Favourites are kept, matching
 * {@link ExceptFavoriteCleanupAlgorithm}; files of local folder podcasts (content URIs) belong to
 * the user and are never offered; and the episode that is loaded in the player stays.
 */
public final class PlayedDownloadSelector {

    private PlayedDownloadSelector() {
    }

    @NonNull
    public static List<FeedItem> select(@NonNull List<FeedItem> items, long currentlyPlayingMediaId) {
        List<FeedItem> result = new ArrayList<>();
        for (FeedItem item : items) {
            FeedMedia media = item.getMedia();
            if (media == null
                    || !media.isDownloaded()
                    || !item.isPlayed()
                    || media.isContentUri()
                    || item.isTagged(FeedItem.TAG_FAVORITE)
                    || media.getId() == currentlyPlayingMediaId) {
                continue;
            }
            result.add(item);
        }
        return result;
    }
}
