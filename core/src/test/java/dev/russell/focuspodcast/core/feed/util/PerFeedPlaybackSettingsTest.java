package dev.russell.focuspodcast.core.feed.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import dev.russell.focuspodcast.core.pref.PlaybackPreferences;
import dev.russell.focuspodcast.core.pref.Prefs;
import dev.russell.focuspodcast.core.pref.UsageStatistics;
import dev.russell.focuspodcast.core.storage.DBReader;
import dev.russell.focuspodcast.core.storage.DBWriter;
import dev.russell.focuspodcast.core.sync.SynchronizationCredentials;
import dev.russell.focuspodcast.core.sync.SynchronizationSettings;
import dev.russell.focuspodcast.model.feed.Feed;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedMedia;
import dev.russell.focuspodcast.model.feed.FeedPreferences;
import dev.russell.focuspodcast.model.feed.VolumeAdaptionSetting;
import dev.russell.focuspodcast.model.playback.ClearVoiceLevel;
import dev.russell.focuspodcast.model.playback.VolumeLeveling;
import dev.russell.focuspodcast.model.playback.MediaType;
import dev.russell.focuspodcast.storage.db.Db;

/**
 * Per-podcast playback speed and audio effects: a subscribed podcast follows the global defaults
 * until the player changes them, a change made in the player lands on the podcast and not on the
 * global default, and only the changed columns are written so a stale in-memory copy (the
 * playback service keeps one per episode) cannot undo other settings.
 */
@RunWith(RobolectricTestRunner.class)
public class PerFeedPlaybackSettingsTest {

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

    // ------------------------------------------------------- speed resolution

    @Test
    public void resolveSpeedFallsBackToTheGlobalDefault() {
        assertEquals(1.3f, PlaybackSpeedUtils.resolveSpeed(null, 1.3f), 0.0001f);
        FeedPreferences following = preferences();
        assertFalse(PlaybackSpeedUtils.hasOwnSpeed(following));
        assertEquals(1.3f, PlaybackSpeedUtils.resolveSpeed(following, 1.3f), 0.0001f);
    }

    @Test
    public void resolveSpeedPrefersThePodcastsOwnSpeed() {
        FeedPreferences own = preferences();
        own.setFeedPlaybackSpeed(1.8f);
        assertTrue(PlaybackSpeedUtils.hasOwnSpeed(own));
        assertEquals(1.8f, PlaybackSpeedUtils.resolveSpeed(own, 1.3f), 0.0001f);
    }

    @Test
    public void aPodcastThatNeverChangedSpeedTracksTheGlobalDefault() {
        FeedMedia media = mediaOf(feed(true));
        Prefs.setPlaybackSpeed(1.4f);
        assertEquals(1.4f, PlaybackSpeedUtils.getCurrentPlaybackSpeed(media), 0.0001f);
        Prefs.setPlaybackSpeed(2.0f);
        assertEquals(2.0f, PlaybackSpeedUtils.getCurrentPlaybackSpeed(media), 0.0001f);
    }

    @Test
    public void anUnsubscribedFeedAlwaysUsesTheGlobalDefault() {
        Feed preview = feed(false);
        preview.getPreferences().setFeedPlaybackSpeed(1.7f);
        Prefs.setPlaybackSpeed(1.1f);
        assertNull(PlayableFeedPreferences.of(mediaOf(preview)));
        assertEquals(1.1f, PlaybackSpeedUtils.getCurrentPlaybackSpeed(mediaOf(preview)), 0.0001f);
    }

    // ------------------------------------------------------- player speed changes

    @Test
    public void aSpeedChangeInThePlayerIsStoredOnThePodcastNotGlobally() throws Exception {
        Feed feed = storedFeed();
        FeedMedia media = mediaOf(feed);
        Prefs.setPlaybackSpeed(1.2f);

        long changedFeed = PlaybackSpeedUtils.rememberSpeed(media, 1.75f);
        awaitDb();

        assertEquals(feed.getId(), changedFeed);
        assertEquals(1.2f, Prefs.getPlaybackSpeed(MediaType.AUDIO), 0.0001f);
        // the in-memory copy (the service's) sees it for a re-prepare ...
        assertEquals(1.75f, PlaybackSpeedUtils.getCurrentPlaybackSpeed(media), 0.0001f);
        // ... and so does the next episode, which is loaded from the database
        FeedPreferences stored = DBReader.getFeedPreferences(feed.getId());
        assertNotNull(stored);
        assertEquals(1.75f, stored.getFeedPlaybackSpeed(), 0.0001f);
        // another podcast that never changed its speed still plays at the default
        assertEquals(1.2f, PlaybackSpeedUtils.getCurrentPlaybackSpeed(mediaOf(feed(true))), 0.0001f);
    }

