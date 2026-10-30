package dev.russell.yagi.core.pref

import dev.russell.yagi.common.util.BasePreferenceUtil.materialYou
import dev.russell.yagi.common.util.ThemeUtils.generalThemeValue
import dev.russell.yagi.core.R
import dev.russell.yagi.core.feed.AudiobookShelfFilter
import dev.russell.yagi.core.feed.EpisodeFilterOptions
import dev.russell.yagi.core.feed.SubscriptionsFilter
import dev.russell.yagi.core.playback.AlbumCoverStyle
import dev.russell.yagi.core.playback.NowPlayingScreen
import dev.russell.yagi.core.pref.PrefsStore.context
import dev.russell.yagi.core.pref.PrefsStore.getString
import dev.russell.yagi.core.pref.PrefsStore.prefs
import dev.russell.yagi.core.service.playback.NotificationCustomAction
import dev.russell.yagi.core.view.TopAppBarLayout.AppBarMode
import dev.russell.yagi.model.feed.EpisodeSearchOptions
import dev.russell.yagi.model.feed.FeedCounter
import dev.russell.yagi.model.feed.FeedItemFilter
import android.os.Build
import android.text.TextUtils
import androidx.annotation.StyleRes
import androidx.core.app.NotificationCompat
import dev.russell.yagi.common.theme.ThemeMode

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
    private const val PREF_NOTIFICATION_RATIONALE_SHOWN = "pref_notification_rationale_shown"
    private const val PREF_AUDIOBOOKS_SORT = "pref_audiobooks_sort"
    private const val PREF_AUDIOBOOKS_STATUS = "pref_audiobooks_status"
    private const val PREF_AUDIOBOOKS_AUTHORS = "pref_audiobooks_authors"
    private const val PREF_AUDIOBOOKS_LENGTH = "pref_audiobooks_length"
    private const val PREF_SEARCH_FILTER = "pref_episode_search_filter"
    private const val PREF_SEARCH_DATE = "pref_episode_search_date"
    private const val PREF_SEARCH_DURATION = "pref_episode_search_duration"
    private const val PREF_SEARCH_FIELDS = "pref_episode_search_fields"
    private const val PREF_SEARCH_SORT = "pref_episode_search_sort"

    private const val NOTIFICATION_BUTTON_REWIND = 0
    private const val NOTIFICATION_BUTTON_FAST_FORWARD = 1
    private const val NOTIFICATION_BUTTON_SKIP = 2

    /**
     * Returns the current theme.
     *
     * @return R.style.Theme_Yagi_Light or R.style.Theme_Yagi_Dark
     */
    @get:StyleRes
    val theme: Int
        get() = if (materialYou) {
            if (generalThemeValue(context) === ThemeMode.BLACK) R.style.Theme_Yagi_MD3_Base_Black else R.style.Theme_Yagi_MD3_Base
        } else {
            val themeMode = generalThemeValue(
                context
            )
            if (themeMode === ThemeMode.LIGHT) {
                R.style.Theme_Yagi_Light
            } else if (themeMode === ThemeMode.DARK) {
                R.style.Theme_Yagi_Dark
            } else if (themeMode === ThemeMode.BLACK) {
                R.style.Theme_Yagi_TrueBlack
            } else {
                R.style.Theme_Yagi_Light
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

    var notificationCustomButtons: List<NotificationCustomAction>
        get() = NotificationCustomAction.parse(
            prefs.getString(Prefs.PREF_NOTIFICATION_CUSTOM_BUTTONS, null)
        )
        set(items) {
            prefs.edit()
                .putString(Prefs.PREF_NOTIFICATION_CUSTOM_BUTTONS, NotificationCustomAction.serialize(items))
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

    /** Sort and filter of the Audiobooks page (not the audiobook row of the Library page). */
    var audiobookShelfFilter: AudiobookShelfFilter
        get() = AudiobookShelfFilter(
            enumOrNull<AudiobookShelfFilter.Sort>(prefs.getString(PREF_AUDIOBOOKS_SORT, null)),
            enumOrNull<AudiobookShelfFilter.Status>(prefs.getString(PREF_AUDIOBOOKS_STATUS, null)),
            prefs.getStringSet(PREF_AUDIOBOOKS_AUTHORS, null),
            enumOrNull<AudiobookShelfFilter.Length>(prefs.getString(PREF_AUDIOBOOKS_LENGTH, null))
        )
        set(value) {
            prefs.edit()
                .putString(PREF_AUDIOBOOKS_SORT, value.sort.name)
                .putString(PREF_AUDIOBOOKS_STATUS, value.status?.name)
                .putStringSet(PREF_AUDIOBOOKS_AUTHORS, HashSet(value.authors))
                .putString(PREF_AUDIOBOOKS_LENGTH, value.length?.name)
                .apply()
        }

    /**
     * Filters and sort of the episode search, kept from one search to the next. The podcast scope
     * ([EpisodeSearchOptions.feedId]) is not stored: it is always 0 here.
     */
    var episodeSearchOptions: EpisodeSearchOptions
        get() = EpisodeSearchOptions(
            EpisodeFilterOptions.sanitize(FeedItemFilter(prefs.getString(PREF_SEARCH_FILTER, "").orEmpty())),
            0,
            enumOrNull<EpisodeSearchOptions.DateRange>(prefs.getString(PREF_SEARCH_DATE, null)),
            enumOrNull<EpisodeSearchOptions.DurationRange>(prefs.getString(PREF_SEARCH_DURATION, null)),
            prefs.getStringSet(PREF_SEARCH_FIELDS, null).orEmpty()
                .mapNotNull { enumOrNull<EpisodeSearchOptions.Field>(it) },
            enumOrNull<EpisodeSearchOptions.Sort>(prefs.getString(PREF_SEARCH_SORT, null))
        )
        set(value) {
            prefs.edit()
                .putString(PREF_SEARCH_FILTER, value.filter.values.joinToString(","))
                .putString(PREF_SEARCH_DATE, value.dateRange.name)
                .putString(PREF_SEARCH_DURATION, value.durationRange.name)
                .putStringSet(PREF_SEARCH_FIELDS, value.fields.map { it.name }.toHashSet())
                .putString(PREF_SEARCH_SORT, value.sort.name)
                .apply()
        }

    /**
     * Rewrites the Audiobooks status saved before it became Unfinished / Finished: Not started and
     * In progress both become Unfinished. Idempotent.
     */
    fun migrateAudiobookShelfStatus() {
        when (prefs.getString(PREF_AUDIOBOOKS_STATUS, null)) {
            "NOT_STARTED", "IN_PROGRESS" -> prefs.edit()
                .putString(PREF_AUDIOBOOKS_STATUS, AudiobookShelfFilter.Status.UNFINISHED.name)
                .apply()
        }
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        enumValues<E>().firstOrNull { it.name == name }

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

    /** Whether the notification permission explanation was shown (answered either way). */
    var notificationRationaleShown: Boolean
        get() = prefs.getBoolean(PREF_NOTIFICATION_RATIONALE_SHOWN, false)
        set(value) {
            prefs.edit().putBoolean(PREF_NOTIFICATION_RATIONALE_SHOWN, value).apply()
        }
}
