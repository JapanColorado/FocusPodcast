package allen.town.podcast.core.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** {@link StorageUsage}: the pure aggregation and selection, plus measuring files on disk. */
public class StorageUsageTest {
    private static final long NOTHING_PLAYING = -1;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void sortsFeedsBySizeDescending() {
        List<StorageUsage.Entry> entries = Arrays.asList(
                new StorageUsage.Entry(1, "Small", 10, 100, false, false),
                new StorageUsage.Entry(2, "Big", 20, 500, false, false),
                new StorageUsage.Entry(3, "Middle", 30, 200, false, false),
                new StorageUsage.Entry(3, "Middle", 31, 200, false, false));

        StorageUsage.Overview overview = StorageUsage.aggregate(entries, true, NOTHING_PLAYING);

        assertEquals(3, overview.feeds.size());
        assertEquals(2, overview.feeds.get(0).bucketId);
        assertEquals(3, overview.feeds.get(1).bucketId);
        assertEquals(400, overview.feeds.get(1).bytes);
        assertEquals(2, overview.feeds.get(1).count);
        assertEquals(1, overview.feeds.get(2).bucketId);
        assertEquals(4, overview.count);
        assertEquals(1000, overview.bytes);
    }

    @Test
    public void equalSizesAreOrderedByTitleThenId() {
        List<StorageUsage.Entry> entries = Arrays.asList(
                new StorageUsage.Entry(5, "beta", 1, 100, false, false),
                new StorageUsage.Entry(4, "Alpha", 2, 100, false, false),
                new StorageUsage.Entry(3, "alpha", 3, 100, false, false));

        List<StorageUsage.FeedSummary> feeds =
                StorageUsage.aggregate(entries, true, NOTHING_PLAYING).feeds;

        assertEquals(3, feeds.get(0).bucketId);
        assertEquals(4, feeds.get(1).bucketId);
        assertEquals(5, feeds.get(2).bucketId);
    }

    @Test
    public void countsPlayedAndKeepsFavouritesWhenThePreferenceIsOn() {
        List<StorageUsage.Entry> entries = Arrays.asList(
                new StorageUsage.Entry(1, "Show", 1, 100, true, false),
                new StorageUsage.Entry(1, "Show", 2, 200, true, true),
                new StorageUsage.Entry(1, "Show", 3, 400, false, true),
                new StorageUsage.Entry(1, "Show", 4, 800, false, false));

        StorageUsage.FeedSummary keep = StorageUsage.aggregate(entries, true, NOTHING_PLAYING).feeds.get(0);
        assertEquals(1, keep.playedCount);
        assertEquals(100, keep.playedBytes);
        assertEquals(1, keep.keptFavoriteCount);
        assertEquals(4, keep.deletableCount);
        assertEquals(1500, keep.deletableBytes);
        assertEquals(2, keep.favoriteCount);

        StorageUsage.FeedSummary dontKeep = StorageUsage.aggregate(entries, false, NOTHING_PLAYING).feeds.get(0);
        assertEquals(2, dontKeep.playedCount);
        assertEquals(300, dontKeep.playedBytes);
        assertEquals(0, dontKeep.keptFavoriteCount);
    }

    @Test
    public void theEpisodeInThePlayerIsListedButNotDeletable() {
        List<StorageUsage.Entry> entries = Arrays.asList(
                new StorageUsage.Entry(1, "Show", 1, 100, true, false),
                new StorageUsage.Entry(1, "Show", 2, 200, true, false));

        StorageUsage.FeedSummary summary = StorageUsage.aggregate(entries, true, 2).feeds.get(0);

        assertEquals(2, summary.count);
        assertEquals(300, summary.bytes);
        assertEquals(1, summary.playedCount);
        assertEquals(100, summary.playedBytes);
        assertEquals(1, summary.deletableCount);
        assertTrue(summary.holdsPlayerEpisode);
        assertFalse(StorageUsage.aggregate(entries, true, NOTHING_PLAYING).feeds.get(0).holdsPlayerEpisode);
        assertEquals(Collections.singletonList(1L),
                StorageUsage.selectForDeletion(entries, 1, true, true, 2));
        assertEquals(Collections.singletonList(1L),
                StorageUsage.selectForDeletion(entries, 1, false, true, 2));
    }

    @Test
    public void zeroByteFilesAreCountedAsEpisodes() {
        List<StorageUsage.Entry> entries = Collections.singletonList(
                new StorageUsage.Entry(1, "Show", 1, 0, true, false));

        StorageUsage.Overview overview = StorageUsage.aggregate(entries, true, NOTHING_PLAYING);

        assertEquals(1, overview.count);
        assertEquals(0, overview.bytes);
        assertEquals(1, overview.feeds.get(0).count);
        assertEquals(1, overview.feeds.get(0).playedCount);
        assertEquals(Collections.singletonList(1L),
                StorageUsage.selectForDeletion(entries, 1, true, true, NOTHING_PLAYING));
    }

