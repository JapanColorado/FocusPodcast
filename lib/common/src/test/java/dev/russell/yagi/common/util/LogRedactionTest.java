package dev.russell.yagi.common.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LogRedactionTest {

    @Test
    public void keepsOnlySchemeAndHost() {
        assertEquals("GET https://www.patreon.com/... 200",
                LogRedaction.redactUrls("GET https://www.patreon.com/rss/show?auth=s3cr3t 200"));
    }

    @Test
    public void dropsTokensInThePath() {
        assertEquals("feed http://feeds.supercast.com/...",
                LogRedaction.redactUrls("feed http://feeds.supercast.com/episodes/abcdef123456.rss"));
    }

    @Test
    public void dropsUserInfoAndPort() {
        assertEquals("url=https://example.org/... next",
                LogRedaction.redactUrls("url=https://user:pass@example.org:8443/feed.xml next"));
    }

    @Test
    public void stopsAtQuotes() {
        assertEquals("href=\"https://a.example/...\" x",
                LogRedaction.redactUrls("href=\"https://a.example/x?y=1\" x"));
    }

    @Test
    public void leavesOtherTextAlone() {
        String line = "10-30 12:00:01.123  1234  5678 D PlaybackService: seek to 120000";
        assertEquals(line, LogRedaction.redactUrls(line));
    }
}
