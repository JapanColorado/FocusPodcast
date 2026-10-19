package dev.russell.focuspodcast.core.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import dev.russell.focuspodcast.model.feed.Feed;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.SortOrder;

/** {@link SortOrder#TRACK_ORDER} through {@link FeedItemPermutors}. Plain JUnit. */
public class TrackOrderSortTest {

    private static final Feed LOCAL = new Feed(Feed.PREFIX_LOCAL_FOLDER + "content://tree", null, "Book");
    private static final Feed REMOTE = new Feed("https://example.com/feed.xml", null, "Podcast");

    private static long nextId = 1;

    private static FeedItem item(Feed feed, String path, String title, int disc, int track) {
        FeedItem item = new FeedItem(nextId++, title, "guid" + nextId, path, new Date(0), FeedItem.UNPLAYED, feed);
        item.setDiscNumber(disc);
        item.setTrackNumber(track);
        return item;
    }

    private static List<String> sortedLinks(SortOrder order, FeedItem... items) {
        List<FeedItem> list = new ArrayList<>(Arrays.asList(items));
        FeedItemPermutors.getPermutor(order).reorder(list);
        List<String> links = new ArrayList<>();
        for (FeedItem item : list) {
            links.add(item.getLink());
        }
        return links;
    }

    @Test
    public void untaggedFilesFollowTheNaturalPathOrder() {
        assertEquals(Arrays.asList("Ch 1.mp3", "Ch 2.mp3", "Ch 10.mp3"), sortedLinks(SortOrder.TRACK_ORDER,
                item(LOCAL, "Ch 10.mp3", "x", 0, 0),
                item(LOCAL, "Ch 2.mp3", "y", 0, 0),
                item(LOCAL, "Ch 1.mp3", "z", 0, 0)));
    }

    @Test
    public void trackNumbersBeatFileNames() {
        assertEquals(Arrays.asList("b.mp3", "a.mp3", "c.mp3"), sortedLinks(SortOrder.TRACK_ORDER,
                item(LOCAL, "a.mp3", "a", 0, 2),
                item(LOCAL, "c.mp3", "c", 0, 3),
                item(LOCAL, "b.mp3", "b", 0, 1)));
    }

    @Test
    public void discNumberComesFirst() {
        assertEquals(Arrays.asList("x/2.mp3", "x/1.mp3"), sortedLinks(SortOrder.TRACK_ORDER,
                item(LOCAL, "x/1.mp3", "a", 2, 1),
                item(LOCAL, "x/2.mp3", "b", 1, 5)));
    }

    @Test
    public void subfoldersKeepPerDiscTrackNumbersApart() {
        assertEquals(Arrays.asList("CD1/01.mp3", "CD1/02.mp3", "CD2/01.mp3", "CD2/02.mp3"),
                sortedLinks(SortOrder.TRACK_ORDER,
                        item(LOCAL, "CD2/01.mp3", "a", 0, 1),
                        item(LOCAL, "CD1/02.mp3", "b", 0, 2),
                        item(LOCAL, "CD2/02.mp3", "c", 0, 2),
                        item(LOCAL, "CD1/01.mp3", "d", 0, 1)));
    }

    @Test
    public void unknownTrackNumbersSortAfterKnownOnes() {
        assertEquals(Arrays.asList("b.mp3", "a.mp3"), sortedLinks(SortOrder.TRACK_ORDER,
                item(LOCAL, "a.mp3", "a", 0, 0),
                item(LOCAL, "b.mp3", "b", 0, 7)));
    }

    @Test
    public void reversedIsTheExactReverse() {
        assertEquals(Arrays.asList("Ch 10.mp3", "Ch 2.mp3", "Ch 1.mp3"), sortedLinks(SortOrder.TRACK_ORDER_REVERSED,
                item(LOCAL, "Ch 2.mp3", "y", 0, 0),
                item(LOCAL, "Ch 10.mp3", "x", 0, 0),
                item(LOCAL, "Ch 1.mp3", "z", 0, 0)));
    }

    @Test
    public void remoteItemsUseTheirTitle() {
        assertEquals(Arrays.asList("https://e/1", "https://e/2"), sortedLinks(SortOrder.TRACK_ORDER,
                item(REMOTE, "https://e/2", "Episode 10", 0, 0),
                item(REMOTE, "https://e/1", "Episode 9", 0, 0)));
    }

    @Test
    public void trackOrderPairFollowsTheSortDialogConventions() {
        assertEquals(SortOrder.ASC_INDEX, SortOrder.TRACK_ORDER.code - SortOrder.TRACK_ORDER.ascBaseCode);
        assertEquals(SortOrder.DESC_INDEX,
                SortOrder.TRACK_ORDER_REVERSED.code - SortOrder.TRACK_ORDER_REVERSED.ascBaseCode);
        assertEquals(SortOrder.Scope.INTRA_FEED, SortOrder.TRACK_ORDER.scope);
    }
}
