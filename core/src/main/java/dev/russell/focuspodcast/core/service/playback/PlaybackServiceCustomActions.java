package dev.russell.focuspodcast.core.service.playback;

import android.content.Context;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import dev.russell.focuspodcast.core.R;
import dev.russell.focuspodcast.core.pref.Prefs;
import dev.russell.focuspodcast.event.playback.SleepTimerMode;
import dev.russell.focuspodcast.model.playback.Playable;

/**
 * The user's extra playback buttons ({@link NotificationCustomAction}) for {@link PlaybackService}:
 * which of them apply right now, how they are published (media session custom actions for the
 * lock screen, Android 13+ media controls, Android Auto and Wear; notification actions are built
 * by {@link PlaybackServiceNotificationBuilder} from {@link #getVisible()}), and what they do.
 *
 * <p>Applicability changes with the position (chapters, ad segments), the sleep timer and the
 * speed. {@link #refreshIfChanged()} is called from the one-second ticker and from the events
 * that change those, and re-publishes the media session state and the notification only when the
 * set of visible buttons (or the speed shown on the speed button) actually changed. Main thread
 * only.
 */
class PlaybackServiceCustomActions {
    private static final String TAG = "PlaybackService";

    /** What "sleep timer +15 min" starts or adds. */
    static final long SLEEP_TIMER_EXTENSION_MS = TimeUnit.MINUTES.toMillis(15);

    private final PlaybackService service;

    /** What the media session state was last published with, see {@link #signature}. */
    @Nullable
    private String publishedSignature;

    PlaybackServiceCustomActions(PlaybackService service) {
        this.service = service;
    }

    /** The buttons that apply now, in the user's order. */
    @NonNull
    List<NotificationCustomAction> getVisible() {
        List<NotificationCustomAction> selected = Prefs.getNotificationCustomButtons();
        if (selected.isEmpty() || service.getPlayable() == null) {
            return Collections.emptyList();
        }
        return NotificationCustomActionResolver.resolve(selected, currentState());
    }

    private NotificationCustomActionResolver.State currentState() {
        Playable playable = service.getPlayable();
        int position = service.getCurrentPosition();
        boolean hasNextChapter = playable != null && NotificationCustomActionResolver.nextChapterStart(
                SleepTimerEndCondition.chapterStarts(playable.getChapters()), position)
                != NotificationCustomActionResolver.NO_CHAPTER;
        return new NotificationCustomActionResolver.State(
                hasNextChapter,
                service.adSkipper.isInSkippableSegment(),
                service.getSleepTimerMode(),
                !Prefs.getPlaybackSpeedArray().isEmpty());
    }

