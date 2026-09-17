package allen.town.podcast.core.pref

import allen.town.podcast.common.util.BasePreferenceUtil.materialYou
import allen.town.podcast.common.util.ThemeUtils.generalThemeValue
import allen.town.podcast.core.R
import allen.town.podcast.core.feed.SubscriptionsFilter
import allen.town.podcast.core.playback.AlbumCoverStyle
import allen.town.podcast.core.playback.NowPlayingScreen
import allen.town.podcast.core.pref.PrefsStore.context
import allen.town.podcast.core.pref.PrefsStore.getString
import allen.town.podcast.core.pref.PrefsStore.prefs
import allen.town.podcast.core.view.TopAppBarLayout.AppBarMode
import allen.town.podcast.model.feed.FeedCounter
import android.os.Build
import android.text.TextUtils
import androidx.annotation.StyleRes
import androidx.core.app.NotificationCompat
import allen.town.podcast.common.theme.ThemeMode

/**
 * Owns everything the user sees: the app theme and now-playing skin, the subscription list
 * ordering, counters and filter, the "show remaining time" / episode cover toggles, the layout
 * switches (app bar mode, landscape columns, back button behaviour, full lock screen) and the
 * notification appearance settings (priority, persistence, compact buttons, download reports).
 * It reads and writes through [PrefsStore]; [Prefs] exposes every member of it unchanged.
 */
internal object UiPrefs {

    private const val PREF_PERSISTENT_NOTIFICATION = "pref_persist_notification"
    private const val PREF_DRAWER_FEED_COUNTER = "pref_feed_counts"
    private const val PREF_SHOW_DOWNLOAD_REPORT = "pref_show_download_sync_failed"
    private const val PREF_SHOW_AUTO_DOWNLOAD_REPORT = "pref_show_auto_downlod_result"

    private const val NOTIFICATION_BUTTON_REWIND = 0
    private const val NOTIFICATION_BUTTON_FAST_FORWARD = 1
    private const val NOTIFICATION_BUTTON_SKIP = 2

    /**
     * Returns the current theme.
     *
     * @return R.style.Theme_FocusPodcast_Light or R.style.Theme_FocusPodcast_Dark
     */
    @get:StyleRes
    val theme: Int
        get() = if (materialYou) {
            if (generalThemeValue(context) === ThemeMode.BLACK) R.style.Theme_FocusPodcast_MD3_Base_Black else R.style.Theme_FocusPodcast_MD3_Base
        } else {
            val themeMode = generalThemeValue(
                context
            )
            if (themeMode === ThemeMode.LIGHT) {
                R.style.Theme_FocusPodcast_Light
            } else if (themeMode === ThemeMode.DARK) {
                R.style.Theme_FocusPodcast_Dark
            } else if (themeMode === ThemeMode.BLACK) {
                R.style.Theme_FocusPodcast_TrueBlack
            } else {
                R.style.Theme_FocusPodcast_Light
            }
        }

    var compactNotificationButtons: List<Int>
        get() {
            val buttons = TextUtils.split(
                prefs.getString(
                    Prefs.PREF_COMPACT_NOTIFICATION_BUTTONS,
                    NOTIFICATION_BUTTON_REWIND.toString() + "," + NOTIFICATION_BUTTON_FAST_FORWARD
                ),
                ","
            )
            val notificationButtons: MutableList<Int> = ArrayList()
            for (button in buttons) {
                notificationButtons.add(button.toInt())
            }
            return notificationButtons
        }
        set(items) {
            val str = TextUtils.join(",", items)
            prefs.edit()
                .putString(Prefs.PREF_COMPACT_NOTIFICATION_BUTTONS, str)
                .apply()
        }

    /**
     * Helper function to return whether the specified button should be shown on compact
     * notifications.
     *
     * @param buttonId Either NOTIFICATION_BUTTON_REWIND, NOTIFICATION_BUTTON_FAST_FORWARD or
     * NOTIFICATION_BUTTON_SKIP.
     * @return `true` if button should be shown, `false`  otherwise
     */
    private fun showButtonOnCompactNotification(buttonId: Int): Boolean {
        return compactNotificationButtons.contains(buttonId)
    }

    fun showRewindOnCompactNotification(): Boolean {
        return showButtonOnCompactNotification(NOTIFICATION_BUTTON_REWIND)
    }