    @Test
    public void resettingMakesThePodcastFollowTheDefaultAgain() throws Exception {
        Feed feed = storedFeed();
        FeedMedia media = mediaOf(feed);
        PlaybackSpeedUtils.rememberSpeed(media, 1.75f);

        PlaybackSpeedUtils.rememberSpeed(media, FeedPreferences.SPEED_USE_GLOBAL);
        awaitDb();

        Prefs.setPlaybackSpeed(1.5f);
        assertEquals(1.5f, PlaybackSpeedUtils.getCurrentPlaybackSpeed(media), 0.0001f);
        FeedPreferences stored = DBReader.getFeedPreferences(feed.getId());
        assertNotNull(stored);
        assertFalse(PlaybackSpeedUtils.hasOwnSpeed(stored));
    }

    @Test
    public void aSpeedChangeForMediaWithoutAPodcastChangesTheGlobalDefault() {
        Feed preview = feed(false);
        assertEquals(0, PlaybackSpeedUtils.rememberSpeed(mediaOf(preview), 1.6f));
        assertEquals(1.6f, Prefs.getPlaybackSpeed(MediaType.AUDIO), 0.0001f);
        assertEquals(FeedPreferences.SPEED_USE_GLOBAL, preview.getPreferences().getFeedPlaybackSpeed(), 0.0001f);

        assertEquals(0, PlaybackSpeedUtils.rememberSpeed(null, 1.9f));
        assertEquals(1.9f, Prefs.getPlaybackSpeed(MediaType.AUDIO), 0.0001f);

        // "reset" means nothing without a podcast and must not store -1 as the default
        assertEquals(0, PlaybackSpeedUtils.rememberSpeed(null, FeedPreferences.SPEED_USE_GLOBAL));
        assertEquals(1.9f, Prefs.getPlaybackSpeed(MediaType.AUDIO), 0.0001f);
    }

    @Test
    public void aSpeedChangeFromAStaleCopyKeepsOtherSettings() throws Exception {
        Feed feed = storedFeed();
        FeedMedia media = mediaOf(feed); // the service's copy, loaded when the episode started

        // meanwhile Feed Settings changes something else from its own copy
        FeedPreferences settingsCopy = DBReader.getFeedPreferences(feed.getId());
        assertNotNull(settingsCopy);
        settingsCopy.setKeepUpdated(false);
        await(DBWriter.setFeedPreferences(settingsCopy));

        PlaybackSpeedUtils.rememberSpeed(media, 1.25f);
        awaitDb();

        FeedPreferences stored = DBReader.getFeedPreferences(feed.getId());
        assertNotNull(stored);
        assertEquals(1.25f, stored.getFeedPlaybackSpeed(), 0.0001f);
        assertFalse(stored.getKeepUpdated());
    }

    // ------------------------------------------------------- audio effects

    @Test
    public void aPodcastWithoutItsOwnEffectsUsesTheGlobalOnes() {
        FeedMedia media = mediaOf(feed(true));
        Prefs.setSkipSilence(true);
        Prefs.stereoToMono(true);
        Prefs.setClearVoiceLevel(ClearVoiceLevel.STRONG);
        Prefs.setVolumeLeveling(VolumeLeveling.GENTLE);
        assertTrue(AudioEffectUtils.isSkipEnable(media));
        assertTrue(AudioEffectUtils.isMonoEnable(media));
        assertEquals(ClearVoiceLevel.STRONG, AudioEffectUtils.getClearVoiceLevel(media));
        assertEquals(VolumeLeveling.GENTLE, AudioEffectUtils.getVolumeLeveling(media));
        assertEquals(VolumeLeveling.GENTLE, AudioEffectUtils.getVolumeLeveling((FeedMedia) null));
        assertTrue(AudioEffectUtils.isSkipEnable(null));
    }

