package allen.town.podcast.fragment.pref;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;

import allen.town.podcast.searchpreference.SearchConfiguration;
import allen.town.podcast.searchpreference.SearchPreference;

import allen.town.podcast.R;
import allen.town.podcast.activity.SettingsActivity;
import allen.town.podcast.activity.SettingsScreen;

/**
 * Top-level settings screen: the search bar and one entry per settings screen.
 */
public class MainPrefFragment extends AbsSettingsFragment {

    /** Entry keys in {@code pref_main.xml} and the screen each one opens. */
    private static final String[] ENTRY_KEYS = {
            "pref_appearance", "pref_playback", "pref_ad_skip", "pref_subscriptions",
            "pref_downloads", "notifications", "pref_sync", "pref_about_support",
    };
    private static final int[] ENTRY_SCREENS = {
            R.xml.pref_appearance, R.xml.pref_playback, R.xml.pref_ad_skip, R.xml.pref_subscriptions,
            R.xml.pref_downloads, R.xml.pref_notifications, R.xml.pref_sync, R.xml.pref_about,
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_main);
        setupMainScreen();
        setupSearch();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((SettingsActivity) getActivity()).setTitle(R.string.settings_label);
    }

    private void setupMainScreen() {
        for (int i = 0; i < ENTRY_KEYS.length; i++) {
            final int screen = ENTRY_SCREENS[i];
            Preference entry = findPreference(ENTRY_KEYS[i]);
            if (entry == null) {
                throw new IllegalStateException("missing preference " + ENTRY_KEYS[i] + " in pref_main.xml");
            }
            entry.setOnPreferenceClickListener(preference -> {
                ((SettingsActivity) getActivity()).openScreen(screen);
                return true;
            });
        }
    }

    private void setupSearch() {
        SearchPreference searchPreference = findPreference("searchPreference");
        SearchConfiguration config = searchPreference.getSearchConfiguration();
        config.setActivity((AppCompatActivity) getActivity());
        config.setFragmentContainerViewId(R.id.settingsContainer);
        config.setBreadcrumbsEnabled(true);

        SettingsScreen.indexAll(config);
    }

    @Override
    public void invalidateSettings() {

    }
}
