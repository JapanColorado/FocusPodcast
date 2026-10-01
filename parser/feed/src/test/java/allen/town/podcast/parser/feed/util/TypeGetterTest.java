package allen.town.podcast.parser.feed.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.parser.feed.UnsupportedFeedtypeException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public class TypeGetterTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void recognisesRss2() throws Exception {
        Feed feed = feedFrom("<?xml version=\"1.0\"?><rss version=\"2.0\"><channel/></rss>");
        assertEquals(TypeGetter.Type.RSS20, new TypeGetter().getType(feed));
    }

    @Test
    public void htmlPageIsReportedAsHtml() throws Exception {
        Feed feed = feedFrom("<!DOCTYPE html>\n<html><head><title>x</title><br></head></html>");
        try {
            new TypeGetter().getType(feed);
            fail("expected UnsupportedFeedtypeException");
        } catch (UnsupportedFeedtypeException e) {
            assertEquals("html", e.getRootElement());
        }
    }

    @Test(expected = SAXException.class)
    public void nonXmlBodyIsAParseErrorNotAWebsite() throws Exception {
        // e.g. a JSON error body served in place of the feed
        new TypeGetter().getType(feedFrom("{\"error\": \"not found\"}"));
    }

    private Feed feedFrom(String content) throws IOException {
        File file = folder.newFile();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        Feed feed = new Feed("http://example.com/feed", null);
        feed.setFile_url(file.getAbsolutePath());
        return feed;
    }
}
