package dev.russell.focuspodcast.core.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import dev.russell.focuspodcast.core.util.comparator.NaturalOrderComparator;
import dev.russell.focuspodcast.model.feed.Feed;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.SortOrder;

/**
 * Sorts {@link FeedItem} lists according to a rule.
 */
public class FeedItemPermutors {

    /** Seed of {@link #getStablePermutor}'s shuffle; lives as long as the process. */
    private static volatile long shuffleSeed = new Random().nextLong();

    /**
     * Returns a Permutor that sorts a list appropriate to the given sort order.
     *
     * @return Permutor that sorts a list appropriate to the given sort order.
     */
    @NonNull
    public static Permutor<FeedItem> getPermutor(@NonNull SortOrder sortOrder) {

        Comparator<FeedItem> comparator = null;
        Permutor<FeedItem> permutor = null;

        switch (sortOrder) {
            case EPISODE_TITLE_A_Z:
                comparator = (f1, f2) -> itemTitle(f1).compareTo(itemTitle(f2));
                break;
            case EPISODE_TITLE_Z_A:
                comparator = (f1, f2) -> itemTitle(f2).compareTo(itemTitle(f1));
                break;
            case DATE_OLD_NEW:
                comparator = (f1, f2) -> pubDate(f1).compareTo(pubDate(f2));
                break;
            case DATE_NEW_OLD:
                comparator = (f1, f2) -> pubDate(f2).compareTo(pubDate(f1));
                break;
            case DURATION_SHORT_LONG:
                comparator = (f1, f2) -> Integer.compare(duration(f1), duration(f2));
                break;
            case DURATION_LONG_SHORT:
                comparator = (f1, f2) -> Integer.compare(duration(f2), duration(f1));
                break;
            case TRACK_ORDER:
                comparator = TRACK_ORDER_COMPARATOR;
                break;
            case TRACK_ORDER_REVERSED:
                comparator = TRACK_ORDER_COMPARATOR.reversed();
                break;
            case FEED_TITLE_A_Z:
                comparator = (f1, f2) -> feedTitle(f1).compareTo(feedTitle(f2));
                break;
            case FEED_TITLE_Z_A:
                comparator = (f1, f2) -> feedTitle(f2).compareTo(feedTitle(f1));
                break;
            case RANDOM:
            case RANDOM_FAKE:
                permutor = Collections::shuffle;
                break;
            case SMART_SHUFFLE_OLD_NEW:
                permutor = (queue) -> smartShuffle(queue, true);
                break;
            case SMART_SHUFFLE_NEW_OLD:
                permutor = (queue) -> smartShuffle(queue, false);
                break;
        }

        if (comparator != null) {
            final Comparator<FeedItem> comparator2 = comparator;
            permutor = (queue) -> Collections.sort(queue, comparator2);
        }
        return permutor;
    }

    /**
     * Like {@link #getPermutor}, but {@link SortOrder#RANDOM} gives the same order every time it
     * is applied (until {@link #reshuffle}), so a list that reloads often does not jump around
     * and playback can follow the order that is shown. New items land at a random position
     * without moving the others.
     */
    @NonNull
    public static Permutor<FeedItem> getStablePermutor(@NonNull SortOrder sortOrder) {
        if (sortOrder == SortOrder.RANDOM || sortOrder == SortOrder.RANDOM_FAKE) {
            final long seed = shuffleSeed;
            return list -> Collections.sort(list, (f1, f2) -> {
                int result = Long.compare(mix(f1.getId() ^ seed), mix(f2.getId() ^ seed));
                return result != 0 ? result : Long.compare(f1.getId(), f2.getId());
            });
        }
        return getPermutor(sortOrder);
    }

    /** Picks a new order for {@link #getStablePermutor}'s shuffle. */
    public static void reshuffle() {
        shuffleSeed = new Random().nextLong();
    }

