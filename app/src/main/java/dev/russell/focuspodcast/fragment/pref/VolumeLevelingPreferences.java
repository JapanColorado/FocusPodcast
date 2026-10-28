package dev.russell.focuspodcast.fragment.pref;

import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;

import dev.russell.focuspodcast.R;
import dev.russell.focuspodcast.model.playback.VolumeLeveling;

/**
 * Shared bits of the two Volume leveling list preferences (global default and per podcast), like
 * {@link ClearVoicePreferences}: the entries name the modes (Off / Gentle / Strong) and the
 * summary describes the selected one.
 */
public final class VolumeLevelingPreferences {
    private VolumeLevelingPreferences() {
    }

    /** Shows {@code mode} as the preference's value and describes it in the summary. */
    static void show(@NonNull ListPreference preference, int mode) {
        int sanitized = VolumeLeveling.sanitize(mode);
        preference.setValue(String.valueOf(sanitized));
        updateSummary(preference, sanitized);
    }

    static void updateSummary(@NonNull ListPreference preference, int mode) {
        Resources res = preference.getContext().getResources();
        preference.setSummary(description(res, mode));
    }

    /** The one-line description of a Volume leveling mode, as shown under the selector. */
    @NonNull
    public static String description(@NonNull Resources res, int mode) {
        String[] descriptions = res.getStringArray(R.array.volume_leveling_descriptions);
        return descriptions[Math.min(VolumeLeveling.sanitize(mode), descriptions.length - 1)];
    }

    /** The mode a ListPreference change listener received, or -1 if it is not one. */
    static int parse(Object newValue) {
        if (!(newValue instanceof String)) {
            return -1;
        }
        try {
            return VolumeLeveling.sanitize(Integer.parseInt((String) newValue));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
