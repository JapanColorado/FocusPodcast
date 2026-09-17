package allen.town.podcast.core.service.playback;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.playback.base.PlayerStatus;

/**
 * Owns the audio focus of {@link LocalPSMP}: the {@link AudioFocusRequestCompat} it asks for and
 * abandons, the change listener that ducks, pauses or resumes playback when another app takes the
 * focus, the "did we pause because of a transient loss" flag, and the delayed callback that turns
 * a transient loss which never came back into a real pause. {@link LocalPSMP} keeps every playback
 * operation and is called back into from here.
 */
class LocalPSMPAudioFocus {
    private static final String TAG = "LocalMediaPlayer";

    private final LocalPSMP player;
    private final AudioManager audioManager;
    private final AudioFocusRequestCompat audioFocusRequest;
    private final Handler audioFocusCanceller;

    private volatile boolean pausedBecauseOfTransientAudiofocusLoss;

    LocalPSMPAudioFocus(LocalPSMP player, Context context) {
        this.player = player;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.audioFocusCanceller = new Handler(Looper.getMainLooper());
        this.pausedBecauseOfTransientAudiofocusLoss = false;

        AudioAttributesCompat audioAttributes = new AudioAttributesCompat.Builder()
                .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                .build();
        audioFocusRequest = new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setWillPauseWhenDucked(true)
                .build();
    }

    int request() {
        return AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest);
    }

    void abandon() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest);
    }

    void clearTransientLossFlag() {
        pausedBecauseOfTransientAudiofocusLoss = false;
    }

    /**
     * The delayed "still no audio focus" callback holds the player; drop it so it cannot
     * fire (and call pause()) after the player has been torn down.
     */
    void cancelPendingLossCallback() {
        audioFocusCanceller.removeCallbacksAndMessages(null);
    }

    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener =
            new AudioManager.OnAudioFocusChangeListener() {

        @Override
        public void onAudioFocusChange(final int focusChange) {
            if (player.isShutDown) {
                return;
            }
            if (!PlaybackService.isRunning) {
                abandon();
                Log.d(TAG, "onAudioFocusChange and PlaybackService is no longer running");
                return;
            }

            player.executor.submit(() -> {
                player.playerLock.lock();
                Log.i(TAG, "there is a call");
                player.playerLock.lock();
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    Log.d(TAG, "Lost audio focus");
                    player.pause(true, false);
                    player.notifyShouldStop();
                } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                        && !Prefs.shouldPauseForFocusLoss()) {
                    if (player.status() == PlayerStatus.PLAYING) {
                        Log.d(TAG, "Lost audio focus temporarily. Ducking...");
                        player.setVolumeSync(0.25f, 0.25f);
                        pausedBecauseOfTransientAudiofocusLoss = false;
                    }
                } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                        || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    if (player.status() == PlayerStatus.PLAYING) {
                        Log.d(TAG, "Lost audio focus temporarily. Pausing...");
                        player.mediaPlayer.pause(); // Pause without telling the PlaybackService
                        pausedBecauseOfTransientAudiofocusLoss = true;

                        audioFocusCanceller.removeCallbacksAndMessages(null);
                        audioFocusCanceller.postDelayed(() -> {
                            if (pausedBecauseOfTransientAudiofocusLoss) {
                                // Still did not get back the audio focus. Now actually pause.
                                player.pause(true, false);
                            }
                        }, 30000);
                    }
                } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                    Log.d(TAG, "Gained audio focus");
                    audioFocusCanceller.removeCallbacksAndMessages(null);
                    if (pausedBecauseOfTransientAudiofocusLoss) { // we paused => play now
                        player.mediaPlayer.start();
                    } else { // we ducked => raise audio level back
                        player.setVolumeSync(1.0f, 1.0f);
                    }
                    pausedBecauseOfTransientAudiofocusLoss = false;
                }
                player.playerLock.unlock();
            });
        }
    };
}
