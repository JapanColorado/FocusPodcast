package dev.russell.focuspodcast.fragment.pref;

import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;

import dev.russell.focuspodcast.R;
import dev.russell.focuspodcast.model.playback.ClearVoiceLevel;

/**
 * Shared bits of the two Voice list preferences (global default and per podcast): the entries
 * name the modes (Original / Clear / Clear+ / Warm) and the summary describes the selected one.
 */
public final class ClearVoicePreferences {
    private ClearVoicePreferences() {
    }

    /** Shows {@code level} as the preference's value and describes it in the summary. */
    static void show(@NonNull ListPreference preference, int level) {
        int sanitized = ClearVoiceLevel.sanitize(level);
        preference.setValue(String.valueOf(sanitized));
        updateSummary(preference, sanitized);
    }

    static void updateSummary(@NonNull ListPreference preference, int level) {
        Resources res = preference.getContext().getResources();
        preference.setSummary(description(res, level));
    }

    /** The one-line description of a Voice mode, as shown under the selector. */
    @NonNull
    public static String description(@NonNull Resources res, int level) {
        String[] descriptions = res.getStringArray(R.array.clear_voice_descriptions);
        return descriptions[Math.min(ClearVoiceLevel.sanitize(level), descriptions.length - 1)];
    }

    /** The level a ListPreference change listener received, or -1 if it is not one. */
    static int parse(Object newValue) {
        if (!(newValue instanceof String)) {
            return -1;
        }
        try {
            return ClearVoiceLevel.sanitize(Integer.parseInt((String) newValue));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
