package dev.russell.yagi.core.service.playback;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import dev.russell.yagi.core.feed.util.AudioEffectUtils;
import dev.russell.yagi.event.playback.SpeedChangedEvent;
import dev.russell.yagi.model.feed.FeedMedia;
import dev.russell.yagi.model.feed.FeedPreferences;
import dev.russell.yagi.model.feed.VolumeAdaptionSetting;
import dev.russell.yagi.model.playback.MediaType;
import dev.russell.yagi.model.playback.Playable;
import dev.russell.yagi.playback.base.PlayerStatus;

/**
 * Owns how loud, how fast and with which effects {@link LocalPSMP} plays: the playback speed and
 * skip-silence parameters (including the {@link SpeedChangedEvent} that announces a speed change),
 * the per-feed volume adaption applied to every volume change, and the Clear voice enhancer,
 * volume leveling and stereo-to-mono downmix effects. {@link LocalPSMP} keeps all the public entry points of
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
     * Applies the Clear voice, volume leveling and downmix effects configured for the current media.
     * This method is executed on an internal executor service.
     */
    void setAudioEffect() {
        player.executor.submit(() -> {
            player.playerLock.lock();
            int clearVoiceLevel = AudioEffectUtils.getClearVoiceLevel(player.media);
            int volumeLeveling = AudioEffectUtils.getVolumeLeveling(player.media);
            boolean isMono = AudioEffectUtils.isMonoEnable(player.media);
            Log.d(TAG, "set AudioEffect clear voice -> " + clearVoiceLevel + " leveling -> " + volumeLeveling
                    + " downmix ->" + isMono);
            player.mediaPlayer.setClearVoiceLevel(clearVoiceLevel);
            player.mediaPlayer.setVolumeLeveling(volumeLeveling);
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

    void setClearVoiceLevel(int level) {
        player.playerLock.lock();
        if (player.media != null && player.media.getMediaType() == MediaType.AUDIO) {
            player.mediaPlayer.setClearVoiceLevel(level);
            Log.d(TAG, "clear voice was set to " + level);
        }
        player.playerLock.unlock();
    }

    /** Unlike the other effects this also applies to video episodes, whose audio it levels too. */
    void setVolumeLeveling(int mode) {
        player.playerLock.lock();
        if (player.mediaPlayer != null) {
            player.mediaPlayer.setVolumeLeveling(mode);
            Log.d(TAG, "volume leveling was set to " + mode);
        }
        player.playerLock.unlock();
    }
}
