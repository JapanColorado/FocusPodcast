package dev.russell.focuspodcast.core.pref;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import android.util.Log;
import dev.russell.focuspodcast.event.PlayerStatusEvent;
import dev.russell.focuspodcast.model.feed.FeedMedia;
import dev.russell.focuspodcast.model.playback.MediaType;
import dev.russell.focuspodcast.model.playback.Playable;
import dev.russell.focuspodcast.playback.base.PlayerStatus;
import org.greenrobot.eventbus.EventBus;


/**
 * Provides access to preferences set by the playback service. A private
 * instance of this class must first be instantiated via createInstance() or
 * otherwise every public method will throw an Exception when called.
 */
public class PlaybackPreferences implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "PlaybackPreferences";

    /**
     * Contains the feed id of the currently playing item if it is a FeedMedia
     * object.
     */
    private static final String PREF_CURRENTLY_PLAYING_FEED_ID = "dev.russell.focuspodcast.preferences.lastPlayedFeedId";

    /**
     * Contains the id of the currently playing FeedMedia object or
     * NO_MEDIA_PLAYING if the currently playing media is no FeedMedia object.
     */
    private static final String PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID
            = "dev.russell.focuspodcast.preferences.lastPlayedFeedMediaId";

    /**
     * Type of the media object that is currently being played. This preference
     * is set to NO_MEDIA_PLAYING after playback has been completed and is set
     * as soon as the 'play' button is pressed.
     */
    private static final String PREF_CURRENTLY_PLAYING_MEDIA_TYPE
            = "dev.russell.focuspodcast.preferences.currentlyPlayingMedia";

    /**
     * True if last played media was a video.
     */
    private static final String PREF_CURRENT_EPISODE_IS_VIDEO = "dev.russell.focuspodcast.preferences.lastIsVideo";

    /**
     * The current player status as int.
     */
    private static final String PREF_CURRENT_PLAYER_STATUS = "dev.russell.focuspodcast.preferences.currentPlayerStatus";

    /**
     * The episode the video mode below belongs to ({@code String.valueOf} of its identifier), and
     * whether it is being watched (true) or listened to (false). It only ever describes the
     * episode being played: starting any other episode replaces or clears it, and so does the
     * end of playback.
     */
    private static final String PREF_VIDEO_MODE_MEDIA = "dev.russell.focuspodcast.preferences.videoModeMedia";
    private static final String PREF_VIDEO_MODE_SHOW_VIDEO = "dev.russell.focuspodcast.preferences.videoModeShowVideo";



    /**
     * Value of PREF_CURRENTLY_PLAYING_MEDIA if no media is playing.
     */
    public static final long NO_MEDIA_PLAYING = -1;

    /**
     * Value of PREF_CURRENT_PLAYER_STATUS if media player status is playing.
     */
    public static final int PLAYER_STATUS_PLAYING = 1;

    /**
     * Value of PREF_CURRENT_PLAYER_STATUS if media player status is paused.
     */
    public static final int PLAYER_STATUS_PAUSED = 2;

    /**
     * Value of PREF_CURRENT_PLAYER_STATUS if media player status is neither playing nor paused.
     */
    public static final int PLAYER_STATUS_OTHER = 3;

    private static PlaybackPreferences instance;
    private static SharedPreferences prefs;

    private PlaybackPreferences() {
    }

    public static void init(Context context) {
        instance = new PlaybackPreferences();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(instance);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_CURRENT_PLAYER_STATUS.equals(key)) {
            EventBus.getDefault().post(new PlayerStatusEvent());
        }
    }

    public static long getCurrentlyPlayingMediaType() {
        return prefs.getLong(PREF_CURRENTLY_PLAYING_MEDIA_TYPE, NO_MEDIA_PLAYING);
    }

    public static long getCurrentlyPlayingFeedMediaId() {
        return prefs.getLong(PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID, NO_MEDIA_PLAYING);
    }

    public static boolean getCurrentEpisodeIsVideo() {
        return prefs.getBoolean(PREF_CURRENT_EPISODE_IS_VIDEO, false);
    }

    /**
     * Whether the episode with this key is being watched (true) or listened to (false), or null
     * when no choice has been recorded for it.
     */
    @Nullable
    public static Boolean getVideoMode(@NonNull String mediaKey) {
        if (!mediaKey.equals(prefs.getString(PREF_VIDEO_MODE_MEDIA, null))) {
            return null;
        }
        return prefs.getBoolean(PREF_VIDEO_MODE_SHOW_VIDEO, true);
    }

    /** The recorded mode whichever episode it belongs to, or null when none is recorded. */
    @Nullable
    public static Boolean getVideoModeOfAnyMedia() {
        if (prefs.getString(PREF_VIDEO_MODE_MEDIA, null) == null) {
            return null;
        }
        return prefs.getBoolean(PREF_VIDEO_MODE_SHOW_VIDEO, true);
    }

    public static void clearVideoMode() {
        prefs.edit()
                .remove(PREF_VIDEO_MODE_MEDIA)
                .remove(PREF_VIDEO_MODE_SHOW_VIDEO)
                .apply();
    }

    public static void writeVideoMode(@NonNull String mediaKey, boolean showVideo) {
        prefs.edit()
                .putString(PREF_VIDEO_MODE_MEDIA, mediaKey)
                .putBoolean(PREF_VIDEO_MODE_SHOW_VIDEO, showVideo)
                .apply();
    }

    public static int getCurrentPlayerStatus() {
        return prefs.getInt(PREF_CURRENT_PLAYER_STATUS, PLAYER_STATUS_OTHER);
    }

    public static void writeNoMediaPlaying() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(PREF_CURRENTLY_PLAYING_MEDIA_TYPE, NO_MEDIA_PLAYING);
        editor.putLong(PREF_CURRENTLY_PLAYING_FEED_ID, NO_MEDIA_PLAYING);
        editor.putLong(PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID, NO_MEDIA_PLAYING);
        editor.putInt(PREF_CURRENT_PLAYER_STATUS, PLAYER_STATUS_OTHER);
        editor.remove(PREF_VIDEO_MODE_MEDIA);
        editor.remove(PREF_VIDEO_MODE_SHOW_VIDEO);
        editor.apply();
    }

    public static void writeMediaPlaying(Playable playable, PlayerStatus playerStatus) {
        SharedPreferences.Editor editor = prefs.edit();

        if (playable == null) {
            writeNoMediaPlaying();
        } else {
            editor.putLong(PREF_CURRENTLY_PLAYING_MEDIA_TYPE, playable.getPlayableType());
            editor.putBoolean(PREF_CURRENT_EPISODE_IS_VIDEO, playable.getMediaType() == MediaType.VIDEO);
            if (playable instanceof FeedMedia) {
                FeedMedia feedMedia = (FeedMedia) playable;
                editor.putLong(PREF_CURRENTLY_PLAYING_FEED_ID, feedMedia.getItem().getFeed().getId());
                editor.putLong(PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID, feedMedia.getId());
            } else {
                editor.putLong(PREF_CURRENTLY_PLAYING_FEED_ID, NO_MEDIA_PLAYING);
                editor.putLong(PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID, NO_MEDIA_PLAYING);
            }
            playable.writeToPreferences(editor);
        }
        editor.putInt(PREF_CURRENT_PLAYER_STATUS, getCurrentPlayerStatusAsInt(playerStatus));

        editor.apply();
    }

    public static void writePlayerStatus(PlayerStatus playerStatus) {

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREF_CURRENT_PLAYER_STATUS, getCurrentPlayerStatusAsInt(playerStatus));
        editor.apply();
    }

    private static int getCurrentPlayerStatusAsInt(PlayerStatus playerStatus) {
        int playerStatusAsInt;
        switch (playerStatus) {
            case PLAYING:
                playerStatusAsInt = PLAYER_STATUS_PLAYING;
                break;
            case PAUSED:
                playerStatusAsInt = PLAYER_STATUS_PAUSED;
                break;
            default:
                playerStatusAsInt = PLAYER_STATUS_OTHER;
        }
        return playerStatusAsInt;
    }
}
