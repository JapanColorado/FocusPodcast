package allen.town.podcast.core.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import allen.town.podcast.model.feed.FeedItem;

/**
 * Plain JUnit tests for the pure helpers used by the feed merge and the missing-file sweep.
 */
public class DBTasksPureLogicTest {

    private static FeedItem item(String identifier, String title) {
        FeedItem item = new FeedItem();
        item.setItemIdentifier(identifier);
        item.setTitle(title);
        return item;
    }

    @Test
    public void indexPrefersIdentifierAndKeepsFirstDuplicate() {
        FeedItem a = item("guid-a", "A");
        FeedItem b = item(null, "Only title");
        FeedItem aAgain = item("guid-a", "A again");
        Map<String, FeedItem> index = DBTasks.indexByIdentifyingValue(Arrays.asList(a, b, aAgain));

        assertEquals(2, index.size());
        assertSame(a, index.get("guid-a"));
        assertSame(b, index.get("Only title"));
        assertNull(index.get("missing"));
    }

    @Test
    public void indexOfEmptyListIsEmpty() {
        assertTrue(DBTasks.indexByIdentifyingValue(Collections.emptyList()).isEmpty());
    }

    @Test
    public void fewMissingFilesAreIndividualDeletions() {
        assertFalse(DBTasks.looksLikeStorageOutage(1, 10));
        assertFalse(DBTasks.looksLikeStorageOutage(5, 10));
        assertFalse(DBTasks.looksLikeStorageOutage(3, 3)); // too few files to judge
    }

    @Test
    public void mostFilesMissingLooksLikeAnOutage() {
        assertTrue(DBTasks.looksLikeStorageOutage(6, 10));
        assertTrue(DBTasks.looksLikeStorageOutage(200, 200));
    }
}