    fun showFastForwardOnCompactNotification(): Boolean {
        return showButtonOnCompactNotification(NOTIFICATION_BUTTON_FAST_FORWARD)
    }

    fun showSkipOnCompactNotification(): Boolean {
        return showButtonOnCompactNotification(NOTIFICATION_BUTTON_SKIP)
    }

    val feedOrder: Int
        get() {
            return getString(Prefs.PREF_DRAWER_FEED_ORDER, "" + Prefs.FEED_ORDER_COUNTER).toInt()
        }

    fun setFeedOrder(selected: String?) {
        prefs.edit()
            .putString(Prefs.PREF_DRAWER_FEED_ORDER, selected)
            .apply()
    }

    var feedOrderMethod: String
        get() = getString(Prefs.PREF_DRAWER_FEED_ORDER_METHOD, Prefs.ORDER_ASC)
        set(selected) {
            prefs.edit()
                .putString(Prefs.PREF_DRAWER_FEED_ORDER_METHOD, selected)
                .apply()
        }

    val feedCounterSetting: FeedCounter
        get() {
            val value = getString(
                PREF_DRAWER_FEED_COUNTER,
                "" + FeedCounter.SHOW_NEW_UNPLAYED_SUM.id
            )
            return FeedCounter.fromOrdinal(value.toInt())
        }

    /**
     * @return `true` if episodes should use their own cover, `false`  otherwise
     */
    val useEpisodeCoverSetting: Boolean
        get() = prefs.getBoolean(Prefs.PREF_USE_EPISODE_COVER, true)

    val showEpisodeCoverInFeed: Boolean
        get() = prefs.getBoolean(Prefs.PREF_SHOW_EPISODE_COVER_IN_FEED, true)

    /**
     * @return `true` if we should show remaining time or the duration
     */
    fun shouldShowRemainingTime(): Boolean {
        return prefs.getBoolean(Prefs.PREF_SHOW_TIME_LEFT, false)
    }

    /**
     * Whether to show the most recently opened page: "1" is the first item, "0" is the most recent one.
     * @return
     */
    fun shouldShowLastPageOfHome(): Boolean {
        return prefs.getString(Prefs.PREF_HOME_PAGE, "0") == "0"
    }

    /**
     * Sets the preference for whether we show the remain time, if not show the duration. This will
     * send out events so the current playing screen, queue and the episode list would refresh
     *
     * @return `true` if we should show remaining time or the duration
     */
    fun setShowRemainTimeSetting(showRemain: Boolean?) {
        prefs.edit()
            .putBoolean(
                Prefs.PREF_SHOW_TIME_LEFT,
                checkNotNull(showRemain) { "showRemain must not be null" }
            )
            .apply()
    }

