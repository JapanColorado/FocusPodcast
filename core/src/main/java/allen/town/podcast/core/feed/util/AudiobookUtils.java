package allen.town.podcast.core.feed.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import allen.town.podcast.core.util.FeedItemPermutors;
import allen.town.podcast.model.feed.AudiobookProgress;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.model.feed.SortOrder;

/**
 * Pure logic for audiobook feeds (no database, no Android): the order the tracks of a book play
 * in, which track follows the current one, and the progress through the book.
 */
public final class AudiobookUtils {

    private AudiobookUtils() {
    }

    /**
     * The order an audiobook plays in: the feed's own intra-feed sort order if the user chose one,
     * else {@link SortOrder#TRACK_ORDER}.
     */
    @NonNull
    public static SortOrder playbackOrder(@Nullable Feed feed) {
        SortOrder order = feed != null ? feed.getSortOrder() : null;
        return order != null ? order : SortOrder.TRACK_ORDER;
    }

    /**
     * Returns the track that follows {@code currentItemId} when {@code items} (all tracks of one
     * book, in any order) are sorted by {@code order}, regardless of played state. Null when the
     * current item is the last one, is not among {@code items}, or the next track has no media.
     */
    @Nullable
    public static FeedItem nextTrack(@NonNull List<FeedItem> items, @NonNull SortOrder order,
                                     long currentItemId) {
        List<FeedItem> sorted = new ArrayList<>(items);
        FeedItemPermutors.getPermutor(order).reorder(sorted);
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getId() == currentItemId) {
                if (i + 1 >= sorted.size()) {
                    return null;
                }
                FeedItem next = sorted.get(i + 1);
                return next.getMedia() != null ? next : null;
            }
        }
        return null;
    }

    /**
     * Progress through one book. {@code items} are all its tracks, with media loaded. Played tracks
     * count with their whole duration, unplayed ones with their saved position. The resume track is
     * the unplayed track with a saved position that was played most recently, else the first
     * unplayed track in track order. The book's last-played time is the latest of its tracks', so
     * the Audiobooks page can sort by it without another query.
     */
    @NonNull
    public static AudiobookProgress computeProgress(long feedId, @NonNull List<FeedItem> items) {
        long total = 0;
        long listened = 0;
        int played = 0;
        boolean started = false;
        long lastPlayed = 0;
        FeedItem inProgress = null;
        long inProgressLastPlayed = Long.MIN_VALUE;
        for (FeedItem item : items) {
            FeedMedia media = item.getMedia();
            long duration = media != null ? Math.max(0, media.getDuration()) : 0;
            total += duration;
            if (media != null) {
                lastPlayed = Math.max(lastPlayed, media.getLastPlayedTime());
                started |= media.getPosition() > 0;
            }
            started |= item.isPlayed();
            if (item.isPlayed()) {
                played++;
                listened += duration;
            } else if (media != null) {
                long position = Math.max(0, media.getPosition());
                listened += duration > 0 ? Math.min(position, duration) : 0;
                if (position > 0 && media.getLastPlayedTime() > inProgressLastPlayed) {
                    inProgress = item;
                    inProgressLastPlayed = media.getLastPlayedTime();
                }
            }
        }
        FeedItem resume = inProgress;
        if (resume == null) {
            List<FeedItem> sorted = new ArrayList<>(items);
            sorted.sort(FeedItemPermutors.TRACK_ORDER_COMPARATOR);
            for (FeedItem item : sorted) {
                if (!item.isPlayed() && item.getMedia() != null) {
                    resume = item;
                    break;
                }
            }
        }
        return new AudiobookProgress(feedId, items.size(), played, total, listened, resume, started,
                lastPlayed);
    }
}
