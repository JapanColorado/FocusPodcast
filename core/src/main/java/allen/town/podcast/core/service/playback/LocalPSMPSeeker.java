package allen.town.podcast.core.service.playback;

import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import allen.town.podcast.playback.base.PlayerStatus;

/**
 * Owns the seeking of {@link LocalPSMP}: the synchronous seek that waits (with a timeout) on the
 * player's seek-complete callback through a {@link CountDownLatch}, the player status the seek
 * interrupted and has to restore afterwards, and the handling of that callback. {@link LocalPSMP}
 * keeps the public {@code seekTo} / {@code seekDelta} entry points and delegates here.
 */
class LocalPSMPSeeker {
    private static final String TAG = "LocalMediaPlayer";

    private final LocalPSMP player;

    private volatile PlayerStatus statusBeforeSeeking;
    private CountDownLatch seekLatch;

    LocalPSMPSeeker(LocalPSMP player) {
        this.player = player;
        this.statusBeforeSeeking = null;
    }

    /**
     * Seeks to the specified position. If the PSMP object is in an invalid state, this method will
     * do nothing.
     *
     * @param t The position to seek to in milliseconds. t &lt; 0 will be interpreted as t = 0
     *          <p/>
     *          This method is executed on the caller's thread.
     */
    void seekToSync(int t) {
        if (t < 0) {
            t = 0;
        }

        if (t >= player.getDuration()) {
            Log.d(TAG, "Seek reached end of file, skipping to next episode");
            player.skip();
            return;
        }

        player.playerLock.lock();

        if (player.status() == PlayerStatus.PLAYING
                || player.status() == PlayerStatus.PAUSED
                || player.status() == PlayerStatus.PREPARED) {
            if (seekLatch != null && seekLatch.getCount() > 0) {
                try {
                    seekLatch.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    // Safe to continue: the new seek below supersedes the one we were waiting for.
                    // Restore the flag so the executor still sees that it was interrupted.
                    Thread.currentThread().interrupt();
                    Log.e(TAG, "Interrupted while waiting for the previous seek to finish", e);
                }
            }
            seekLatch = new CountDownLatch(1);
            statusBeforeSeeking = player.status();
            player.updateStatus(PlayerStatus.SEEKING, player.media, player.getPosition());
            player.mediaPlayer.seekTo(t);
            if (statusBeforeSeeking == PlayerStatus.PREPARED) {
                player.media.setPosition(t);
            }
            try {
                seekLatch.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Safe to continue: the seek has already been handed to the player, we only stop
                // waiting for its callback. Restore the flag for the executor.
                Thread.currentThread().interrupt();
                Log.e(TAG, "Interrupted while waiting for the seek to complete", e);
            }
        } else if (player.status() == PlayerStatus.INITIALIZED) {
            player.media.setPosition(t);
            player.startWhenPrepared.set(false);
            player.prepare();
        }
        player.playerLock.unlock();
    }

    /**
     * Called by the media player once a seek has finished.
     */
    void onSeekComplete() {
        if (seekLatch != null) {
            seekLatch.countDown();
        }

        Runnable r = () -> {
            player.playerLock.lock();
            if (player.status() == PlayerStatus.PLAYING) {
                player.playerCallback().onPlaybackStart(player.media, player.getPosition());
            }
            if (player.status() == PlayerStatus.SEEKING) {
                player.updateStatus(statusBeforeSeeking, player.media, player.getPosition());
            }
            player.playerLock.unlock();
        };

        if (player.useCallerThread.get()) {
            r.run();
        } else {
            player.executor.submit(r);
        }
    }
}