    /**
     * Adds the visible buttons to a media session state being built, and remembers them as
     * published.
     *
     * @param forWear mark them to be shown on Android Wear
     */
    void addTo(@NonNull PlaybackStateCompat.Builder sessionState, boolean forWear) {
        List<NotificationCustomAction> visible = getVisible();
        float speed = service.getCurrentPlaybackSpeed();
        for (NotificationCustomAction action : visible) {
            String title = getTitle(service, action, speed);
            if (forWear) {
                WearMediaSession.sessionStateAddActionForWear(sessionState, action.getSessionAction(),
                        title, getIcon(action));
            } else {
                sessionState.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                        action.getSessionAction(), title, getIcon(action)).build());
            }
        }
        publishedSignature = signature(visible, speed);
    }

    /**
     * Re-publishes the media session state and the notification if the visible buttons changed
     * since they were last published.
     */
    void refreshIfChanged() {
        if (service.mediaPlayer == null) {
            return;
        }
        String current = signature(getVisible(), service.getCurrentPlaybackSpeed());
        if (current.equals(publishedSignature)) {
            return;
        }
        republish();
    }

    /** Re-publishes unconditionally, e.g. after the selection changed in the settings. */
    void republish() {
        if (service.mediaPlayer == null) {
            return;
        }
        service.mediaSessionHolder.updatePlaybackState(service.getStatus());
        Playable playable = service.getPlayable();
        if (playable != null) {
            service.refreshNotification(playable);
        }
    }

    private static String signature(List<NotificationCustomAction> visible, float speed) {
        StringBuilder builder = new StringBuilder();
        for (NotificationCustomAction action : visible) {
            builder.append(action.name()).append(';');
            if (action == NotificationCustomAction.CYCLE_SPEED) {
                builder.append(formatSpeed(speed)).append(';');
            }
        }
        return builder.toString();
    }

    /**
     * Runs a custom action from the media session or a notification button.
     *
     * @return false if {@code sessionAction} is not one of these buttons or nothing is playing
     */
    boolean perform(@Nullable String sessionAction) {
        NotificationCustomAction action = NotificationCustomAction.fromSessionAction(sessionAction);
        Playable playable = service.getPlayable();
        if (action == null || playable == null || service.mediaPlayer == null) {
            return false;
        }
        Log.d(TAG, "custom action " + action);
        switch (action) {
            case NEXT_CHAPTER:
                long target = NotificationCustomActionResolver.nextChapterStart(
                        SleepTimerEndCondition.chapterStarts(playable.getChapters()),
                        service.getCurrentPosition());
                if (target != NotificationCustomActionResolver.NO_CHAPTER) {
                    service.seekTo((int) target);
                }
                break;
            case CYCLE_SPEED:
                float next = NotificationCustomActionResolver.nextSpeed(
                        Prefs.getPlaybackSpeedArray(), service.getCurrentPlaybackSpeed());
                // The same path as choosing a speed in the player: per podcast, or the default.
                service.setSpeedForCurrentMedia(next);
                break;
            case SLEEP_TIMER_EXTEND:
                extendSleepTimer();
                break;
            case SKIP_AD:
                service.adSkipper.skipCurrentSegment();
                break;
            default:
                return false;
        }
        refreshIfChanged();
        return true;
    }

    private void extendSleepTimer() {
        SleepTimerMode mode = service.getSleepTimerMode();
        if (mode == null) {
            service.setSleepTimer(SLEEP_TIMER_EXTENSION_MS);
        } else if (mode == SleepTimerMode.DURATION) {
            service.setSleepTimer(service.getSleepTimerTimeLeft() + SLEEP_TIMER_EXTENSION_MS);
        }
        // End of episode / end of chapter cannot be extended; the button is hidden for them.
    }

    /** The button's title (also its content description and the Android Auto label). */
    @NonNull
    static String getTitle(@NonNull Context context, @NonNull NotificationCustomAction action, float speed) {
        switch (action) {
            case NEXT_CHAPTER:
                return context.getString(R.string.custom_action_next_chapter);
            case CYCLE_SPEED:
                return context.getString(R.string.custom_action_speed_label, formatSpeed(speed));
            case SLEEP_TIMER_EXTEND:
                return context.getString(R.string.custom_action_sleep_extend);
            case SKIP_AD:
                return context.getString(R.string.custom_action_skip_ad);
            default:
                return action.name();
        }
    }

    @DrawableRes
    static int getIcon(@NonNull NotificationCustomAction action) {
        switch (action) {
            case NEXT_CHAPTER:
                return R.drawable.ic_notification_next_chapter;
            case CYCLE_SPEED:
                return R.drawable.ic_notification_speed;
            case SLEEP_TIMER_EXTEND:
                return R.drawable.ic_notification_sleep_extend;
            case SKIP_AD:
            default:
                return R.drawable.ic_notification_skip_ad;
        }
    }

    /** "1.5×", "1.25×". */
    @NonNull
    static String formatSpeed(float speed) {
        DecimalFormat format = new DecimalFormat("0.0#", DecimalFormatSymbols.getInstance(Locale.getDefault()));
        return format.format(speed) + "×";
    }
}
