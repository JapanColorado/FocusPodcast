package dev.russell.yagi.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import dev.russell.yagi.model.feed.Feed;
import dev.russell.yagi.model.feed.FeedItem;
import dev.russell.yagi.model.feed.SortOrder;

/** {@link SortOrder#RANDOM} through {@link FeedItemPermutors#getStablePermutor}. Plain JUnit. */
public class RandomSortTest {

    private static final Feed FEED = new Feed("https://example.com/feed.xml", null, "Podcast");

    private static List<FeedItem> items(int count) {
        List<FeedItem> items = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            items.add(new FeedItem(id, "Episode " + id, "guid" + id, "link" + id, new Date(id),
                    FeedItem.UNPLAYED, FEED));
        }
        return items;
    }

    private static List<Long> ids(List<FeedItem> items) {
        List<Long> ids = new ArrayList<>();
        for (FeedItem item : items) {
            ids.add(item.getId());
        }
        return ids;
    }

    private static List<Long> shuffled(List<FeedItem> items) {
        List<FeedItem> list = new ArrayList<>(items);
        FeedItemPermutors.getStablePermutor(SortOrder.RANDOM).reorder(list);
        return ids(list);
    }

    @Test
    public void shufflesAndKeepsEveryItem() {
        List<FeedItem> items = items(50);
        List<Long> result = shuffled(items);
        assertNotEquals(ids(items), result);
        List<Long> sorted = new ArrayList<>(result);
        Collections.sort(sorted);
        assertEquals(ids(items), sorted);
    }

    @Test
    public void sameOrderOnEveryReloadWhateverTheInputOrder() {
        List<FeedItem> items = items(50);
        List<Long> first = shuffled(items);
        Collections.reverse(items);
        assertEquals(first, shuffled(items));
    }

    @Test
    public void newItemDoesNotMoveTheOthers() {
        List<Long> before = shuffled(items(50));
        List<Long> after = shuffled(items(51));
        after.remove(Long.valueOf(51));
        assertEquals(before, after);
    }

    @Test
    public void reshuffleDealsANewOrder() {
        List<FeedItem> items = items(50);
        List<Long> before = shuffled(items);
        FeedItemPermutors.reshuffle();
        assertNotEquals(before, shuffled(items));
    }

    @Test
    public void randomIsAllowedAsAFeedSortOrder() {
        Feed feed = new Feed("https://example.com/other.xml", null, "Other");
        feed.setSortOrder(SortOrder.RANDOM);
        assertEquals(SortOrder.RANDOM, feed.getSortOrder());
        assertEquals(SortOrder.Scope.INTRA_FEED, SortOrder.RANDOM_FAKE.scope);
    }
}
