package allen.town.podcast.core.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The numbers behind the storage overview: how much space the downloads of each podcast take, and
 * which of them "Delete played downloads" / "Delete all downloads" would remove.
 *
 * <p>{@link #measure} reads the file sizes from disk; everything else is pure so it can be tested
 * on the JVM. Downloads whose file is missing count as nothing and are neither listed nor deleted
 * from here (the missing-file sweep fixes their rows). Downloads of podcasts the user is not
 * subscribed to (previews) are pooled into one {@link #UNSUBSCRIBED_BUCKET}. The episode that is
 * loaded in the player is listed but never offered for deletion.
 */
public final class StorageUsage {

    /** Bucket id of all downloads that belong to podcasts the user is not subscribed to. */
    public static final long UNSUBSCRIBED_BUCKET = -1;
    /** {@link Entry#fileSize} of a download whose file is not on disk. */
    public static final long MISSING = -1;

    private StorageUsage() {
    }

    /** One download with its measured size. */
    public static final class Entry {
        public final long feedId;
        @Nullable
        public final String feedTitle;
        @Nullable
        public final String feedImageUrl;
        public final boolean feedSubscribed;
        public final boolean feedAudiobook;
        public final long mediaId;
        /** Bytes on disk, or {@link #MISSING}. */
        public final long fileSize;
        public final boolean played;
        public final boolean favorite;

        public Entry(long feedId, @Nullable String feedTitle, @Nullable String feedImageUrl,
                     boolean feedSubscribed, boolean feedAudiobook, long mediaId, long fileSize,
                     boolean played, boolean favorite) {
            this.feedId = feedId;
            this.feedTitle = feedTitle;
            this.feedImageUrl = feedImageUrl;
            this.feedSubscribed = feedSubscribed;
            this.feedAudiobook = feedAudiobook;
            this.mediaId = mediaId;
            this.fileSize = fileSize;
            this.played = played;
            this.favorite = favorite;
        }

        /** Shorthand for a download of a subscribed podcast without cover. */
        public Entry(long feedId, @Nullable String feedTitle, long mediaId, long fileSize,
                     boolean played, boolean favorite) {
            this(feedId, feedTitle, null, true, false, mediaId, fileSize, played, favorite);
        }

        long bucket() {
            return feedSubscribed ? feedId : UNSUBSCRIBED_BUCKET;
        }

        boolean isPresent() {
            return fileSize != MISSING;
        }
    }

    /** What one podcast (or the pool of unsubscribed ones) holds on disk. */
    public static final class FeedSummary {
        /** The feed id, or {@link #UNSUBSCRIBED_BUCKET}. */
        public final long bucketId;
        /** Null for the unsubscribed bucket, which the UI names itself. */
        @Nullable
        public final String title;
        @Nullable
        public final String imageUrl;
        public final boolean audiobook;
        public final int count;
        public final long bytes;
        /** What "Delete played downloads" removes. */
        public final int playedCount;
        public final long playedBytes;
        /** Played favourites that "Delete played downloads" keeps because of the preference. */
        public final int keptFavoriteCount;
        /** What "Delete all downloads" removes: everything except the episode in the player. */
        public final int deletableCount;
        public final long deletableBytes;
        /** Favourites among {@link #deletableCount}. */
        public final int favoriteCount;
        /**
         * Whether the episode loaded in the player is among these downloads; it is counted in
         * {@link #count} but left out of every delete action.
         */
        public final boolean holdsPlayerEpisode;

        FeedSummary(long bucketId, @Nullable String title, @Nullable String imageUrl,
                    boolean audiobook, int count, long bytes, int playedCount, long playedBytes,
                    int keptFavoriteCount, int deletableCount, long deletableBytes,
                    int favoriteCount, boolean holdsPlayerEpisode) {
            this.bucketId = bucketId;
            this.title = title;
            this.imageUrl = imageUrl;
            this.audiobook = audiobook;
            this.count = count;
            this.bytes = bytes;
            this.playedCount = playedCount;
            this.playedBytes = playedBytes;
            this.keptFavoriteCount = keptFavoriteCount;
            this.deletableCount = deletableCount;
            this.deletableBytes = deletableBytes;
            this.favoriteCount = favoriteCount;
            this.holdsPlayerEpisode = holdsPlayerEpisode;
        }

        public boolean isUnsubscribedBucket() {
            return bucketId == UNSUBSCRIBED_BUCKET;
        }
    }

    /** All downloads: the totals and the per-podcast rows, largest first. */
    public static final class Overview {
        public final int count;
        public final long bytes;
        @NonNull
        public final List<FeedSummary> feeds;

        Overview(int count, long bytes, @NonNull List<FeedSummary> feeds) {
            this.count = count;
            this.bytes = bytes;
            this.feeds = feeds;
        }
    }

    /**
     * Measures the files of the given downloads. Local folder files (content URIs) are the user's
     * and not counted at all; a path that is not a file on disk becomes {@link #MISSING}. Does
     * disk I/O, so call it off the main thread.
     */
    @NonNull
    public static List<Entry> measure(@NonNull List<DownloadedMedia> media) {
        List<Entry> entries = new ArrayList<>(media.size());
        for (DownloadedMedia m : media) {
            if (m.isContentUri()) {
                continue;
            }
            long size = MISSING;
            if (m.fileUrl != null) {
                File file = new File(m.fileUrl);
                if (file.isFile()) {
                    size = file.length();
                }
            }
            entries.add(new Entry(m.feedId, m.feedTitle, m.feedImageUrl, m.feedSubscribed,
                    m.feedAudiobook, m.mediaId, size, m.played, m.favorite));
        }
        return entries;
    }

    /**
     * Groups the downloads by podcast and sorts the podcasts by size, largest first (ties by
     * title, then id, so the order is stable).
     *
     * @param keepFavorites            whether "Delete played downloads" keeps favourites (the
     *                                 "favourites keep episodes" preference)
     * @param currentlyPlayingMediaId  the media loaded in the player, never offered for deletion
     */
    @NonNull
    public static Overview aggregate(@NonNull List<Entry> entries, boolean keepFavorites,
                                     long currentlyPlayingMediaId) {
        Map<Long, Builder> buckets = new LinkedHashMap<>();
        int count = 0;
        long bytes = 0;
        for (Entry entry : entries) {
            if (!entry.isPresent()) {
                continue;
            }
            Builder builder = buckets.get(entry.bucket());
            if (builder == null) {
                builder = new Builder(entry);
                buckets.put(entry.bucket(), builder);
            }
            builder.add(entry, keepFavorites, currentlyPlayingMediaId);
            count++;
            bytes += entry.fileSize;
        }
        List<FeedSummary> feeds = new ArrayList<>(buckets.size());
        for (Builder builder : buckets.values()) {
            feeds.add(builder.build());
        }
        Collections.sort(feeds, (a, b) -> {
            int bySize = Long.compare(b.bytes, a.bytes);
            if (bySize != 0) {
                return bySize;
            }
            int byTitle = sortTitle(a).compareTo(sortTitle(b));
            if (byTitle != 0) {
                return byTitle;
            }
            return Long.compare(a.bucketId, b.bucketId);
        });
        return new Overview(count, bytes, feeds);
    }

    /**
     * The media ids that a delete action on one bucket removes, using exactly the rules the
     * counts of {@link #aggregate} were made with.
     *
     * @param playedOnly    true for "Delete played downloads", false for "Delete all downloads"
     * @param keepFavorites only applies to {@code playedOnly}; deleting everything includes
     *                      favourites
     */
    @NonNull
    public static List<Long> selectForDeletion(@NonNull List<Entry> entries, long bucketId,
                                               boolean playedOnly, boolean keepFavorites,
                                               long currentlyPlayingMediaId) {
        List<Long> ids = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.isPresent() || entry.bucket() != bucketId) {
                continue;
            }
            boolean deletable = playedOnly
                    ? isDeletablePlayed(entry, keepFavorites, currentlyPlayingMediaId)
                    : entry.mediaId != currentlyPlayingMediaId;
            if (deletable) {
                ids.add(entry.mediaId);
            }
        }
        return ids;
    }

    private static boolean isDeletablePlayed(Entry entry, boolean keepFavorites,
                                             long currentlyPlayingMediaId) {
        return entry.played
                && entry.mediaId != currentlyPlayingMediaId
                && !(keepFavorites && entry.favorite);
    }

    private static String sortTitle(FeedSummary summary) {
        return summary.title == null ? "" : summary.title.toLowerCase(Locale.ROOT);
    }

    private static final class Builder {
        private final long bucketId;
        @Nullable
        private final String title;
        @Nullable
        private final String imageUrl;
        private final boolean audiobook;
        private int count;
        private long bytes;
        private int playedCount;
        private long playedBytes;
        private int keptFavoriteCount;
        private int deletableCount;
        private long deletableBytes;
        private int favoriteCount;
        private boolean holdsPlayerEpisode;

        Builder(Entry first) {
            bucketId = first.bucket();
            boolean pooled = bucketId == UNSUBSCRIBED_BUCKET;
            title = pooled ? null : first.feedTitle;
            imageUrl = pooled ? null : first.feedImageUrl;
            audiobook = !pooled && first.feedAudiobook;
        }

        void add(Entry entry, boolean keepFavorites, long currentlyPlayingMediaId) {
            count++;
            bytes += entry.fileSize;
            if (isDeletablePlayed(entry, keepFavorites, currentlyPlayingMediaId)) {
                playedCount++;
                playedBytes += entry.fileSize;
            } else if (entry.played && entry.favorite && keepFavorites
                    && entry.mediaId != currentlyPlayingMediaId) {
                keptFavoriteCount++;
            }
            if (entry.mediaId != currentlyPlayingMediaId) {
                deletableCount++;
                deletableBytes += entry.fileSize;
                if (entry.favorite) {
                    favoriteCount++;
                }
            } else {
                holdsPlayerEpisode = true;
            }
        }

        FeedSummary build() {
            return new FeedSummary(bucketId, title, imageUrl, audiobook, count, bytes,
                    playedCount, playedBytes, keptFavoriteCount, deletableCount, deletableBytes,
                    favoriteCount, holdsPlayerEpisode);
        }
    }
}
