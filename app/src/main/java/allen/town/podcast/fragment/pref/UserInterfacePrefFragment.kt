package allen.town.podcast.fragment.pref

import allen.town.podcast.R
import allen.town.podcast.activity.SettingsActivity
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.pref.Prefs.PREF_HOME_PAGE
import allen.town.podcast.core.pref.Prefs.setShowRemainTimeSetting
import allen.town.podcast.dialog.FeedsSortDialog.Companion.newInstance
import allen.town.podcast.dialog.SubsFilterDialog.showDialog
import allen.town.podcast.event.PlayerStatusEvent
import allen.town.podcast.event.UnreadItemsUpdateEvent
import allen.town.podcast.pref.AppLanguage
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import allen.town.podcast.theme.constants.ThemeConstants.LANGUAGE_NAME
import org.greenrobot.eventbus.EventBus

class UserInterfacePrefFragment : AbsSettingsFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_user_interface)
        setupInterfaceScreen()
    }

    override fun onStart() {
        super.onStart()
        (requireActivity() as SettingsActivity).setTitle(R.string.user_interface_label)
    }

    /** Preferences declared in `pref_user_interface.xml`; a missing key is a programming error. */
    private fun requirePreference(key: String): Preference =
        checkNotNull(findPreference(key)) { "missing preference $key in pref_user_interface.xml" }

    private fun setupInterfaceScreen() {

        //show remaining time or total duration on the player screen
        findPreference<Preference>(Prefs.PREF_SHOW_TIME_LEFT)?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
                setShowRemainTimeSetting(newValue as Boolean?)
                EventBus.getDefault().post(UnreadItemsUpdateEvent())
                EventBus.getDefault().post(PlayerStatusEvent())
                true
            }
        findPreference<Preference>(Prefs.PREF_FILTER_FEED)?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                showDialog(requireContext())
                true
            }
        findPreference<Preference>(Prefs.PREF_DRAWER_FEED_ORDER)?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                newInstance().show(
                    childFragmentManager, null
                )
                true
            }
        requirePreference(PREF_THEME_INTERFACE).onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                (requireActivity() as SettingsActivity).openScreen(R.xml.pref_theme)
                true
            }
        // Not persisted: the per-app locale is the source of truth, and it can also be changed in
        // the system's app language settings. AppCompat recreates the activities on a change.
        val language = requirePreference(LANGUAGE_NAME) as ListPreference
        language.value = AppLanguage.currentCode(language.entryValues)
        language.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                AppLanguage.apply(newValue.toString())
                true
            }
        requirePreference(PREF_HOME_PAGE).onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                restartActivity()
                true
            }
    }

    override fun invalidateSettings() {}

    companion object {
        private const val PREF_THEME_INTERFACE = "pref_theme"
    }
}