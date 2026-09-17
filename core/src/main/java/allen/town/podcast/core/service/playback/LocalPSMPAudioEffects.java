package allen.town.podcast.core.service.playback;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import allen.town.podcast.core.feed.util.AudioEffectUtils;
import allen.town.podcast.event.playback.SpeedChangedEvent;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.model.feed.FeedPreferences;
import allen.town.podcast.model.feed.VolumeAdaptionSetting;
import allen.town.podcast.model.playback.MediaType;
import allen.town.podcast.model.playback.Playable;
import allen.town.podcast.playback.base.PlayerStatus;

/**
 * Owns how loud, how fast and with which effects {@link LocalPSMP} plays: the playback speed and
 * skip-silence parameters (including the {@link SpeedChangedEvent} that announces a speed change),
 * the per-feed volume adaption applied to every volume change, and the loudness enhancement and
 * stereo-to-mono downmix effects. {@link LocalPSMP} keeps all the public entry points of
 * {@code PlaybackServiceMediaPlayer} and delegates their bodies here.
 */
class LocalPSMPAudioEffects {
    private static final String TAG = "LocalMediaPlayer";

    private final LocalPSMP player;

    LocalPSMPAudioEffects(LocalPSMP player) {
        this.player = player;
    }

    /**
     * Sets the playback speed.
     * This method is executed on the caller's thread.
     */
    private void setSpeedSyncAndSkipSilence(float speed, boolean skipSilence) {
        player.playerLock.lock();
        Log.d(TAG, "speed was set to " + speed + " skipSilence -> " + skipSilence);
        EventBus.getDefault().post(new SpeedChangedEvent(speed));
        player.mediaPlayer.setPlaybackParams(speed, skipSilence);
        player.playerLock.unlock();
    }

    /**
     * Applies the loudness and downmix effects configured for the current media.
     * This method is executed on an internal executor service.
     */
    void setAudioEffect() {
        player.executor.submit(() -> {
            player.playerLock.lock();
            boolean isLoudness = AudioEffectUtils.isLoudnessEnable(player.media);
            boolean isMono = AudioEffectUtils.isMonoEnable(player.media);
            Log.d(TAG, "set AudioEffect loudness -> " + isLoudness + " downmix ->" + isMono);
            player.mediaPlayer.setLoudness(isLoudness);
            player.mediaPlayer.setDownmix(isMono);
            player.playerLock.unlock();
        });

    }

    /**
     * Sets the playback speed.
     * This method is executed on an internal executor service.
     */
    void setPlaybackParams(final float speed, final boolean skipSilence) {
        player.executor.submit(() -> setSpeedSyncAndSkipSilence(speed, skipSilence));
    }

    /**
     * Returns the current playback speed. If the playback speed could not be retrieved, 1 is returned.
     */
    float getPlaybackSpeed() {
        if (!player.playerLock.tryLock()) {
            return 1;
        }

        float retVal = 1;
        if ((player.status() == PlayerStatus.PLAYING
                || player.status() == PlayerStatus.PAUSED
                || player.status() == PlayerStatus.INITIALIZED
                || player.status() == PlayerStatus.PREPARED)) {
            retVal = player.mediaPlayer.getCurrentSpeedMultiplier();
        }
        player.playerLock.unlock();
        return retVal;
    }

    /**
     * Sets the playback volume.
     * This method is executed on an internal executor service.
     */
    void setVolume(final float volumeLeft, float volumeRight) {
        player.executor.submit(() -> setVolumeSync(volumeLeft, volumeRight));
    }

    /**
     * Sets the playback volume.
     * This method is executed on the caller's thread.
     */
    void setVolumeSync(float volumeLeft, float volumeRight) {
        player.playerLock.lock();
        Playable playable = player.getPlayable();
        if (playable instanceof FeedMedia) {
            FeedMedia feedMedia = (FeedMedia) playable;
            FeedPreferences preferences = feedMedia.getItem().getFeed().getPreferences();
            VolumeAdaptionSetting volumeAdaptionSetting = preferences.getVolumeAdaptionSetting();
            float adaptionFactor = volumeAdaptionSetting.getAdaptionFactor();
            volumeLeft *= adaptionFactor;
            volumeRight *= adaptionFactor;
        }
        player.mediaPlayer.setVolume(volumeLeft, volumeRight);
        Log.d(TAG, "volume was set to " + volumeLeft + " " + volumeRight);
        player.playerLock.unlock();
    }

    /**
     * Returns true if the mediaplayer can mix stereo down to mono
     */
    boolean canDownmix() {
        boolean retVal = false;
        if (player.mediaPlayer != null && player.media != null
                && player.media.getMediaType() == MediaType.AUDIO) {
            retVal = player.mediaPlayer.canDownmix();
        }
        return retVal;
    }

    void setDownmix(boolean enable) {
        player.playerLock.lock();
        if (player.media != null && player.media.getMediaType() == MediaType.AUDIO) {
            player.mediaPlayer.setDownmix(enable);
            Log.d(TAG, "downmix was set to " + enable);
        }
        player.playerLock.unlock();
    }

    void setLoudness(boolean enable) {
        player.playerLock.lock();
        if (player.media != null && player.media.getMediaType() == MediaType.AUDIO) {
            player.mediaPlayer.setLoudness(enable);
            Log.d(TAG, "loudness was set to " + enable);
        }
        player.playerLock.unlock();
    }
}
