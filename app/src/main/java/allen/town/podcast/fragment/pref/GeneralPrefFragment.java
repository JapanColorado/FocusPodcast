package allen.town.podcast.fragment.pref;

import android.os.Bundle;

import allen.town.podcast.R;
import allen.town.podcast.activity.SettingsActivity;

public class GeneralPrefFragment extends AbsSettingsFragment {
    private static final String PREF_SWIPE = "pref_swipe";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_general);
        setupInterfaceScreen();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((SettingsActivity) getActivity()).setTitle(R.string.pref_general_title);
    }

    private void setupInterfaceScreen() {
        findPreference(PREF_SWIPE)
                .setOnPreferenceClickListener(preference -> {
                    ((SettingsActivity) getActivity()).openScreen(R.xml.pref_swipe);
                    return true;
                });
    }

    @Override
    public void invalidateSettings() {

    }
}
