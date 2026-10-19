package dev.russell.focuspodcast.core.service.playback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The optional buttons the user can add to the playback notification, the lock screen and the
 * system media controls (Android 13+ media output, Android Auto, Wear), next to the standard
 * rewind / play-pause / fast-forward. Each one is published as a {@code MediaSession} custom
 * action and as a notification action; {@link NotificationCustomActionResolver} decides which of
 * the selected ones apply right now.
 */
public enum NotificationCustomAction {
    /** Seeks to the start of the next chapter. Shown only when there is one. */
    NEXT_CHAPTER("next_chapter", "action.dev.russell.focuspodcast.core.service.nextChapter"),
    /** Steps to the next of the user's speed presets, wrapping around. */
    CYCLE_SPEED("cycle_speed", "action.dev.russell.focuspodcast.core.service.cycleSpeed"),
    /** Starts a 15-minute sleep timer, or adds 15 minutes to a running duration timer. */
    SLEEP_TIMER_EXTEND("sleep_extend", "action.dev.russell.focuspodcast.core.service.sleepExtend"),
    /** Skips to the end of the ad segment playback is in. Shown only inside one. */
    SKIP_AD("skip_ad", "action.dev.russell.focuspodcast.core.service.skipAd");

    /** How many custom buttons may be selected at once. */
    public static final int MAX_SELECTED = 2;

    private static final String SEPARATOR = ",";

    private final String prefValue;
    private final String sessionAction;

    NotificationCustomAction(String prefValue, String sessionAction) {
        this.prefValue = prefValue;
        this.sessionAction = sessionAction;
    }

    /** The stable id stored in the preference. */
    @NonNull
    public String getPrefValue() {
        return prefValue;
    }

    /** The action string of the media session custom action and the notification intent. */
    @NonNull
    public String getSessionAction() {
        return sessionAction;
    }

    @Nullable
    public static NotificationCustomAction fromSessionAction(@Nullable String action) {
        for (NotificationCustomAction value : values()) {
            if (value.sessionAction.equals(action)) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    public static NotificationCustomAction fromPrefValue(@Nullable String prefValue) {
        for (NotificationCustomAction value : values()) {
            if (value.prefValue.equals(prefValue)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Reads the stored selection: comma separated ids in the user's order. Unknown ids and
     * duplicates are dropped and at most {@link #MAX_SELECTED} are kept.
     */
    @NonNull
    public static List<NotificationCustomAction> parse(@Nullable String stored) {
        if (stored == null || stored.isEmpty()) {
            return Collections.emptyList();
        }
        List<NotificationCustomAction> result = new ArrayList<>();
        for (String part : stored.split(SEPARATOR)) {
            NotificationCustomAction action = fromPrefValue(part.trim());
            if (action != null && !result.contains(action) && result.size() < MAX_SELECTED) {
                result.add(action);
            }
        }
        return result;
    }

    /** The inverse of {@link #parse(String)}. */
    @NonNull
    public static String serialize(@NonNull List<NotificationCustomAction> actions) {
        StringBuilder builder = new StringBuilder();
        for (NotificationCustomAction action : actions) {
            if (builder.length() > 0) {
                builder.append(SEPARATOR);
            }
            builder.append(action.prefValue);
        }
        return builder.toString();
    }
}
