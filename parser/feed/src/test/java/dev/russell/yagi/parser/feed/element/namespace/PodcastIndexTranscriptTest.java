package dev.russell.yagi.parser.feed.element.namespace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;

import dev.russell.yagi.model.feed.Feed;
import dev.russell.yagi.model.feed.FeedItem;

/** {@code <podcast:transcript>} on items: the best of several formats wins. */
@RunWith(RobolectricTestRunner.class)
public class PodcastIndexTranscriptTest {

    @Test
    public void picksTheBestTranscriptOfEachItem() throws Exception {
        File feedFile = FeedParserTestHelper.getFeedFile("feed-rss-testTranscripts.xml");
        Feed feed = FeedParserTestHelper.runFeedParser(feedFile);

        assertTranscript(item(feed, "One transcript"), "https://example.com/one.vtt", "text/vtt");
        assertTranscript(item(feed, "Several transcripts"), "https://example.com/several.json", "application/json");
        assertTranscript(item(feed, "SRT before VTT"), "https://example.com/b.vtt", "text/vtt");
        assertTranscript(item(feed, "Unknown type"), "https://example.com/unknown.bin", "application/x-something");
        assertTranscript(item(feed, "Unknown then known"), "https://example.com/u.txt", "text/plain");
        assertFalse(item(feed, "No url").hasTranscript());
        assertNull(item(feed, "None").getTranscriptUrl());
    }

    private static void assertTranscript(FeedItem item, String url, String type) {
        assertEquals(item.getTitle(), url, item.getTranscriptUrl());
        assertEquals(item.getTitle(), type, item.getTranscriptType());
    }

    private static FeedItem item(Feed feed, String title) {
        for (FeedItem item : feed.getItems()) {
            if (title.equals(item.getTitle())) {
                return item;
            }
        }
        throw new AssertionError("no item " + title);
    }
}
