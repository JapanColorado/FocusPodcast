package allen.town.podcast.fragment.pref;

import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;

import allen.town.podcast.R;
import allen.town.podcast.model.playback.ClearVoiceLevel;

/** Shared bits of the two Clear voice list preferences (global default and per podcast). */
final class ClearVoicePreferences {
    private ClearVoicePreferences() {
    }

    /** Shows {@code level} as the preference's value and in its summary. */
    static void show(@NonNull ListPreference preference, int level) {
        int sanitized = ClearVoiceLevel.sanitize(level);
        preference.setValue(String.valueOf(sanitized));
        updateSummary(preference, sanitized);
    }

    static void updateSummary(@NonNull ListPreference preference, int level) {
        Resources res = preference.getContext().getResources();
        String[] names = res.getStringArray(R.array.clear_voice_options);
        int index = Math.min(ClearVoiceLevel.sanitize(level), names.length - 1);
        preference.setSummary(res.getString(R.string.pref_clear_voice_sum, names[index]));
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
