package dev.russell.yagi.core.storage;

import android.util.Log;

import androidx.annotation.NonNull;
import dev.russell.yagi.model.feed.EpisodeSearchOptions;
import dev.russell.yagi.model.feed.Feed;
import dev.russell.yagi.model.feed.FeedItem;

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

    /** Searches the episodes, filtered, scoped and sorted by {@code options}; empty on failure. */
    @NonNull
    public static List<FeedItem> searchFeedItems(final String query, final EpisodeSearchOptions options) {
        try {
            FutureTask<List<FeedItem>> itemSearchTask = DBTasks.searchFeedItems(query, options);
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

    /** How many episodes {@link #searchFeedItems(String, EpisodeSearchOptions)} finds; 0 on failure. */
    public static int countFeedItems(final String query, final EpisodeSearchOptions options) {
        try {
            FutureTask<Integer> countTask = DBTasks.countFeedItems(query, options);
            countTask.run();
            Integer count = countTask.get();
            return count != null ? count : 0;
        } catch (ExecutionException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.e(TAG, "Counting search results failed for query: " + query, e);
            return 0;
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