    /**
     * Returns notification priority.
     *
     * @return NotificationCompat.PRIORITY_MAX or NotificationCompat.PRIORITY_DEFAULT
     */
    val notifyPriority: Int
        get() = if (prefs.getBoolean(Prefs.PREF_EXPANDED_NOTIFICATION, false)) {
            NotificationCompat.PRIORITY_MAX
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

    /**
     * Returns true if notifications are persistent
     *
     * @return `true` if notifications are persistent, `false`  otherwise
     */
    val isPersistNotify: Boolean
        get() = prefs.getBoolean(PREF_PERSISTENT_NOTIFICATION, true)

    /**
     * Returns true if the lockscreen background should be set to the current episode's image
     *
     * @return `true` if the lockscreen background should be set, `false`  otherwise
     */
    fun setLockscreenBackground(): Boolean {
        return prefs.getBoolean(Prefs.PREF_LOCKSCREEN_BACKGROUND, true)
    }

    /**
     * Returns true if download reports are shown
     *
     * @return `true` if download reports are shown, `false`  otherwise
     */
    fun showDownloadReport(): Boolean {
        return if (Build.VERSION.SDK_INT >= 26) {
            true // System handles notification preferences
        } else prefs.getBoolean(
            PREF_SHOW_DOWNLOAD_REPORT,
            true
        )
    }

    /**
     * Used for migration of the preference to system notification channels.
     */
    val showDownloadReportRaw: Boolean
        get() = prefs.getBoolean(PREF_SHOW_DOWNLOAD_REPORT, true)

    fun showAutoDownloadReport(): Boolean {
        return if (Build.VERSION.SDK_INT >= 26) {
            true // System handles notification preferences
        } else prefs.getBoolean(PREF_SHOW_AUTO_DOWNLOAD_REPORT, false)
    }

    /**
     * Used for migration of the preference to system notification channels.
     */
    val showAutoDownloadReportRaw: Boolean
        get() = prefs.getBoolean(PREF_SHOW_AUTO_DOWNLOAD_REPORT, false)

    fun showSnowFall(): Boolean {
        return prefs.getBoolean(Prefs.PREF_SNOWFALL, false)
    }

    fun showExtraMiniButtons(): Boolean {
        return prefs.getBoolean(Prefs.PREF_TOGGLE_ADD_CONTROLS, false)
    }

    val isAdapterColor: Boolean
        get() = prefs.getBoolean(Prefs.PREF_ADAPTIVE_COLOR_APP, true)

    // Also set a cover theme for that now playing
    var nowPlayingScreen: NowPlayingScreen
        get() {
            val id = prefs.getInt(Prefs.NOW_PLAYING_SCREEN_ID, 0)
            for (nowPlayingScreen in NowPlayingScreen.values()) {
                if (nowPlayingScreen.id == id) {
                    return nowPlayingScreen
                }
            }
            return NowPlayingScreen.Normal
        }
        set(nowPlayingScreen) {
            prefs.edit()
                .putInt(Prefs.NOW_PLAYING_SCREEN_ID, nowPlayingScreen.id)
                .apply()
            // Also set a cover theme for that now playing
            albumCoverStyle = nowPlayingScreen.defaultCoverTheme
        }

    var ALBUM_COVER_STYLE = "album_cover_style_id"

    var albumCoverStyle: AlbumCoverStyle?
        get() {
            val id = prefs.getInt(ALBUM_COVER_STYLE, 0)
            for (albumCoverStyle in AlbumCoverStyle.values()) {
                if (albumCoverStyle.id == id) {
                    return albumCoverStyle
                }
            }
            return AlbumCoverStyle.Normal
        }
        set(albumCoverStyle) {
            val style = checkNotNull(albumCoverStyle) { "albumCoverStyle must not be null" }
            prefs.edit()
                .putInt(ALBUM_COVER_STYLE, style.id)
                .apply()
        }

    val blurAmount: Int
        get() = prefs.getInt(Prefs.NEW_BLUR_AMOUNT, 12)

    val backButtonBehavior: Prefs.BackButtonBehavior
        get() = when (prefs.getString(Prefs.PREF_BACK_BUTTON_BEHAVIOR, "default")) {
            "drawer" -> Prefs.BackButtonBehavior.OPEN_DRAWER
            "doubletap" -> Prefs.BackButtonBehavior.DOUBLE_TAP
            "prompt" -> Prefs.BackButtonBehavior.SHOW_PROMPT
            "default" -> Prefs.BackButtonBehavior.DEFAULT
            else -> Prefs.BackButtonBehavior.DEFAULT
        }

    var subscriptionsFilter: SubscriptionsFilter
        get() {
            val value = prefs.getString(Prefs.PREF_FILTER_FEED, "")
            return SubscriptionsFilter(value)
        }
        set(value) {
            prefs.edit()
                .putString(Prefs.PREF_FILTER_FEED, value.serialize())
                .apply()
        }

    fun shouldShowSubscriptionTitle(): Boolean {
        return prefs.getBoolean(Prefs.PREF_SUBSCRIPTION_TITLE, false)
    }

    val isFullLockScreen: Boolean
        get() = prefs.getBoolean(Prefs.PREF_FULL_LOCK_SCREEN, false)

    val appBarMode: AppBarMode
        get() {
            val value = prefs.getString(Prefs.APPBAR_MODE, "1")
            return if (value == "0") {
                AppBarMode.COLLAPSING
            } else if (value == "2") {
                AppBarMode.FIXED
            } else {
                AppBarMode.SIMPLE
            }
        }

    /**
     * Whether to show two columns in landscape orientation.
     * @return
     */
    fun shouldShowColumnInLandscape(): Boolean {
        return prefs.getBoolean(Prefs.PREF_COLUMN_IN_LANDSCAPE, true)
    }
}
