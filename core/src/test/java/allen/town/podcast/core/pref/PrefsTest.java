package allen.town.podcast.core.pref;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.TimeUnit;

import allen.town.podcast.model.playback.MediaType;

/**
 * Tests the documented defaults of {@link Prefs}, that its setters round-trip, and that using
 * it before {@code init()} fails loudly instead of returning a silent zero value.
 */
@RunWith(RobolectricTestRunner.class)
public class PrefsTest {

    private Application context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        Prefs.resetForTests();
        Prefs.init(context);
    }

    @After
    public void tearDown() {
        Prefs.resetForTests();
    }

    // -------------------------------------------------------------- defaults

    @Test
    public void defaultPlaybackSpeedIsNormal() {
        assertEquals(1.0f, Prefs.getPlaybackSpeed(MediaType.AUDIO), 0.0001f);
        assertEquals(1.0f, Prefs.getPlaybackSpeed(MediaType.VIDEO), 0.0001f);
        assertEquals(1.0f, Prefs.getPlaybackSpeed(null), 0.0001f);
    }

    @Test
    public void defaultUpdateIntervalIsOff() {
        assertEquals(0L, Prefs.getUpdateInterval());
        assertFalse(Prefs.isAutoUpdateTimeOfDay());
    }

    @Test
    public void aPristineInstallHasAutoUpdateDisabled() {
        assertEquals(0L, Prefs.getUpdateInterval());
        assertTrue(Prefs.isAutoUpdateDisabled());
    }

    @Test
    public void anExplicitZeroIntervalMeansAutoUpdateIsDisabled() {
        prefs().edit().putString(Prefs.PREF_UPDATE_INTERVAL, "0").commit();
        assertEquals(0L, Prefs.getUpdateInterval());
        assertTrue(Prefs.isAutoUpdateDisabled());
    }

    @Test
    public void defaultEpisodeCacheSizeIsTwenty() {
        assertEquals(20, Prefs.getEpisodeCacheSize());
    }

    @Test
    public void defaultParallelDownloadCountIsFour() {
        assertEquals(4, Prefs.getParallelDownloads());
    }

    @Test
    public void otherDocumentedDefaults() {
        assertEquals(30, Prefs.getFastForwardSecs());
        assertEquals(10, Prefs.getRewindSecs());
        assertEquals(30, Prefs.getSmartMarkAsPlayedSecs());
        assertTrue(Prefs.enqueueDownloadedEpisodes());
        assertTrue(Prefs.isFollowQueue());
        assertFalse(Prefs.isEnableAutodownload());
        assertTrue(Prefs.isEnableAutodownloadOnBattery());
        assertFalse(Prefs.isSkipSilence());
        assertFalse(Prefs.shouldDeleteRemoveFromQueue());
        assertTrue(Prefs.isPauseOnHeadsetDisconnect());
        assertTrue(Prefs.shouldPauseForFocusLoss());
        assertEquals(Prefs.EnqueueLocation.BACK, Prefs.getEnqueueLocation());
    }

    // ------------------------------------------------------------ round trips

    @Test
    public void playbackSpeedRoundTrips() {
        Prefs.setPlaybackSpeed(1.75f);
        assertEquals(1.75f, Prefs.getPlaybackSpeed(MediaType.AUDIO), 0.0001f);

        Prefs.setVideoPlaybackSpeed(2.0f);
        assertEquals(2.0f, Prefs.getPlaybackSpeed(MediaType.VIDEO), 0.0001f);
        // Audio and video speeds are independent.
        assertEquals(1.75f, Prefs.getPlaybackSpeed(MediaType.AUDIO), 0.0001f);
    }

    @Test
    public void aCorruptPlaybackSpeedFallsBackToNormalSpeed() {
        prefs().edit().putString("pref_globa_playback_speed", "not a number").commit();
        assertEquals(1.0f, Prefs.getPlaybackSpeed(MediaType.AUDIO), 0.0001f);
        // ... and the broken value is repaired in place.
        assertEquals(1.0f, Prefs.getPlaybackSpeed(MediaType.AUDIO), 0.0001f);
    }

    @Test
    public void updateIntervalIsStoredInHoursAndReadBackInMilliseconds() {
        prefs().edit().putString(Prefs.PREF_UPDATE_INTERVAL, "6").commit();
        assertEquals(TimeUnit.HOURS.toMillis(6), Prefs.getUpdateInterval());
        assertFalse(Prefs.isAutoUpdateDisabled());
        assertFalse(Prefs.isAutoUpdateTimeOfDay());
    }

    @Test
    public void aTimeOfDayUpdateScheduleReportsNoInterval() {
        prefs().edit().putString(Prefs.PREF_UPDATE_INTERVAL, "8:30").commit();
        assertEquals(0L, Prefs.getUpdateInterval());
        assertTrue(Prefs.isAutoUpdateTimeOfDay());
        assertFalse(Prefs.isAutoUpdateDisabled());
    }

    @Test
    public void episodeCacheSizeRoundTrips() {
        prefs().edit().putString(Prefs.PREF_EPISODE_CACHE_SIZE, "100").commit();
        assertEquals(100, Prefs.getEpisodeCacheSize());
    }

    @Test
    public void unlimitedEpisodeCacheSizeIsReportedAsNegativeOne() {
        String unlimited = context.getString(
                allen.town.podcast.core.R.string.pref_episode_cache_unlimited);
        prefs().edit().putString(Prefs.PREF_EPISODE_CACHE_SIZE, unlimited).commit();
        assertEquals(-1, Prefs.getEpisodeCacheSize());
        assertEquals(Prefs.getEpisodeCacheSizeUnlimited(), Prefs.getEpisodeCacheSize());
    }

    @Test
    public void parallelDownloadCountRoundTrips() {
        prefs().edit().putString(Prefs.PREF_PARALLEL_DOWNLOADS, "8").commit();
        assertEquals(8, Prefs.getParallelDownloads());
    }

    @Test
    public void secondsSettersRoundTrip() {
        Prefs.setFastForwardSecs(45);
        Prefs.setRewindSecs(15);
        assertEquals(45, Prefs.getFastForwardSecs());
        assertEquals(15, Prefs.getRewindSecs());
    }

    @Test
    public void booleanSettersRoundTrip() {
        Prefs.setEnqueueDownloadedEpisodes(false);
        assertFalse(Prefs.enqueueDownloadedEpisodes());

        Prefs.setFollowQueue(false);
        assertFalse(Prefs.isFollowQueue());

        Prefs.setEnableAutodownload(true);
        assertTrue(Prefs.isEnableAutodownload());

        Prefs.setSkipSilence(true);
        assertTrue(Prefs.isSkipSilence());
    }

    @Test
    public void enqueueLocationRoundTrips() {
        Prefs.setEnqueueLocation(Prefs.EnqueueLocation.FRONT);
        assertEquals(Prefs.EnqueueLocation.FRONT, Prefs.getEnqueueLocation());
    }

    @Test
    public void anUnknownEnqueueLocationFallsBackToTheBackOfTheQueue() {
        prefs().edit().putString(Prefs.PREF_ENQUEUE_LOCATION, "NO_SUCH_LOCATION").commit();
        assertEquals(Prefs.EnqueueLocation.BACK, Prefs.getEnqueueLocation());
    }

    // ------------------------------------- the never-null string default helper

    @Test
    public void stringPreferencesExplicitlySetToNullStillYieldTheirDefault() {
        // SharedPreferences happily stores a null string; the getString helper must not leak it.
        prefs().edit()
                .putString(Prefs.PREF_UPDATE_INTERVAL, null)
                .putString(Prefs.PREF_PARALLEL_DOWNLOADS, null)
                .putString(Prefs.PREF_EPISODE_CACHE_SIZE, null)
                .putString(Prefs.PREF_ENQUEUE_LOCATION, null)
                .putString(Prefs.PREF_DRAWER_FEED_ORDER_METHOD, null)
                .commit();

        assertEquals(0L, Prefs.getUpdateInterval());
        assertEquals(4, Prefs.getParallelDownloads());
        assertEquals(20, Prefs.getEpisodeCacheSize());
        assertEquals(Prefs.EnqueueLocation.BACK, Prefs.getEnqueueLocation());
        assertNotNull(Prefs.getFeedOrderMethod());
        assertEquals(Prefs.ORDER_ASC, Prefs.getFeedOrderMethod());
    }

    @Test
    public void proxyConfigIsNeverNullAndDefaultsToADirectConnection() {
        assertNotNull(Prefs.getProxyConfig());
        assertEquals(java.net.Proxy.Type.DIRECT, Prefs.getProxyConfig().type);
    }

    // ------------------------------------------------------- the init contract

    @Test
    public void readingAPreferenceBeforeInitThrowsAndNamesTheInitCall() {
        Prefs.resetForTests();
        try {
            Prefs.getParallelDownloads();
            fail("expected an IllegalStateException");
        } catch (IllegalStateException expected) {
            assertNotNull(expected.getMessage());
            assertTrue(expected.getMessage(), expected.getMessage().contains("Prefs.init()"));
        }
    }

    @Test
    public void writingAPreferenceBeforeInitThrowsToo() {
        Prefs.resetForTests();
        try {
            Prefs.setPlaybackSpeed(1.5f);
            fail("expected an IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Prefs.init()"));
        }
    }

    @Test
    public void initMakesPreferencesUsableAgain() {
        Prefs.resetForTests();
        Prefs.init(context);
        assertEquals(4, Prefs.getParallelDownloads());
    }

    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
