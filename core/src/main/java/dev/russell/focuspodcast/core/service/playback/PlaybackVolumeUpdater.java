package dev.russell.focuspodcast.core.service.playback;

import dev.russell.focuspodcast.model.feed.FeedMedia;
import dev.russell.focuspodcast.model.feed.FeedPreferences;
import dev.russell.focuspodcast.model.feed.VolumeAdaptionSetting;
import dev.russell.focuspodcast.model.playback.Playable;
import dev.russell.focuspodcast.playback.base.PlaybackServiceMediaPlayer;
import dev.russell.focuspodcast.playback.base.PlayerStatus;

class PlaybackVolumeUpdater {

    public void updateVolumeIfNecessary(PlaybackServiceMediaPlayer mediaPlayer, long feedId,
                                        VolumeAdaptionSetting volumeAdaptionSetting) {
        Playable playable = mediaPlayer.getPlayable();

        if (playable instanceof FeedMedia) {
            updateFeedMediaVolumeIfNecessary(mediaPlayer, feedId, volumeAdaptionSetting, (FeedMedia) playable);
        }
    }

    private void updateFeedMediaVolumeIfNecessary(PlaybackServiceMediaPlayer mediaPlayer, long feedId,
                                                  VolumeAdaptionSetting volumeAdaptionSetting, FeedMedia feedMedia) {
        if (feedMedia.getItem().getFeed().getId() == feedId) {
            FeedPreferences preferences = feedMedia.getItem().getFeed().getPreferences();
            preferences.setVolumeAdaptionSetting(volumeAdaptionSetting);

            if (mediaPlayer.getPlayerStatus() == PlayerStatus.PLAYING) {
                forceUpdateVolume(mediaPlayer);
            }
        }
    }

    private void forceUpdateVolume(PlaybackServiceMediaPlayer mediaPlayer) {
        mediaPlayer.pause(false, false);
        mediaPlayer.resume();
    }

}
