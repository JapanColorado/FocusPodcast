package allen.town.podcast.fragment.pref

import androidx.preference.PreferenceDataStore

/**
 * A data store that keeps nothing, for preference screens whose values belong to one feed and
 * are loaded from and written to the database by the screen itself. Without it the preferences
 * persist to the app-wide SharedPreferences, where every feed would share one key. The base
 * [PreferenceDataStore] throws from every `put*`, so each one is overridden to discard the
 * value; the `get*` defaults already return the given default.
 */
class DiscardingPreferenceDataStore : PreferenceDataStore() {
    override fun putString(key: String?, value: String?) = Unit
    override fun putStringSet(key: String?, values: MutableSet<String>?) = Unit
    override fun putInt(key: String?, value: Int) = Unit
    override fun putLong(key: String?, value: Long) = Unit
    override fun putFloat(key: String?, value: Float) = Unit
    override fun putBoolean(key: String?, value: Boolean) = Unit
}