    @Test
    public void customizeCopiesTheDefaultsSoNothingChangesAudibly() {
        Prefs.setSkipSilence(true);
        Prefs.stereoToMono(false);
        Prefs.setClearVoiceLevel(ClearVoiceLevel.LIGHT);
        Prefs.setVolumeLeveling(VolumeLeveling.STRONG);
        FeedPreferences preferences = preferences();

        AudioEffectUtils.customize(preferences);

        assertTrue(AudioEffectUtils.hasOwnEffects(preferences));
        assertTrue(preferences.isSkipSilence());
        assertFalse(preferences.isMono());
        assertEquals(ClearVoiceLevel.LIGHT, preferences.getClearVoiceLevel());
        assertEquals(VolumeLeveling.STRONG, preferences.getVolumeLeveling());

        // later global changes no longer reach a podcast with its own effects
        Prefs.setSkipSilence(false);
        Prefs.setVolumeLeveling(VolumeLeveling.OFF);
        assertTrue(AudioEffectUtils.isSkipSilence(preferences));
        assertEquals(VolumeLeveling.STRONG, AudioEffectUtils.getVolumeLeveling(preferences));
    }

    @Test
    public void customizeKeepsEffectsThePodcastAlreadyHas() {
        FeedPreferences preferences = preferences();
        preferences.setUseFeedEffect(true);
        preferences.setMono(true);
        Prefs.stereoToMono(false);

        AudioEffectUtils.customize(preferences);

        assertTrue(preferences.isMono());
    }

    @Test
    public void savedEffectsReachTheDatabaseWithoutTouchingOtherFields() throws Exception {
        Feed feed = storedFeed();
        FeedPreferences stale = feed.getPreferences();
        FeedPreferences settingsCopy = DBReader.getFeedPreferences(feed.getId());
        assertNotNull(settingsCopy);
        settingsCopy.setFeedPlaybackSpeed(1.6f);
        await(DBWriter.setFeedPreferences(settingsCopy));

        AudioEffectUtils.customize(stale);
        stale.setClearVoiceLevel(ClearVoiceLevel.STRONG);
        stale.setVolumeLeveling(VolumeLeveling.GENTLE);
        AudioEffectUtils.saveFeedEffects(feed.getId(), stale);
        awaitDb();

        FeedPreferences stored = DBReader.getFeedPreferences(feed.getId());
        assertNotNull(stored);
        assertTrue(stored.isUseFeedEffect());
        assertEquals(ClearVoiceLevel.STRONG, stored.getClearVoiceLevel());
        assertEquals(VolumeLeveling.GENTLE, stored.getVolumeLeveling());
        assertEquals(1.6f, stored.getFeedPlaybackSpeed(), 0.0001f);
    }

    // ------------------------------------------------------- set as default

    @Test
    public void makeDefaultMovesThePodcastsEffectsIntoTheDefaults() throws Exception {
        Feed feed = storedFeed();
        FeedPreferences preferences = feed.getPreferences();
        Prefs.setSkipSilence(false);
        Prefs.stereoToMono(false);
        Prefs.setClearVoiceLevel(ClearVoiceLevel.OFF);
        Prefs.setVolumeLeveling(VolumeLeveling.OFF);
        AudioEffectUtils.customize(preferences);
        preferences.setMono(true);
        preferences.setClearVoiceLevel(ClearVoiceLevel.WARM);
        preferences.setVolumeLeveling(VolumeLeveling.GENTLE);

        AudioEffectUtils.makeDefault(feed.getId(), preferences);
        awaitDb();

        assertTrue(Prefs.stereoToMono());
        assertFalse(Prefs.isSkipSilence());
        assertEquals(ClearVoiceLevel.WARM, Prefs.getClearVoiceLevel());
        assertEquals(VolumeLeveling.GENTLE, Prefs.getVolumeLeveling());
        assertFalse(AudioEffectUtils.hasOwnEffects(preferences));
        FeedPreferences stored = DBReader.getFeedPreferences(feed.getId());
        assertNotNull(stored);
        assertFalse(stored.isUseFeedEffect());
        // what the podcast plays with is unchanged
        assertEquals(ClearVoiceLevel.WARM, AudioEffectUtils.getClearVoiceLevel(preferences));
    }

