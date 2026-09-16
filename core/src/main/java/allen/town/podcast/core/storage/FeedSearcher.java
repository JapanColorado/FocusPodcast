package allen.town.podcast.core.storage;

import android.util.Log;

import androidx.annotation.NonNull;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedItem;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Performs search on Feeds and FeedItems.
 */
public class FeedSearcher {
    private static final String TAG = "FeedSearcher";

    private FeedSearcher() {

    }

    @NonNull
    public static List<FeedItem> searchFeedItems(final String query, final long selectedFeed) {
        try {
            FutureTask<List<FeedItem>> itemSearchTask = DBTasks.searchFeedItems(selectedFeed, query);
            itemSearchTask.run();
            return itemSearchTask.get();
        } catch (ExecutionException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Documented behaviour of this @NonNull method: an empty result set on failure.
            Log.e(TAG, "Feed item search failed for query: " + query, e);
            return Collections.emptyList();
        }
    }

    @NonNull
    public static List<Feed> searchFeeds(final String query) {
        try {
            FutureTask<List<Feed>> feedSearchTask = DBTasks.searchFeeds(query);
            feedSearchTask.run();
            return feedSearchTask.get();
        } catch (ExecutionException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Documented behaviour of this @NonNull method: an empty result set on failure.
            Log.e(TAG, "Feed search failed for query: " + query, e);
            return Collections.emptyList();
        }
    }
}
