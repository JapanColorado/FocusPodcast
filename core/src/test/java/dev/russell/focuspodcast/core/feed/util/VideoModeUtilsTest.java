package dev.russell.focuspodcast.core.feed.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;
import java.util.HashSet;

import dev.russell.focuspodcast.core.pref.PlaybackPreferences;
import dev.russell.focuspodcast.core.pref.Prefs;
import dev.russell.focuspodcast.core.pref.UsageStatistics;
import dev.russell.focuspodcast.core.storage.DBReader;
import dev.russell.focuspodcast.core.storage.DBWriter;
import dev.russell.focuspodcast.core.sync.SynchronizationCredentials;
import dev.russell.focuspodcast.core.sync.SynchronizationSettings;
import dev.russell.focuspodcast.model.feed.Feed;
import dev.russell.focuspodcast.model.feed.FeedFilter;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedMedia;
import dev.russell.focuspodcast.model.feed.FeedPreferences;
import dev.russell.focuspodcast.model.feed.VolumeAdaptionSetting;
import dev.russell.focuspodcast.playback.base.PlayerStatus;
import dev.russell.focuspodcast.storage.db.Db;

/**
 * How a video episode plays: the global default, a podcast's own choice over it, and the switch
 * for the episode that is playing over both.
 */
@RunWith(RobolectricTestRunner.class)
public class VideoModeUtilsTest {

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
    public void videoIsTheDefaultOnAFreshInstall() {
        assertTrue(Prefs.isPlayVideoAsVideo());
        assertTrue(VideoModeUtils.shouldShowVideo(video(feed(null), 1)));
    }

    @Test
    public void aFeedFollowsTheDefaultUntilItHasItsOwnChoice() {
        FeedMedia follows = video(feed(null), 1);
        FeedMedia watches = video(feed(Boolean.TRUE), 2);
        FeedMedia listens = video(feed(Boolean.FALSE), 3);

        Prefs.setPlayVideoAsVideo(false);
        assertFalse(VideoModeUtils.shouldShowVideo(follows));
        assertTrue("a feed set to video opens the video player while the default is audio",
                VideoModeUtils.shouldShowVideo(watches));
        assertFalse(VideoModeUtils.shouldShowVideo(listens));

        Prefs.setPlayVideoAsVideo(true);
        assertTrue(VideoModeUtils.shouldShowVideo(follows));
        assertTrue(VideoModeUtils.shouldShowVideo(watches));
        assertFalse("a feed set to audio only stays audio while the default is video",
                VideoModeUtils.shouldShowVideo(listens));
    }

    @Test
    public void audioEpisodesAndNullNeverShowVideo() {
        FeedItem item = new FeedItem(0, "Episode", "guid", "https://example.com/e",
                new Date(1_600_000_000_000L), FeedItem.NEW, feed(Boolean.TRUE));
        FeedMedia audio = new FeedMedia(item, "https://example.com/e.mp3", 1, "audio/mpeg");
        item.setMedia(audio);
        assertFalse(VideoModeUtils.shouldShowVideo(audio));
        assertFalse(VideoModeUtils.shouldShowVideo(null));
    }

    @Test
    public void theSwitchLastsForThatEpisodeOnly() {
        Feed feed = feed(null);
        FeedMedia switched = video(feed, 1);
        FeedMedia next = video(feed, 2);

        VideoModeUtils.setShowVideo(switched, false);
        assertFalse(VideoModeUtils.shouldShowVideo(switched));
        assertTrue("another episode follows its podcast", VideoModeUtils.shouldShowVideo(next));

        // Starting the next episode replaces the remembered switch.
        VideoModeUtils.onMediaStarted(next);
        assertTrue(VideoModeUtils.shouldShowVideo(next));
        assertTrue(VideoModeUtils.shouldShowVideo(switched));
    }

    @Test
    public void startingAnEpisodeKeepsASwitchMadeForIt() {
        FeedMedia media = video(feed(null), 1);
        VideoModeUtils.setShowVideo(media, false);
        VideoModeUtils.onMediaStarted(media);
        assertFalse(VideoModeUtils.shouldShowVideo(media));
    }

    @Test
    public void startingAnyOtherEpisodeForgetsTheSwitch() {
        FeedMedia switched = video(feed(Boolean.FALSE), 1);
        VideoModeUtils.setShowVideo(switched, true);
        assertTrue(VideoModeUtils.shouldShowVideo(switched));

        FeedItem item = new FeedItem(9, "Audio", "guid9", "https://example.com/9",
                new Date(1_600_000_000_000L), FeedItem.NEW, feed(null));
        FeedMedia audio = new FeedMedia(item, "https://example.com/9.mp3", 1, "audio/mpeg");
        item.setMedia(audio);
        VideoModeUtils.onMediaStarted(audio);
        assertFalse("replaying it later follows its podcast again", VideoModeUtils.shouldShowVideo(switched));
    }

    @Test
    public void theEndOfPlaybackForgetsTheSwitch() {
        FeedMedia switched = video(feed(null), 1);
        VideoModeUtils.setShowVideo(switched, false);
        PlaybackPreferences.writeNoMediaPlaying();
        assertTrue(VideoModeUtils.shouldShowVideo(switched));
    }

    @Test
    public void lookupsWithoutTheEpisodeAgreeOnceTheServiceStartedIt() {
        FeedMedia listens = video(feed(Boolean.FALSE), 7);
        PlaybackPreferences.writeMediaPlaying(listens, PlayerStatus.PLAYING);
        VideoModeUtils.onMediaStarted(listens);
        assertFalse("the podcast's audio-only choice holds for the notification too",
                VideoModeUtils.shouldShowVideoForCurrentMedia());

        VideoModeUtils.setShowVideo(listens, true);
        assertTrue(VideoModeUtils.shouldShowVideoForCurrentMedia());
    }

    @Test
    public void setAndResetStoreTheFeedsChoice() throws Exception {
        Feed feed = feed(null);
        DBWriter.addNewFeed(context, feed).get();

        VideoModeUtils.setVideoForFeed(feed, false).get();
        assertEquals(Boolean.FALSE, DBReader.getFeedPreferences(feed.getId()).getVideoOverride());

        VideoModeUtils.resetVideoForFeed(feed).get();
        assertNull(feed.getPreferences().getVideoOverride());
        assertNull(DBReader.getFeedPreferences(feed.getId()).getVideoOverride());
    }

    private static FeedMedia video(Feed feed, long id) {
        FeedItem item = new FeedItem(id, "Episode " + id, "guid" + id, "https://example.com/" + id,
                new Date(1_600_000_000_000L), FeedItem.NEW, feed);
        FeedMedia media = new FeedMedia(id, item, 60_000, 0, 1, "video/mp4", null,
                "https://example.com/" + id + ".mp4", false, null, 0, 0);
        item.setMedia(media);
        return media;
    }

    private static Feed feed(Boolean videoOverride) {
        FeedPreferences preferences = new FeedPreferences(0, true, true,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                null, null, new FeedFilter(), FeedPreferences.SPEED_USE_GLOBAL,
                0, 0, false, new HashSet<>(), false, 0, false, false);
        preferences.setVideoOverride(videoOverride);
        Feed feed = new Feed("https://example.com/feed.xml", null, "Video feed");
        feed.setPreferences(preferences);
        feed.setSubscribed(true);
        return feed;
    }
}
