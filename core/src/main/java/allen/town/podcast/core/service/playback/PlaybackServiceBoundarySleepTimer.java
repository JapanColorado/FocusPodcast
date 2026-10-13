package allen.town.podcast.core.service.playback;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import allen.town.podcast.core.pref.SleepTimerPreferences;
import allen.town.podcast.event.playback.SleepTimerMode;
import allen.town.podcast.event.playback.SleepTimerUpdatedEvent;
import allen.town.podcast.model.playback.Playable;
import allen.town.podcast.playback.base.PlayerStatus;

/**
 * The sleep timer modes that stop at a point in the content rather than after a duration: "end
 * of episode" and "end of chapter" ({@link SleepTimerEndCondition} decides where that point is).
 * The duration timer stays in {@link PlaybackServiceTaskManager}; {@link PlaybackService} makes
 * sure only one of the two runs.
 * <p>
 * It is driven by the service's one-second position ticker, so it only advances while playing.
 * Each tick posts a {@link SleepTimerUpdatedEvent} carrying the wall-clock time left (content
 * time divided by the speed), which the service also uses for the usual volume fade over the
 * last ten seconds; the device vibrates once when that window is entered, as for the duration
 * timer. Shake to reset does nothing for these modes: there is no duration to restart.
 * <p>
 * A chapter boundary is met precisely: when the next tick would overshoot it, a one-off check is
 * scheduled for the moment it is reached, and playback is paused there. The end of the episode is
 * not paused at: the episode completes normally and {@link #consumeAtEpisodeEnd()} tells the
 * player callback not to start the next one.
 */
class PlaybackServiceBoundarySleepTimer {
    private static final String TAG = "BoundarySleepTimer";
    /** The ticker's period; a boundary nearer than this gets its own precisely timed check. */
    private static final long TICK_MS = 1000;

    private final PlaybackService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable preciseCheck = this::tick;

    @Nullable
    private SleepTimerEndCondition condition;
    private long timeLeftMs = SleepTimerUpdatedEvent.TIME_LEFT_UNKNOWN;
    private boolean hasVibrated;

    PlaybackServiceBoundarySleepTimer(PlaybackService service) {
        this.service = service;
    }

    synchronized boolean isActive() {
        return condition != null;
    }

    @Nullable
    synchronized SleepTimerMode getMode() {
        SleepTimerEndCondition current = condition;
        return current == null ? null : current.getMode();
    }

    /** Wall-clock milliseconds left, {@link SleepTimerUpdatedEvent#TIME_LEFT_UNKNOWN} if unknown. */
    synchronized long getTimeLeft() {
        return timeLeftMs;
    }

    /** Arms the timer for {@code mode}, replacing a boundary timer that was running. */
    synchronized void start(SleepTimerMode mode) {
        Log.d(TAG, "sleep timer -> " + mode);
        handler.removeCallbacks(preciseCheck);
        SleepTimerEndCondition newCondition = new SleepTimerEndCondition(mode);
        condition = newCondition;
        hasVibrated = false;
        timeLeftMs = compute(newCondition);
        EventBus.getDefault().post(SleepTimerUpdatedEvent.justEnabled(timeLeftMs, mode));
    }

    /** Stops the timer without pausing; does nothing if it is not running. */
    synchronized void cancel() {
        if (condition == null) {
            return;
        }
        Log.d(TAG, "cancelled");
        clear();
        EventBus.getDefault().post(SleepTimerUpdatedEvent.cancelled());
    }

    /** Playback resumed or a different media started: the target follows the new position. */
    synchronized void onPlaybackStartedOrMediaChanged() {
        if (condition != null) {
            condition.resetAnchor();
        }
    }

    /**
     * Called (on the player's thread) when an episode has played to its end. If a boundary timer
     * is running it is used up here and true is returned: the caller then does not start the next
     * episode.
     */
    synchronized boolean consumeAtEpisodeEnd() {
        SleepTimerEndCondition current = condition;
        if (current == null) {
            return false;
        }
        Log.d(TAG, "episode ended, stopping instead of playing on");
        clear();
        EventBus.getDefault().post(SleepTimerUpdatedEvent.updated(0, current.getMode()));
        return true;
    }

    /** One step of the timer, on the main thread: from the position ticker or a precise check. */
    synchronized void tick() {
        SleepTimerEndCondition current = condition;
        if (current == null || service.getStatus() != PlayerStatus.PLAYING) {
            return;
        }
        timeLeftMs = compute(current);
        if (current.isDue()) {
            Log.d(TAG, "chapter boundary reached");
            clear();
            // pause first: the "over" event restores the faded volume
            service.pause(true, true);
            EventBus.getDefault().post(SleepTimerUpdatedEvent.updated(0, current.getMode()));
            return;
        }
        EventBus.getDefault().post(SleepTimerUpdatedEvent.updated(timeLeftMs, current.getMode()));
        if (timeLeftMs == SleepTimerUpdatedEvent.TIME_LEFT_UNKNOWN) {
            return;
        }
        if (timeLeftMs < PlaybackServiceTaskManager.SleepTimer.NOTIFICATION_THRESHOLD) {
            vibrateOnce();
        } else {
            hasVibrated = false;
        }
        handler.removeCallbacks(preciseCheck);
        if (!current.stopsAtEpisodeEnd() && timeLeftMs <= TICK_MS) {
            handler.postDelayed(preciseCheck, timeLeftMs);
        }
    }

    private long compute(SleepTimerEndCondition current) {
        Playable playable = service.getPlayable();
        float speed = service.getCurrentPlaybackSpeed();
        long remaining = current.update(SystemClock.elapsedRealtime(),
                service.getCurrentPosition(), service.getDuration(), speed,
                playable == null ? null : SleepTimerEndCondition.chapterStarts(playable.getChapters()));
        long wall = SleepTimerEndCondition.toWallClock(remaining, speed);
        return wall == SleepTimerEndCondition.UNKNOWN ? SleepTimerUpdatedEvent.TIME_LEFT_UNKNOWN : wall;
    }

    private void vibrateOnce() {
        if (hasVibrated || !SleepTimerPreferences.vibrate()) {
            return;
        }
        Vibrator vibrator = (Vibrator) service.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            vibrator.vibrate(500);
            hasVibrated = true;
        }
    }

    private void clear() {
        handler.removeCallbacks(preciseCheck);
        condition = null;
        timeLeftMs = SleepTimerUpdatedEvent.TIME_LEFT_UNKNOWN;
        hasVibrated = false;
    }
}
