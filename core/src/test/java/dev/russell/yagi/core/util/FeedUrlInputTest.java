package dev.russell.yagi.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class FeedUrlInputTest {

    @Test
    public void plainUrlsPassThrough() {
        assertEquals("https://example.com/feed.xml", FeedUrlInput.normalize("https://example.com/feed.xml"));
        assertEquals("http://example.com:8080/rss?id=1", FeedUrlInput.normalize("http://example.com:8080/rss?id=1"));
        assertEquals("https://example.com", FeedUrlInput.normalize("https://example.com"));
        assertEquals("https://user:pass@example.com/private.xml",
                FeedUrlInput.normalize("https://user:pass@example.com/private.xml"));
    }

    @Test
    public void surroundingAndEmbeddedWhitespaceIsRemoved() {
        assertEquals("https://feed.nashownotes.com/rss.xml",
                FeedUrlInput.normalize("  https://feed.nashownotes.com/rss.xml \n"));
        // non-breaking, narrow no-break, ideographic and zero-width spaces, as pasted from chat apps
        assertEquals("https://example.com/feed.xml",
                FeedUrlInput.normalize("\u00A0https://exam\u200Bple.com/\u202Ffeed.xml\u3000\uFEFF"));
        assertEquals("https://example.com/feed.xml",
                FeedUrlInput.normalize("https://example.com/ feed.xml"));
    }

    @Test
    public void schemeIsLowerCasedButPathIsKept() {
        assertEquals("https://Example.com/Feed.XML", FeedUrlInput.normalize("HTTPS://Example.com/Feed.XML"));
    }

    @Test
    public void podcastPseudoSchemesBecomeHttp() {
        assertEquals("http://example.com/feed.xml", FeedUrlInput.normalize("feed://example.com/feed.xml"));
        assertEquals("https://example.com/feed.xml", FeedUrlInput.normalize("feed:https://example.com/feed.xml"));
        assertEquals("https://example.com/feed.xml", FeedUrlInput.normalize("feed://https://example.com/feed.xml"));
        assertEquals("http://example.com/feed.xml", FeedUrlInput.normalize("itpc://example.com/feed.xml"));
        assertEquals("http://example.com/feed.xml", FeedUrlInput.normalize("pcast://example.com/feed.xml"));
        assertEquals("http://example.com/feed.xml", FeedUrlInput.normalize("PCAST:example.com/feed.xml"));
    }

    @Test
    public void nonUrlsAreRejected() {
        assertNull(FeedUrlInput.normalize(null));
        assertNull(FeedUrlInput.normalize(""));
        assertNull(FeedUrlInput.normalize("   "));
        assertNull(FeedUrlInput.normalize("the daily"));
        assertNull(FeedUrlInput.normalize("example.com/feed.xml"));
        assertNull(FeedUrlInput.normalize("ftp://example.com/feed.xml"));
        assertNull(FeedUrlInput.normalize("https://"));
        assertNull(FeedUrlInput.normalize("https:///feed.xml"));
        assertNull(FeedUrlInput.normalize("httpsexample.com"));
    }
}
