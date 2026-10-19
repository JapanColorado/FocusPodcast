package dev.russell.focuspodcast.storage.db;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Date;

import dev.russell.focuspodcast.model.feed.Feed;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedItemFilter;

/**
 * {@link Db#getFeedItemCount} counts every item of one feed regardless of the feed's item filter;
 * the feed screen subtracts the filtered list from it to say how many episodes the filter hides.
 */
@RunWith(RobolectricTestRunner.class)
public class FeedItemCountTest {

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        Db.init(context);
    }

    @After
    public void tearDown() {
        Db.tearDownTests();
        ApplicationProvider.getApplicationContext().deleteDatabase(Db.DATABASE_NAME);
    }

    @Test
    public void countIgnoresTheItemFilterAndOtherFeeds() {
        Db db = Db.getInstance();
        Feed feed = feedWithItems("https://example.com/a.xml", "A",
                FeedItem.PLAYED, FeedItem.NEW, FeedItem.UNPLAYED);
        Feed other = feedWithItems("https://example.com/b.xml", "B", FeedItem.NEW);
        db.setCompleteFeed(feed, other);

        assertEquals(3, db.getFeedItemCount(feed.getId()));
        assertEquals(1, db.getFeedItemCount(other.getId()));
        try (Cursor cursor = db.getItemsOfFeedCursor(feed,
                new FeedItemFilter(new String[]{FeedItemFilter.PLAYED}))) {
            assertEquals(1, cursor.getCount());
        }
    }

    @Test
    public void filteredCountMatchesTheFilteredListOfOneFeed() {
        Db db = Db.getInstance();
        Feed feed = feedWithItems("https://example.com/a.xml", "A",
                FeedItem.PLAYED, FeedItem.NEW, FeedItem.UNPLAYED);
        Feed other = feedWithItems("https://example.com/b.xml", "B", FeedItem.PLAYED);
        db.setCompleteFeed(feed, other);

        FeedItemFilter played = new FeedItemFilter(new String[]{FeedItemFilter.PLAYED});
        FeedItemFilter unplayed = new FeedItemFilter(new String[]{FeedItemFilter.UNPLAYED});
        assertEquals(1, db.getFeedItemCount(feed.getId(), played));
        assertEquals(2, db.getFeedItemCount(feed.getId(), unplayed));
        assertEquals(3, db.getFeedItemCount(feed.getId(), FeedItemFilter.unfiltered()));
        assertEquals(1, db.getFeedItemCount(other.getId(), played));
    }

    @Test
    public void unknownFeedHasNoItems() {
        assertEquals(0, Db.getInstance().getFeedItemCount(4242L));
    }

    private static Feed feedWithItems(String url, String title, int... states) {
        Feed feed = new Feed(url, null, title);
        ArrayList<FeedItem> items = new ArrayList<>();
        for (int i = 0; i < states.length; i++) {
            items.add(new FeedItem(0, title + i, "guid-" + title + i, url + "#" + i,
                    new Date(1_600_000_000_000L + i), states[i], feed));
        }
        feed.setItems(items);
        return feed;
    }
}