    @Test
    public void missingFilesAreNeitherCountedNorListedNorDeleted() {
        List<StorageUsage.Entry> entries = Arrays.asList(
                new StorageUsage.Entry(1, "Gone", 1, StorageUsage.MISSING, true, false),
                new StorageUsage.Entry(2, "Here", 2, StorageUsage.MISSING, true, false),
                new StorageUsage.Entry(2, "Here", 3, 50, true, false));

        StorageUsage.Overview overview = StorageUsage.aggregate(entries, true, NOTHING_PLAYING);

        assertEquals(1, overview.feeds.size());
        assertEquals(2, overview.feeds.get(0).bucketId);
        assertEquals(1, overview.feeds.get(0).count);
        assertEquals(50, overview.bytes);
        assertTrue(StorageUsage.selectForDeletion(entries, 1, false, false, NOTHING_PLAYING).isEmpty());
        assertEquals(Collections.singletonList(3L),
                StorageUsage.selectForDeletion(entries, 2, false, false, NOTHING_PLAYING));
    }

    @Test
    public void unsubscribedFeedsArePooledIntoOneRow() {
        List<StorageUsage.Entry> entries = Arrays.asList(
                new StorageUsage.Entry(7, "Preview A", "a.png", false, false, 1, 100, false, false),
                new StorageUsage.Entry(8, "Preview B", "b.png", false, false, 2, 100, true, false),
                new StorageUsage.Entry(9, "Subscribed", "c.png", true, true, 3, 150, false, false));

        List<StorageUsage.FeedSummary> feeds =
                StorageUsage.aggregate(entries, true, NOTHING_PLAYING).feeds;

        assertEquals(2, feeds.size());
        StorageUsage.FeedSummary pooled = feeds.get(0);
        assertTrue(pooled.isUnsubscribedBucket());
        assertNull(pooled.title);
        assertNull(pooled.imageUrl);
        assertEquals(2, pooled.count);
        assertEquals(1, pooled.playedCount);
        assertTrue(feeds.get(1).audiobook);
        assertFalse(feeds.get(1).isUnsubscribedBucket());
        assertEquals(Arrays.asList(1L, 2L), StorageUsage.selectForDeletion(entries,
                StorageUsage.UNSUBSCRIBED_BUCKET, false, true, NOTHING_PLAYING));
    }

    @Test
    public void deleteAllIncludesFavouritesAndPlayedOnlyFollowsThePreference() {
        List<StorageUsage.Entry> entries = Arrays.asList(
                new StorageUsage.Entry(1, "Show", 1, 10, true, true),
                new StorageUsage.Entry(1, "Show", 2, 10, false, true),
                new StorageUsage.Entry(1, "Show", 3, 10, true, false),
                new StorageUsage.Entry(2, "Other", 4, 10, true, false));

        assertEquals(Arrays.asList(1L, 2L, 3L),
                StorageUsage.selectForDeletion(entries, 1, false, true, NOTHING_PLAYING));
        assertEquals(Collections.singletonList(3L),
                StorageUsage.selectForDeletion(entries, 1, true, true, NOTHING_PLAYING));
        assertEquals(Arrays.asList(1L, 3L),
                StorageUsage.selectForDeletion(entries, 1, true, false, NOTHING_PLAYING));
    }

    @Test
    public void emptyInputGivesAnEmptyOverview() {
        StorageUsage.Overview overview =
                StorageUsage.aggregate(Collections.emptyList(), true, NOTHING_PLAYING);
        assertEquals(0, overview.count);
        assertEquals(0, overview.bytes);
        assertTrue(overview.feeds.isEmpty());
    }

    @Test
    public void measureReadsFileSizesAndSkipsContentUris() throws IOException {
        File present = tmp.newFile("present.mp3");
        try (FileOutputStream out = new FileOutputStream(present)) {
            out.write(new byte[1234]);
        }
        File empty = tmp.newFile("empty.mp3");
        File missing = new File(tmp.getRoot(), "missing.mp3");
        List<DownloadedMedia> rows = Arrays.asList(
                row(1, present.getAbsolutePath(), 99),
                row(2, empty.getAbsolutePath(), 99),
                row(3, missing.getAbsolutePath(), 99),
                row(4, null, 99),
                row(5, "content://com.example/doc/5", 99),
                row(6, tmp.getRoot().getAbsolutePath(), 99));

        List<StorageUsage.Entry> entries = StorageUsage.measure(rows);

        assertEquals(5, entries.size());
        assertEquals(1234, entries.get(0).fileSize);
        assertEquals(0, entries.get(1).fileSize);
        assertEquals(StorageUsage.MISSING, entries.get(2).fileSize);
        assertEquals(StorageUsage.MISSING, entries.get(3).fileSize);
        assertEquals("a directory is not a download", 6, entries.get(4).mediaId);
        assertEquals(StorageUsage.MISSING, entries.get(4).fileSize);
    }

    private static DownloadedMedia row(long mediaId, String path, long dbSize) {
        return new DownloadedMedia(mediaId, path, dbSize, false, false, 1, "Show", null, true, false);
    }
}
