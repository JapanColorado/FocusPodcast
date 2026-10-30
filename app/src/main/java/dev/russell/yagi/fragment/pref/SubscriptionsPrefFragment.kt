package dev.russell.yagi.fragment.pref

import dev.russell.yagi.R
import dev.russell.yagi.activity.SettingsActivity
import dev.russell.yagi.core.pref.Prefs
import dev.russell.yagi.dialog.FeedRefreshPrefDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.text.format.DateFormat
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.concurrent.TimeUnit

/** Podcast search engine, the refresh schedule and how the Library looks. */
class SubscriptionsPrefFragment : AbsSettingsFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_subscriptions)
        findPreference<Preference>(Prefs.PREF_UPDATE_INTERVAL)?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                FeedRefreshPrefDialog(requireContext()).show()
                true
            }
    }

    override fun onStart() {
        super.onStart()
        (requireActivity() as SettingsActivity).setTitle(R.string.pref_subscriptions_updates_title)
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onResume() {
        super.onResume()
        setUpdateIntervalText()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (Prefs.PREF_UPDATE_INTERVAL == key) {
            setUpdateIntervalText()
        }
    }

    private fun setUpdateIntervalText() {
        val context = requireContext()
        val interval = Prefs.updateInterval
        val value = if (interval > 0) {
            val hours = TimeUnit.MILLISECONDS.toHours(interval).toInt()
            context.resources.getQuantityString(R.plurals.feed_refresh_every_x_hours, hours, hours)
        } else {
            val timeOfDay = Prefs.updateTimeOfDay
            if (timeOfDay.size == 2) {
                val cal = GregorianCalendar()
                cal[Calendar.HOUR_OF_DAY] = timeOfDay[0]
                cal[Calendar.MINUTE] = timeOfDay[1]
                val timeOfDayStr = DateFormat.getTimeFormat(context).format(cal.time)
                context.getString(R.string.feed_refresh_interval_at, timeOfDayStr)
            } else {
                context.getString(R.string.feed_refresh_never)
            }
        }
        findPreference<Preference>(Prefs.PREF_UPDATE_INTERVAL)?.summary =
            context.getString(R.string.feed_refresh_sum) + "\n" +
                context.getString(R.string.pref_current_value, value)
    }

    override fun invalidateSettings() {}
}
