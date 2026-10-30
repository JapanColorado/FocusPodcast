package dev.russell.yagi.storage.db.mapper;

import android.database.Cursor;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import dev.russell.yagi.model.feed.FeedFilter;
import dev.russell.yagi.model.feed.FeedPreferences;
import dev.russell.yagi.model.feed.VolumeAdaptionSetting;
import dev.russell.yagi.storage.db.Db;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Converts a {@link Cursor} to a {@link FeedPreferences} object.
 */
public abstract class FeedPreferencesCursorMapper {
    /**
     * Create a {@link FeedPreferences} instance from a database row (cursor).
     */
    @NonNull
    public static FeedPreferences convert(@NonNull Cursor cursor) {
        int indexId = cursor.getColumnIndex(Db.KEY_ID);
        int indexAutoDownload = cursor.getColumnIndex(Db.KEY_AUTO_DOWNLOAD_ENABLED);
        int indexSkipSilence = cursor.getColumnIndex(Db.KEY_SKIP_SILENCE_ENABLED);
        int indexMono = cursor.getColumnIndex(Db.KEY_MONO_ENABLED);
        int indexUseFeedEffect = cursor.getColumnIndex(Db.KEY_USE_FEED_EFFECT);
        int indexAutoRefresh = cursor.getColumnIndex(Db.KEY_KEEP_UPDATED);
        int indexAutoDeleteAction = cursor.getColumnIndex(Db.KEY_AUTO_DELETE_ACTION);
        int indexVolumeAdaption = cursor.getColumnIndex(Db.KEY_FEED_VOLUME_ADAPTION);
        int indexUsername = cursor.getColumnIndex(Db.KEY_USERNAME);
        int indexPassword = cursor.getColumnIndex(Db.KEY_PASSWORD);
        int indexIncludeFilter = cursor.getColumnIndex(Db.KEY_INCLUDE_FILTER);
        int indexExcludeFilter = cursor.getColumnIndex(Db.KEY_EXCLUDE_FILTER);
        int indexMinimalDurationFilter = cursor.getColumnIndex(Db.KEY_MINIMAL_DURATION_FILTER);
        int indexFeedPlaybackSpeed = cursor.getColumnIndex(Db.KEY_FEED_PLAYBACK_SPEED);
        int indexAutoSkipIntro = cursor.getColumnIndex(Db.KEY_FEED_SKIP_INTRO);
        int indexAutoSkipEnding = cursor.getColumnIndex(Db.KEY_FEED_SKIP_ENDING);
        int indexEpisodeNotification = cursor.getColumnIndex(Db.KEY_EPISODE_NOTIFICATION);
        int indexTags = cursor.getColumnIndex(Db.KEY_FEED_TAGS);

        long feedId = cursor.getLong(indexId);
        boolean autoDownload = cursor.getInt(indexAutoDownload) > 0;
        boolean autoRefresh = cursor.getInt(indexAutoRefresh) > 0;
        boolean skipSilence = cursor.getInt(indexSkipSilence) > 0;
        // Version 8 column; a projection without it reads "off".
        int indexClearVoice = cursor.getColumnIndex(Db.KEY_CLEAR_VOICE);
        int clearVoiceLevel = indexClearVoice < 0 ? 0 : cursor.getInt(indexClearVoice);
        boolean mono = cursor.getInt(indexMono) > 0;
        boolean isUseFeedEffect = cursor.getInt(indexUseFeedEffect) > 0;

        int autoDeleteActionIndex = cursor.getInt(indexAutoDeleteAction);
        FeedPreferences.AutoDeleteAction autoDeleteAction =
                FeedPreferences.AutoDeleteAction.values()[autoDeleteActionIndex];
        int volumeAdaptionValue = cursor.getInt(indexVolumeAdaption);
        VolumeAdaptionSetting volumeAdaptionSetting = VolumeAdaptionSetting.fromInteger(volumeAdaptionValue);
        String username = cursor.getString(indexUsername);
        String password = cursor.getString(indexPassword);
        String includeFilter = cursor.getString(indexIncludeFilter);
        String excludeFilter = cursor.getString(indexExcludeFilter);
        int minimalDurationFilter = cursor.getInt(indexMinimalDurationFilter);
        float feedPlaybackSpeed = cursor.getFloat(indexFeedPlaybackSpeed);
        int feedAutoSkipIntro = cursor.getInt(indexAutoSkipIntro);
        int feedAutoSkipEnding = cursor.getInt(indexAutoSkipEnding);
        boolean showNotification = cursor.getInt(indexEpisodeNotification) > 0;
        String tagsString = cursor.getString(indexTags);
        if (TextUtils.isEmpty(tagsString)) {
            tagsString = FeedPreferences.TAG_ROOT;
        }
        // NULL means "follow the global default". A projection without the column (getColumnIndex
        // returns -1) reads the same way.
        int indexAdSkip = cursor.getColumnIndex(Db.KEY_FEED_AD_SKIP_OVERRIDE);
        Boolean adSkipOverride = indexAdSkip < 0 || cursor.isNull(indexAdSkip)
                ? null : cursor.getInt(indexAdSkip) > 0;
        int indexVideo = cursor.getColumnIndex(Db.KEY_FEED_VIDEO_OVERRIDE);
        Boolean videoOverride = indexVideo < 0 || cursor.isNull(indexVideo)
                ? null : cursor.getInt(indexVideo) > 0;

        FeedPreferences preferences = new FeedPreferences(feedId,
                autoDownload,
                autoRefresh,
                autoDeleteAction,
                volumeAdaptionSetting,
                username,
                password,
                new FeedFilter(includeFilter, excludeFilter, minimalDurationFilter),
                feedPlaybackSpeed,
                feedAutoSkipIntro,
                feedAutoSkipEnding,
                showNotification,
                new HashSet<>(Arrays.asList(tagsString.split(FeedPreferences.TAG_SEPARATOR))),skipSilence, clearVoiceLevel, mono, isUseFeedEffect);
        preferences.setAdSkipOverride(adSkipOverride);
        preferences.setVideoOverride(videoOverride);
        // Version 7 column; a projection without it reads "not an audiobook".
        int indexAudiobook = cursor.getColumnIndex(Db.KEY_AUDIOBOOK);
        preferences.setAudiobook(indexAudiobook >= 0 && cursor.getInt(indexAudiobook) > 0);
        // Version 13 column; a projection without it reads "off".
        int indexVolumeLeveling = cursor.getColumnIndex(Db.KEY_VOLUME_LEVELING);
        preferences.setVolumeLeveling(indexVolumeLeveling < 0 ? 0 : cursor.getInt(indexVolumeLeveling));
        return preferences;
    }
}