    /** SplitMix64 finaliser: spreads consecutive ids over the whole long range. */
    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Book order, see {@link SortOrder#TRACK_ORDER}: disc number, then the folder the file is in,
     * then track number, then the natural order of the relative path, then the id. An unknown (0)
     * disc or track number sorts after every known one, which keeps the order transitive when only
     * some files are tagged. The folder step keeps "CD1/" and "CD2/" apart when the files carry
     * per-disc track numbers but no disc tag. Items that are not from a local feed have no
     * relative path and use their title instead.
     */
    public static final Comparator<FeedItem> TRACK_ORDER_COMPARATOR = (f1, f2) -> {
        int result = Integer.compare(knownFirst(f1.getDiscNumber()), knownFirst(f2.getDiscNumber()));
        if (result != 0) {
            return result;
        }
        String path1 = trackPath(f1);
        String path2 = trackPath(f2);
        result = NaturalOrderComparator.INSTANCE.compare(parentOf(f1, path1), parentOf(f2, path2));
        if (result != 0) {
            return result;
        }
        result = Integer.compare(knownFirst(f1.getTrackNumber()), knownFirst(f2.getTrackNumber()));
        if (result != 0) {
            return result;
        }
        result = NaturalOrderComparator.INSTANCE.compare(path1, path2);
        if (result != 0) {
            return result;
        }
        return Long.compare(f1.getId(), f2.getId());
    };

    private static int knownFirst(int number) {
        return number > 0 ? number : Integer.MAX_VALUE;
    }

    private static boolean isLocal(@NonNull FeedItem item) {
        Feed feed = item.getFeed();
        return feed != null && feed.isLocalFeed();
    }

    /** The relative path of a local file (stored as the item's link), else the title. */
    @NonNull
    private static String trackPath(@NonNull FeedItem item) {
        String value = isLocal(item) ? item.getLink() : item.getTitle();
        if (value == null) {
            value = item.getTitle();
        }
        return value != null ? value : "";
    }

    @NonNull
    private static String parentOf(@NonNull FeedItem item, @NonNull String path) {
        if (!isLocal(item)) {
            return "";
        }
        int slash = path.lastIndexOf('/');
        return slash > 0 ? path.substring(0, slash) : "";
    }

    // Null-safe accessors

    @NonNull
    private static Date pubDate(@Nullable FeedItem item) {
        return (item != null && item.getPubDate() != null) ? item.getPubDate() : new Date(0);
    }

    @NonNull
    private static String itemTitle(@Nullable FeedItem item) {
        return (item != null && item.getTitle() != null) ? item.getTitle().toLowerCase(Locale.getDefault()) : "";
    }

    private static int duration(@Nullable FeedItem item) {
        return (item != null && item.getMedia() != null) ? item.getMedia().getDuration() : 0;
    }

    @NonNull
    private static String itemLink(@Nullable FeedItem item) {
        return (item != null && item.getLink() != null)
                ? item.getLink().toLowerCase(Locale.getDefault()) : "";
    }

    @NonNull
    private static String feedTitle(@Nullable FeedItem item) {
        return (item != null && item.getFeed() != null && item.getFeed().getTitle() != null)
                ? item.getFeed().getTitle().toLowerCase(Locale.getDefault()) : "";
    }

    /**
     * Implements a reordering by pubdate that avoids consecutive episodes from the same feed in
     * the queue.
     *
     * A listener might want to hear episodes from any given feed in pubdate order, but would
     * prefer a more balanced ordering that avoids having to listen to clusters of consecutive
     * episodes from the same feed. This is what "Smart Shuffle" tries to accomplish.
     *
     * Assume the queue looks like this: `ABCDDEEEEEEEEEE`.
     * This method first starts with a queue of the final size, where each slot is empty (null).
     * It takes the podcast with most episodes (`E`) and places the episodes spread out in the queue: `EE_E_EE_E_EE_EE`.
     * The podcast with the second-most number of episodes (`D`) is then
     * placed spread-out in the *available* slots: `EE_EDEE_EDEE_EE`.
     * This continues, until we end up with: `EEBEDEECEDEEAEE`.
     *
     * Note that episodes aren't strictly ordered in terms of pubdate, but episodes of each feed are.
     *
     * @param queue A (modifiable) list of FeedItem elements to be reordered.
     * @param ascending {@code true} to use ascending pubdate in the reordering;
     *                  {@code false} for descending.
     */
    private static void smartShuffle(List<FeedItem> queue, boolean ascending) {
        // Divide FeedItems into lists by feed
        Map<Long, List<FeedItem>> map = new HashMap<>();
        for (FeedItem item : queue) {
            Long id = item.getFeedId();
            if (!map.containsKey(id)) {
                map.put(id, new ArrayList<>());
            }
            map.get(id).add(item);
        }

        // Sort each individual list by PubDate (ascending/descending)
        Comparator<FeedItem> itemComparator = ascending
                ? (f1, f2) -> f1.getPubDate().compareTo(f2.getPubDate())
                : (f1, f2) -> f2.getPubDate().compareTo(f1.getPubDate());
        List<List<FeedItem>> feeds = new ArrayList<>();
        for (Map.Entry<Long, List<FeedItem>> mapEntry : map.entrySet()) {
            Collections.sort(mapEntry.getValue(), itemComparator);
            feeds.add(mapEntry.getValue());
        }

        ArrayList<Integer> emptySlots = new ArrayList<>();
        for (int i = 0; i < queue.size(); i++) {
            queue.set(i, null);
            emptySlots.add(i);
        }

        // Starting with the largest feed, place items spread out through the empty slots in the queue
        Collections.sort(feeds, (f1, f2) -> Integer.compare(f2.size(), f1.size()));
        for (List<FeedItem> feedItems : feeds) {
            double spread = (double) emptySlots.size() / (feedItems.size() + 1);
            Iterator<Integer> emptySlotIterator = emptySlots.iterator();
            int skipped = 0;
            int placed = 0;
            while (emptySlotIterator.hasNext()) {
                int nextEmptySlot = emptySlotIterator.next();
                skipped++;
                if (skipped >= spread * (placed + 1)) {
                    if (queue.get(nextEmptySlot) != null) {
                        throw new RuntimeException("Slot to be placed in not empty");
                    }
                    queue.set(nextEmptySlot, feedItems.get(placed));
                    emptySlotIterator.remove();
                    placed++;
                    if (placed == feedItems.size()) {
                        break;
                    }
                }
            }
        }
    }
}
