package dev.russell.yagi.core.feed.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import dev.russell.yagi.core.pref.PlaybackPreferences;
import dev.russell.yagi.core.pref.Prefs;
import dev.russell.yagi.core.storage.DBWriter;
import dev.russell.yagi.event.playback.VideoModeChangedEvent;
import dev.russell.yagi.model.feed.Feed;
import dev.russell.yagi.model.feed.FeedItem;
import dev.russell.yagi.model.feed.FeedMedia;
import dev.russell.yagi.model.feed.FeedPreferences;
import dev.russell.yagi.model.playback.MediaType;
import dev.russell.yagi.model.playback.Playable;

/**
 * Decides whether a video episode is watched in the video player or listened to as audio only.
 *
 * <p>Two layers, like the other per-podcast settings: the global preference
 * ({@link Prefs#isPlayVideoAsVideo()}) is only the default, and a feed that has its own choice
 * ({@link FeedPreferences#getVideoOverride()}) keeps it. On top of that the listener can switch
 * the episode that is playing ("Audio only" in the video player, "Watch video" in the player
 * sheet); that switch lasts while that episode is the one being played and is kept in
 * {@link PlaybackPreferences}, so the notification, the widgets and a restarted app open the same
 * player. Starting another episode, or the end of playback, forgets it.
 */
public final class VideoModeUtils {

    private VideoModeUtils() {
    }

    /** The feed's own choice if it has one, otherwise the global default. */
    public static boolean isVideoPreferred(@Nullable FeedPreferences preferences) {
        Boolean override = preferences != null ? preferences.getVideoOverride() : null;
        return override != null ? override : Prefs.isPlayVideoAsVideo();
    }

    /**
     * Whether this media's podcast wants its video episodes watched. Anything that is not an
     * episode of a feed with loaded preferences follows the global default.
     */
    public static boolean isVideoPreferred(@Nullable Playable media) {
        return isVideoPreferred(preferencesOf(media));
    }

    /**
     * Whether this media should be shown in the video player now: it has video, and either the
     * listener switched it to video while it played or, without such a switch, its podcast
     * prefers video.
     */
    public static boolean shouldShowVideo(@Nullable Playable media) {
        if (media == null || media.getMediaType() != MediaType.VIDEO) {
            return false;
        }
        Boolean chosen = PlaybackPreferences.getVideoMode(keyOf(media));
        return chosen != null ? chosen : isVideoPreferred(media);
    }

    /**
     * Whether this media has to be streamed: it is not downloaded, or its download holds only
     * the audio track ({@link FeedMedia#isAudioOnlyDownload()}) and it is to be watched.
     */
    public static boolean shouldStream(@NonNull Playable media) {
        return !media.localFileAvailable() || (isAudioOnlyDownload(media) && shouldShowVideo(media));
    }

    /**
     * {@link #shouldStream(Playable)} for starting playback. When streaming is not allowed (mobile
     * data) and the media is an audio-only download that would be watched, it is listened to
     * instead: its mode is set to audio only for this playback and the answer is false.
     */
    public static boolean resolveStream(@NonNull Playable media, boolean streamingAllowed) {
        if (!shouldStream(media)) {
            return false;
        }
        if (!streamingAllowed && media.localFileAvailable()) {
            PlaybackPreferences.writeVideoMode(keyOf(media), false);
            return false;
        }
        return true;
    }

    /** Whether this media's downloaded file is the audio track of a video episode. */
    public static boolean isAudioOnlyDownload(@Nullable Playable media) {
        return media instanceof FeedMedia && ((FeedMedia) media).isAudioOnlyDownload();
    }

    /**
     * {@link #shouldShowVideo(Playable)} for callers that only know what the playback service
     * last recorded (notification, widgets, media session). The service pins the choice when it
     * starts an episode ({@link #onMediaStarted}), so this agrees with the per-podcast setting.
     */
    public static boolean shouldShowVideoForCurrentMedia() {
        if (!PlaybackPreferences.getCurrentEpisodeIsVideo()) {
            return false;
        }
        long mediaId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
        // Media that is not an episode of a feed has no id here; the recorded mode only ever
        // describes the episode being played (see onMediaStarted), so it is read as is.
        Boolean chosen = mediaId == PlaybackPreferences.NO_MEDIA_PLAYING
                ? PlaybackPreferences.getVideoModeOfAnyMedia()
                : PlaybackPreferences.getVideoMode(String.valueOf(mediaId));
        return chosen != null ? chosen : Prefs.isPlayVideoAsVideo();
    }

    /**
     * Switches the episode that is playing between video and audio only, for that episode, and
     * posts {@link VideoModeChangedEvent} so the notification follows.
     */
    public static void setShowVideo(@NonNull Playable media, boolean showVideo) {
        PlaybackPreferences.writeVideoMode(keyOf(media), showVideo);
        EventBus.getDefault().post(new VideoModeChangedEvent());
    }

    /**
     * Called by the playback service once it has loaded an episode. A switch made for this same
     * episode is kept; any other recorded mode belongs to an earlier episode and is replaced: a
     * video episode gets its podcast's choice recorded (so lookups without the episode's
     * preferences, {@link #shouldShowVideoForCurrentMedia()}, give the same answer), anything
     * else clears it.
     */
    public static void onMediaStarted(@Nullable Playable media) {
        if (media == null) {
            return;
        }
        String key = keyOf(media);
        if (PlaybackPreferences.getVideoMode(key) != null) {
            return;
        }
        if (media.getMediaType() == MediaType.VIDEO) {
            PlaybackPreferences.writeVideoMode(key, isVideoPreferred(media));
        } else {
            PlaybackPreferences.clearVideoMode();
        }
    }

    /**
     * Stores an explicit choice for one feed, even when it equals the current default, so a later
     * change of the default does not move it. The future completes once the row is written.
     */
    public static Future<?> setVideoForFeed(@NonNull Feed feed, boolean asVideo) {
        return store(feed, asVideo);
    }

    /** Makes the feed follow the global default again. */
    public static Future<?> resetVideoForFeed(@NonNull Feed feed) {
        return store(feed, null);
    }

    private static Future<?> store(@NonNull Feed feed, @Nullable Boolean override) {
        FeedPreferences preferences = feed.getPreferences();
        if (preferences == null) {
            return CompletableFuture.completedFuture(null);
        }
        preferences.setVideoOverride(override);
        // Only this field is written: the caller's object may be older than the stored row.
        return DBWriter.updateFeedPreferences(feed.getId(), stored -> stored.setVideoOverride(override));
    }

    @NonNull
    private static String keyOf(@NonNull Playable media) {
        return String.valueOf(media.getIdentifier());
    }

    @Nullable
    private static FeedPreferences preferencesOf(@Nullable Playable media) {
        if (!(media instanceof FeedMedia)) {
            return null;
        }
        FeedItem item = ((FeedMedia) media).getItem();
        Feed feed = item != null ? item.getFeed() : null;
        return feed != null ? feed.getPreferences() : null;
    }
}
