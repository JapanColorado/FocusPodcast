package allen.town.podcast.core.service.playback;

import android.util.Log;

import java.util.concurrent.Future;

import allen.town.podcast.model.playback.Playable;
import allen.town.podcast.playback.base.PlayerStatus;

/**
 * Owns what {@link LocalPSMP} does when the current media stops playing: it persists the reached
 * position, resets and un-focuses the player, asks the service for the next episode in the queue
 * and either starts it (continuing automatically only if playback was running) or moves the player
 * into the STOPPED state, and finally hands the finished episode to the post-playback callback.
 * {@link LocalPSMP} keeps the {@code endPlayback} override and delegates its body here.
 */
class LocalPSMPPlaybackEnder {
    private static final String TAG = "LocalMediaPlayer";

    private final LocalPSMP player;

    LocalPSMPPlaybackEnder(LocalPSMP player) {
        this.player = player;
    }

    Future<?> endPlayback(final boolean hasEnded, final boolean wasSkipped,
                          final boolean shouldContinue, final boolean toStoppedState) {
        return player.executor.submit(() -> {
            player.playerLock.lock();
            player.releaseWifiLock();

            boolean isPlaying = player.status() == PlayerStatus.PLAYING;

            // we're relying on the position stored in the Playable object for post-playback processing
            if (player.media != null) {
                int position = player.getPosition();
                if (position >= 0) {
                    player.media.setPosition(position);
                }
            }

            if (player.mediaPlayer != null) {
                player.mediaPlayer.reset();
            }

            player.abandonAudioFocus();

            final Playable currentMedia = player.media;
            Playable nextMedia = null;

            if (shouldContinue) {
                // Load next episode if previous episode was in the queue and if there
                // is an episode in the queue left.
                // Start playback immediately if continuous playback is enabled
                nextMedia = player.playerCallback().getNextInQueue(currentMedia);
                boolean playNextEpisode = isPlaying && nextMedia != null;
                if (playNextEpisode) {
                    Log.d(TAG, "next episode will start later");
                } else if (nextMedia == null) {
                    Log.d(TAG, "no more episodes available to play");
                } else {
                    Log.d(TAG, "load next episode, but not playing automatically.");
                }

                if (nextMedia != null) {
                    player.playerCallback().onPlaybackEnded(nextMedia.getMediaType(), !playNextEpisode);
                    // setting media to null signals to playMediaObject() that we're taking care of post-playback processing
                    player.media = null;
                    player.playMediaObjectSync(nextMedia, false, !nextMedia.localFileAvailable(),
                            playNextEpisode, playNextEpisode);
                }
            }
            if (shouldContinue || toStoppedState) {
                if (nextMedia == null) {
                    player.playerCallback().onPlaybackEnded(null, true);
                    stop();
                }
                final boolean hasNext = nextMedia != null;

                player.executor.submit(() ->
                        player.playerCallback().onPostPlayback(currentMedia, hasEnded, wasSkipped, hasNext));
            } else if (isPlaying) {
                player.playerCallback().onPlaybackPause(currentMedia, currentMedia.getPosition());
            }
            player.playerLock.unlock();
        });
    }

    /**
     * Moves the LocalPSMP into STOPPED state. This call is only valid if the player is currently in
     * INDETERMINATE state, for example after a call to endPlayback.
     * This method will only take care of changing the PlayerStatus of this object! Other tasks like
     * abandoning audio focus have to be done with other methods.
     */
    private void stop() {
        player.executor.submit(() -> {
            player.playerLock.lock();
            player.releaseWifiLock();

            if (player.status() == PlayerStatus.INDETERMINATE) {
                player.updateStatus(PlayerStatus.STOPPED, null);
            } else {
                Log.d(TAG, "Ignored call to stop: Current player state is: " + player.status());
            }
            player.playerLock.unlock();

        });
    }
}