    @Test
    public void makeDefaultLeavesTheDefaultsAloneWithoutOwnEffects() {
        Prefs.stereoToMono(false);
        FeedPreferences preferences = preferences();
        preferences.setMono(true); // stale value of a podcast that follows the defaults

        AudioEffectUtils.makeDefault(1, preferences);

        assertFalse(Prefs.stereoToMono());
    }

    @Test
    public void makeDefaultMovesThePodcastsSpeedIntoTheDefault() throws Exception {
        Feed feed = storedFeed();
        FeedPreferences preferences = feed.getPreferences();
        Prefs.setPlaybackSpeed(1.0f);
        PlaybackSpeedUtils.setFeedSpeed(feed.getId(), preferences, 1.6f);

        PlaybackSpeedUtils.makeDefault(feed.getId(), preferences);
        awaitDb();

        assertEquals(1.6f, Prefs.getPlaybackSpeed(MediaType.AUDIO), 0.0001f);
        assertFalse(PlaybackSpeedUtils.hasOwnSpeed(preferences));
        FeedPreferences stored = DBReader.getFeedPreferences(feed.getId());
        assertNotNull(stored);
        assertFalse(PlaybackSpeedUtils.hasOwnSpeed(stored));
        assertEquals(1.6f, PlaybackSpeedUtils.getCurrentPlaybackSpeed(mediaOf(feed)), 0.0001f);
    }

    @Test
    public void makeDefaultMovesThePodcastsAdChoiceIntoTheDefault() throws Exception {
        Feed feed = storedFeed();
        Prefs.setAdSkipEnabled(false);
        AdSkipUtils.setAdSkipForFeed(feed, true);

        AdSkipUtils.makeDefault(feed);
        awaitDb();

        assertTrue(Prefs.isAdSkipEnabled());
        assertNull(feed.getPreferences().getAdSkipOverride());
        FeedPreferences stored = DBReader.getFeedPreferences(feed.getId());
        assertNotNull(stored);
        assertNull(stored.getAdSkipOverride());
    }

    @Test
    public void playableFeedPreferencesFindsTheSubscribedFeed() {
        Feed feed = feed(true);
        FeedMedia media = mediaOf(feed);
        assertSame(feed.getPreferences(), PlayableFeedPreferences.of(media));
        assertSame(feed, PlayableFeedPreferences.feedOf(media));
        assertNull(PlayableFeedPreferences.of((Feed) null));
        assertEquals(0, PlayableFeedPreferences.feedIdOf(null));
    }

    // ------------------------------------------------------- helpers

    private Feed storedFeed() throws Exception {
        Feed feed = feed(true);
        FeedItem item = feed.getItems().get(0);
        feed.setItems(new ArrayList<>(Collections.singletonList(item)));
        await(DBWriter.addNewFeed(context, feed));
        return feed;
    }

    private static FeedPreferences preferences() {
        return new FeedPreferences(0, true, FeedPreferences.AutoDeleteAction.GLOBAL,
                VolumeAdaptionSetting.OFF, null, null);
    }

    private static Feed feed(boolean subscribed) {
        Feed feed = new Feed("https://s.example/feed.xml", null, "Speed test");
        feed.setPreferences(preferences());
        feed.setSubscribed(subscribed);
        FeedItem item = new FeedItem(0, "Episode", "guid-episode", "https://s.example/1",
                new Date(1_600_000_000_000L), FeedItem.NEW, feed);
        item.setMedia(new FeedMedia(item, "https://s.example/1.mp3", 1000L, "audio/mpeg"));
        feed.setItems(new ArrayList<>(Collections.singletonList(item)));
        return feed;
    }

    private static FeedMedia mediaOf(Feed feed) {
        return feed.getItems().get(0).getMedia();
    }

    /** Waits until everything queued on the single database thread so far has run. */
    private static void awaitDb() throws InterruptedException, ExecutionException {
        await(DBWriter.updateFeedPreferences(-1, stored -> { }));
    }

    private static void await(Future<?> future) throws InterruptedException, ExecutionException {
        future.get();
    }
}
