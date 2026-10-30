package dev.russell.yagi.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;

import dev.russell.yagi.core.pref.PlaybackPreferences;
import dev.russell.yagi.core.pref.Prefs;
import dev.russell.yagi.core.pref.UsageStatistics;
import dev.russell.yagi.core.storage.DBReader;
import dev.russell.yagi.core.storage.DBWriter;
import dev.russell.yagi.core.sync.SynchronizationCredentials;
import dev.russell.yagi.core.sync.SynchronizationSettings;
import dev.russell.yagi.model.feed.Feed;
import dev.russell.yagi.model.feed.FeedFilter;
import dev.russell.yagi.model.feed.FeedItem;
import dev.russell.yagi.model.feed.FeedPreferences;
import dev.russell.yagi.model.feed.VolumeAdaptionSetting;
import dev.russell.yagi.model.transcript.CachedTranscript;
import dev.russell.yagi.model.transcript.Transcript;
import dev.russell.yagi.storage.db.Db;

/**
 * The transcript cache through {@link DBWriter}/{@link DBReader} and the decisions of
 * {@link TranscriptUtils}: a current cached copy is used without the network, a stale one only
 * when the new address fails. No test reaches the network: the failing addresses are malformed,
 * which fails before any connection is made.
 */
@RunWith(RobolectricTestRunner.class)
public class TranscriptUtilsTest {

    private static final String VTT = "WEBVTT\n\n00:01.000 --> 00:02.000\n<v Ann>Cached words.\n";
    private static final String BAD_URL = "not a url";

    private Application context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        Prefs.resetForTests();
        Prefs.init(context);
        PlaybackPreferences.init(context);
        UsageStatistics.init(context);
        SynchronizationCredentials.init(context);
        SynchronizationSettings.init(context);
        Db.init(context);
    }

    @After
    public void tearDown() {
        DBWriter.tearDownTests();
        Db.tearDownTests();
        context.deleteDatabase(Db.DATABASE_NAME);
        Prefs.resetForTests();
    }

    @Test
    public void cacheRoundTripsThroughDbWriterAndDbReader() throws Exception {
        FeedItem item = storedItem("Round trip", "https://example.com/a.vtt");
        assertNull(DBReader.loadCachedTranscript(item.getId()));

        DBWriter.setTranscript(item.getId(), "https://example.com/a.vtt", "text/vtt", VTT, "Ann: Cached words.").get();

        CachedTranscript cached = DBReader.loadCachedTranscript(item.getId());
        assertNotNull(cached);
        assertEquals(VTT, cached.getRaw());
        assertEquals("text/vtt", cached.getType());
        assertTrue(cached.isFrom("https://example.com/a.vtt"));
        assertTrue(cached.getFetchedAt() > 0);
    }

    @Test
    public void currentCachedCopyIsUsedWithoutTheNetwork() throws Exception {
        FeedItem item = storedItem("Current", BAD_URL);
        DBWriter.setTranscript(item.getId(), BAD_URL, "text/vtt", VTT, "Ann: Cached words.").get();

        Transcript transcript = TranscriptUtils.load(item);

        assertEquals(1, transcript.size());
        assertEquals("Ann", transcript.getSegments().get(0).getSpeaker());
    }

    @Test
    public void staleCopyIsShownWhenTheNewAddressFails() throws Exception {
        FeedItem item = storedItem("Stale", BAD_URL);
        DBWriter.setTranscript(item.getId(), "https://example.com/old.vtt", "text/vtt", VTT, "x").get();

        Transcript transcript = TranscriptUtils.load(item);

        assertEquals("Cached words.", transcript.getSegments().get(0).getText());
    }

    @Test
    public void nothingCachedAndAFailingAddressThrows() throws Exception {
        FeedItem item = storedItem("Failing", BAD_URL);
        try {
            TranscriptUtils.load(item);
            fail("expected an IOException");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void itemWithoutTranscriptIsEmptyAndCachingIsANoOp() throws Exception {
        FeedItem item = storedItem("None", null);
        assertTrue(TranscriptUtils.load(item).isEmpty());
        TranscriptUtils.cacheQuietly(item);
        TranscriptUtils.cacheQuietly(null);
        assertNull(DBReader.loadCachedTranscript(item.getId()));
    }

    @Test
    public void cachingAfterADownloadNeverThrows() throws Exception {
        FeedItem item = storedItem("Quiet", BAD_URL);
        TranscriptUtils.cacheQuietly(item);
        assertNull(DBReader.loadCachedTranscript(item.getId()));
    }

    private FeedItem storedItem(String title, String transcriptUrl) throws Exception {
        Feed feed = new Feed("https://example.com/" + title + ".xml", null, title + " feed");
        feed.setPreferences(new FeedPreferences(0, true, true,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                null, null, new FeedFilter(), FeedPreferences.SPEED_USE_GLOBAL,
                0, 0, false, new HashSet<>(), false, 0, false, false));
        feed.setSubscribed(true);
        FeedItem item = new FeedItem(0, title, "guid-" + title, "https://example.com/" + title,
                new Date(1_600_000_000_000L), FeedItem.NEW, feed);
        item.setTranscript(transcriptUrl, transcriptUrl == null ? null : "text/vtt");
        feed.setItems(new ArrayList<>(Collections.singletonList(item)));
        DBWriter.addNewFeed(context, feed).get();
        assertTrue(item.getId() > 0);
        return item;
    }
}
