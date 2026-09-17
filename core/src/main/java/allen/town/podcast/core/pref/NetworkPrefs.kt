package allen.town.podcast.core.pref

import allen.town.podcast.core.pref.PrefsStore.getString
import allen.town.podcast.core.pref.PrefsStore.prefs
import allen.town.podcast.core.util.download.AutoUpdateManager
import allen.town.podcast.model.download.ProxyConfig
import android.content.Context
import android.text.TextUtils
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Owns how the app reaches the network: the automatic feed refresh schedule (an interval or a
 * time of day, and the alarm that backs it), which kinds of traffic are allowed on a metered
 * connection, whether a sync runs at startup, and the HTTP proxy configuration. It reads and
 * writes through [PrefsStore]; [Prefs] exposes every member of it unchanged.
 */
internal object NetworkPrefs {

    private const val PREF_MOBILE_UPDATE = "pref_mobile_update_types"
    private const val PREF_PROXY_HOST = "pref_proxy_host"
    private const val PREF_PROXY_PORT = "pref_proxy_port"
    private const val PREF_PROXY_TYPE = "pref_proxy_type"
    private const val PREF_PROXY_USER = "pref_proxy_username"
    private const val PREF_PROXY_PASSWORD = "pref_proxy_pass"
    private const val PREF_REFRESH_ON_START = "pref_refresh_on_start"

    /*
     * Returns update interval in milliseconds; value 0 means that auto update is disabled
     * or feeds are updated at a certain time of day
     */// when updating with an interval, we assume the user wants
    // to update *now* and then every 'hours' interval thereafter.
    /**
     * Sets the update interval value.
     */
    var updateInterval: Long
        get() {
            val updateInterval = getString(Prefs.PREF_UPDATE_INTERVAL, "0")
            return if (!updateInterval.contains(":")) {
                readUpdateInterval(updateInterval)
            } else {
                0
            }
        }
        set(hours) {
            prefs.edit()
                .putString(Prefs.PREF_UPDATE_INTERVAL, hours.toString())
                .apply()
            // when updating with an interval, we assume the user wants
            // to update *now* and then every 'hours' interval thereafter.
            AutoUpdateManager.restartUpdateAlarm(PrefsStore.context)
        }

    private fun readUpdateInterval(valueFromPrefs: String): Long {
        val hours = valueFromPrefs.toInt()
        return TimeUnit.HOURS.toMillis(hours.toLong())
    }

    val updateTimeOfDay: IntArray
        get() {
            val datetime = getString(Prefs.PREF_UPDATE_INTERVAL, "")
            return if (datetime.length >= 3 && datetime.contains(":")) {
                val parts = datetime.split(":".toRegex()).toTypedArray()
                val hourOfDay = parts[0].toInt()
                val minute = parts[1].toInt()
                intArrayOf(hourOfDay, minute)
            } else {
                IntArray(0)
            }
        }

    /**
     * True when automatic feed refresh is off. Uses the same default as [updateInterval]
     * so a pristine install reports "disabled"; a time-of-day schedule ("HH:mm") is not disabled.
     */
    val isAutoUpdateDisabled: Boolean
        get() = getString(Prefs.PREF_UPDATE_INTERVAL, "0") == "0"

    /**
     *
     * @return true if auto update is set to a specific time
     * false if auto update is set to interval
     */
    val isAutoUpdateTimeOfDay: Boolean
        get() = updateTimeOfDay.size == 2

    /**
     * Sets the update interval value.
     */
    fun setUpdateTimeOfDay(hourOfDay: Int, minute: Int) {
        prefs.edit()
            .putString(Prefs.PREF_UPDATE_INTERVAL, "$hourOfDay:$minute")
            .apply()
        AutoUpdateManager.restartUpdateAlarm(PrefsStore.context)
    }

    fun disableAutoUpdate(context: Context?) {
        prefs.edit()
            .putString(Prefs.PREF_UPDATE_INTERVAL, "0")
            .apply()
        AutoUpdateManager.disableAutoUpdate(context)
    }

    private fun isAllowMobileFor(type: String): Boolean {
        val defaultValue = HashSet<String>()
        defaultValue.add("images")
        val allowed = prefs.getStringSet(PREF_MOBILE_UPDATE, defaultValue) ?: defaultValue
        return allowed.contains(type)
    }

    private fun setAllowMobileFor(type: String, allow: Boolean) {
        val defaultValue = HashSet<String>()
        defaultValue.add("images")
        val getValueStringSet = prefs.getStringSet(PREF_MOBILE_UPDATE, defaultValue) ?: defaultValue
        val allowed: MutableSet<String> = HashSet(getValueStringSet)
        if (allow) {
            allowed.add(type)
        } else {
            allowed.remove(type)
        }
        prefs.edit().putStringSet(PREF_MOBILE_UPDATE, allowed).apply()
        if (type == "feed_refresh") {
            // the periodic worker bakes UNMETERED vs CONNECTED into its constraints
            AutoUpdateManager.restartUpdateAlarm(PrefsStore.context)
        }
    }

    var isAllowMobileFeedRefresh: Boolean
        get() = isAllowMobileFor("feed_refresh")
        set(allow) {
            setAllowMobileFor("feed_refresh", allow)
        }

    var isAllowMobileEpisodeDownload: Boolean
        get() = isAllowMobileFor("episode_download")
        set(allow) {
            setAllowMobileFor("episode_download", allow)
        }

    var isAllowMobileAutoDownload: Boolean
        get() = isAllowMobileFor("auto_download")
        set(allow) {
            setAllowMobileFor("auto_download", allow)
        }

    var isAllowMobileStreaming: Boolean
        get() = isAllowMobileFor("streaming")
        set(allow) {
            setAllowMobileFor("streaming", allow)
        }

    var isAllowMobileImages: Boolean
        get() = isAllowMobileFor("images")
        set(allow) {
            setAllowMobileFor("images", allow)
        }

    fun shouldSyncOnStart(): Boolean {
        return prefs.getBoolean(PREF_REFRESH_ON_START, true)
    }

    var proxyConfig: ProxyConfig
        get() {
            val type =
                Proxy.Type.valueOf(getString(PREF_PROXY_TYPE, Proxy.Type.DIRECT.name))
            val host = prefs.getString(PREF_PROXY_HOST, null)
            val port = prefs.getInt(PREF_PROXY_PORT, 0)
            val username = prefs.getString(PREF_PROXY_USER, null)
            val password = prefs.getString(PREF_PROXY_PASSWORD, null)
            return ProxyConfig(type, host, port, username, password)
        }
        set(config) {
            val editor = prefs.edit()
            editor.putString(PREF_PROXY_TYPE, config.type.name)
            if (TextUtils.isEmpty(config.host)) {
                editor.remove(PREF_PROXY_HOST)
            } else {
                editor.putString(PREF_PROXY_HOST, config.host)
            }
            if (config.port <= 0 || config.port > 65535) {
                editor.remove(PREF_PROXY_PORT)
            } else {
                editor.putInt(PREF_PROXY_PORT, config.port)
            }
            if (TextUtils.isEmpty(config.username)) {
                editor.remove(PREF_PROXY_USER)
            } else {
                editor.putString(PREF_PROXY_USER, config.username)
            }
            if (TextUtils.isEmpty(config.password)) {
                editor.remove(PREF_PROXY_PASSWORD)
            } else {
                editor.putString(PREF_PROXY_PASSWORD, config.password)
            }
            editor.apply()
        }
}
