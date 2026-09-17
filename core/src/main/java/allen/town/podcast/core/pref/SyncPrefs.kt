package allen.town.podcast.core.pref

import allen.town.podcast.common.extensions.getStringOrDefault
import allen.town.podcast.common.model.CategoryInfo
import allen.town.podcast.common.util.JsonHelper.parseStringList
import allen.town.podcast.common.util.JsonHelper.toJSONString
import allen.town.podcast.common.util.PodcastSearchPreferenceUtil
import allen.town.podcast.common.util.Timber
import allen.town.podcast.core.pref.PrefsStore.prefs
import android.os.Build
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

/**
 * Owns the preferences that describe the app's dealings with the outside world: the gpodder.net
 * notification switch, the online podcast search engine list and recent search keywords, and the
 * app/notification version numbers the update check compares against. It reads and writes through
 * [PrefsStore]; [Prefs] exposes every member of it unchanged.
 */
internal object SyncPrefs {

    private const val PODCAST_SEARCH_ENGINE_LIST = "podcast_search_engine_list"
    private const val LAST_CHECKED_APP_VERSION = "last_checked_app_version"
    private const val LAST_CHECKED_NOTIFY_VERSION = "last_checked_notify_version"

    fun gpodnetNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= 26) {
            true // System handles notification preferences
        } else prefs.getBoolean(Prefs.PREF_GPODNET_NOTIFICATIONS, true)
    }

    /**
     * Used for migration of the preference to system notification channels.
     */
    val gpodnetNotificationsEnabledRaw: Boolean
        get() = prefs.getBoolean(Prefs.PREF_GPODNET_NOTIFICATIONS, true)

    fun setGpodnetNotificationsEnabled() {
        prefs.edit()
            .putBoolean(Prefs.PREF_GPODNET_NOTIFICATIONS, true)
            .apply()
    }

    var podcastSearchEngineList: List<CategoryInfo>
        get() {
            val gson = Gson()
            val collectionType = object : TypeToken<List<CategoryInfo>>() {}.type

            val data = prefs.getStringOrDefault(PODCAST_SEARCH_ENGINE_LIST, gson.toJson(
                PodcastSearchPreferenceUtil.defaultSearchEngine, collectionType))
            return try {
                Gson().fromJson(data, collectionType)
            } catch (e: JsonSyntaxException) {
                Timber.e(e,"podcastSearchEngineList")
                return PodcastSearchPreferenceUtil.defaultSearchEngine.orEmpty()
            }
        }
        set(value) {
            val collectionType = object : TypeToken<List<CategoryInfo>>() {}.type
            prefs.edit()
                .putString(PODCAST_SEARCH_ENGINE_LIST, Gson().toJson(value, collectionType))
                .apply()
        }

    var onlinePodcastSearchHistory: List<String?>
        get() = parseStringList(prefs.getString(Prefs.PREF_ONLINE_PODCAST_SEARCH_HISTORY, ""))
        set(keywords) {
            prefs.edit()
                .putString(Prefs.PREF_ONLINE_PODCAST_SEARCH_HISTORY, toJSONString(keywords))
                .apply()
        }

    fun clearOnlinePodcastSearchHistory() {
        prefs.edit()
            .putString(Prefs.PREF_ONLINE_PODCAST_SEARCH_HISTORY, "")
            .apply()
    }

    var versionCode: Int
        get() = prefs.getInt(LAST_CHECKED_APP_VERSION, 0)
        set(versionCode) {
            prefs.edit().putInt(LAST_CHECKED_APP_VERSION, versionCode).apply()
        }

    var notifyVersionCode: Int
        get() = prefs.getInt(LAST_CHECKED_NOTIFY_VERSION, 0)
        set(versionCode) {
            prefs.edit().putInt(LAST_CHECKED_NOTIFY_VERSION, versionCode).apply()
        }

    /**
     * Checks whether the user has already dismissed the update for this version.
     *
     * @param newVersion
     * @return
     */
    fun lastVersionChecked(newVersion: Int): Boolean {
        return versionCode == newVersion
    }

    fun lastNotifyVersionChecked(newVersion: Int): Boolean {
        return notifyVersionCode == newVersion
    }
}
