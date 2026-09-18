package allen.town.podcast.core.pref

import allen.town.podcast.common.model.CategoryInfo
import allen.town.podcast.core.feed.SubscriptionsFilter
import allen.town.podcast.core.playback.AlbumCoverStyle
import allen.town.podcast.core.playback.NowPlayingScreen
import allen.town.podcast.core.storage.EpisodeCleanupAlgorithm
import allen.town.podcast.core.view.TopAppBarLayout.AppBarMode
import allen.town.podcast.model.download.ProxyConfig
import allen.town.podcast.model.feed.FeedCounter
import allen.town.podcast.model.feed.SortOrder
import allen.town.podcast.model.playback.MediaType
import android.content.Context
import androidx.annotation.StyleRes
import androidx.annotation.VisibleForTesting
import allen.town.podcast.theme.constants.ThemeConstants
import java.io.File

/**
 * Provides access to preferences set by the user in the settings screen. A
 * private instance of this class must first be instantiated via
 * init() or otherwise every public method will throw an Exception
 * when called.
 *
 * This object is the single entry point callers use; the implementation is split by topic into
 * [UiPrefs], [PlaybackPrefs], [DownloadPrefs], [NetworkPrefs] and [SyncPrefs], all reading the
 * shared state in [PrefsStore]. Every member below is a one-line delegation, so the preference
 * keys and defaults for one topic live together in exactly one file.
 */
object Prefs {

    // User Interface
    const val PREF_THEME = ThemeConstants.GENERAL_THEME //theme key
    const val PREF_DRAWER_FEED_ORDER_METHOD = "pref_feed_order_method"

    //drive
    const val PREF_QUEUE_KEEP_SORTED = "pref_queue_keep_sorted"

    //app start page
    const val PREF_HOME_PAGE = "pref_homepage"
    const val PREF_DRAWER_FEED_ORDER = "pref_feed_order"
    const val PREF_EXPANDED_NOTIFICATION = "pref_expand_notification"
    const val PREF_SHOW_TIME_LEFT = "pref_show_left_time" //show remaining time
    const val PREF_COMPACT_NOTIFICATION_BUTTONS = "pref_compact_noti_buttons"
    const val PREF_LOCKSCREEN_BACKGROUND = "pref_lock_screen_backgound"
    const val PREF_COLUMN_IN_LANDSCAPE = "prefColumnDisplayInLandscape"
    const val PREF_BACK_BUTTON_BEHAVIOR = "pref_backbutton_behavior"
    const val PREF_USE_EPISODE_COVER = "pref_use_episode_cover" //use episode cover
    const val PREF_SHOW_EPISODE_COVER_IN_FEED = "pref_show_episode_cover_in_feed"
    const val PREF_FILTER_FEED = "prefSubscriptionsFilter"
    const val PREF_SUBSCRIPTION_TITLE = "pref_show_sub_title"
    const val APPBAR_MODE = "appbar_mode"

    //playlist sort order
    const val PREF_QUEUE_KEEP_SORTED_ORDER = "pref_playlist_keep_order"

    // Other
    const val PREF_DELETE_REMOVES_FROM_QUEUE = "prefDeleteRemovesFromQueue"
    const val PREF_USAGE_COUNTING_DATE = "prefUsageCounting"
    const val PREF_ONLINE_PODCAST_SEARCH_HISTORY = "pref_online_podcast_search_history"
    const val PREF_FULL_LOCK_SCREEN = "pref_full_lock_screen"

    // Playback
    const val PREF_PAUSE_ON_HEADSET_DISCONNECT = "pref_pause_when_headset_disconnect"
    const val NEW_BLUR_AMOUNT = "new_blur_amount"
    const val PREF_UNPAUSE_ON_HEADSET_RECONNECT = "pref_play_when_headset_reconnect"
    const val PREF_HARDWARE_FORWARD_BUTTON = "pref_hardware_forward_button"
    const val PREF_FOLLOW_QUEUE = "pref_follow_playlist"
    const val PREF_SKIP_KEEPS_EPISODE = "pref_keep_episode_when_skip"
    const val PREF_STREAM_OVER_DOWNLOAD = "pref_allow_stream_over_download"
    const val PREF_HARDWARE_PREVIOUS_BUTTON = "pref_hardware_previous_button"
    const val PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS = "pref_pause_when_loss_focus"
    const val PREF_SNOWFALL = "pre_show_snow_fall"
    const val PREF_TOGGLE_ADD_CONTROLS = "toggle_add_controls"
    const val PREF_ADAPTIVE_COLOR_APP = "pref_adaptive_color_app"
    const val NOW_PLAYING_SCREEN_ID = "now_playing_screen_id"
    const val PREF_SMART_MARK_AS_PLAYED_SECS = "pref_smart_mark_as_played_secs"

    // Ad auto-skip
    const val PREF_AD_SKIP_ENABLED = "pref_ad_skip_enabled"
    const val PREF_AD_SKIP_SENSITIVITY = "pref_ad_skip_sensitivity"
    const val PREF_AD_SKIP_ANALYZE_ON_DOWNLOAD = "pref_ad_skip_analyze_on_download"
    const val PREF_AD_SKIP_SHOW_SNACKBAR = "pref_ad_skip_show_snackbar"
    const val PREF_AD_SKIP_ANALYZED_MEDIA = "pref_ad_skip_analyzed_media"

    // Network
    const val PREF_UPDATE_INTERVAL = "pref_auto_refresh_interval"
    const val PREF_ENQUEUE_LOCATION = "pref_episode_location_in_playlist"
    const val PREF_PARALLEL_DOWNLOADS = "pref_parallel_downloads"
    const val PREF_EPISODE_CACHE_SIZE = "pref_episodes_cache_size"
    const val PREF_ENABLE_AUTODL = "pref_auto_download_enable"
    const val PREF_ENABLE_AUTODL_ON_BATTERY = "pref_auto_download_enable_on_battery"
    const val PREF_EPISODE_CLEANUP = "pref_episodes_clean_up"

    // Services
    public const val PREF_GPODNET_NOTIFICATIONS = "pref_show_gpod_notifications"

    const val EPISODE_CLEANUP_QUEUE = -1
    const val EPISODE_CLEANUP_NULL = -2
    const val EPISODE_CLEANUP_EXCEPT_FAVORITE = -3
    const val EPISODE_CLEANUP_DEFAULT = 0

    //global audio playback speed
    const val PREF_PLAYBACK_SKIP_SILENCE = "pref_global_skip_silence"

    // Constants
    const val FEED_ORDER_COUNTER = 0
    const val FEED_ORDER_ALPHABETICAL = 1
    const val ORDER_ASC = "asc"
    const val ORDER_DESC = "desc"

    /** Sets up the Prefs class. Must run before any preference below is touched. */
    @JvmStatic
    fun init(context: Context) = PrefsStore.init(context)

    /** Drops the process-wide state so a unit test can assert the uninitialised behaviour. */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @JvmStatic
    fun resetForTests() = PrefsStore.resetForTests()

    @get:StyleRes
    @JvmStatic
    val theme: Int
        get() = UiPrefs.theme

    @JvmStatic
    var compactNotificationButtons: List<Int>
        get() = UiPrefs.compactNotificationButtons
        set(items) {
            UiPrefs.compactNotificationButtons = items
        }

    @JvmStatic
    fun showRewindOnCompactNotification(): Boolean = UiPrefs.showRewindOnCompactNotification()

    @JvmStatic
    fun showFastForwardOnCompactNotification(): Boolean =
        UiPrefs.showFastForwardOnCompactNotification()

    @JvmStatic
    fun showSkipOnCompactNotification(): Boolean = UiPrefs.showSkipOnCompactNotification()

    @JvmStatic
    val feedOrder: Int
        get() = UiPrefs.feedOrder

    @JvmStatic
    fun setFeedOrder(selected: String?) = UiPrefs.setFeedOrder(selected)

    @JvmStatic
    var feedOrderMethod: String
        get() = UiPrefs.feedOrderMethod
        set(selected) {
            UiPrefs.feedOrderMethod = selected
        }

    @JvmStatic
    val feedCounterSetting: FeedCounter
        get() = UiPrefs.feedCounterSetting

    @JvmStatic
    val useEpisodeCoverSetting: Boolean
        get() = UiPrefs.useEpisodeCoverSetting

    @JvmStatic
    val showEpisodeCoverInFeed: Boolean
        get() = UiPrefs.showEpisodeCoverInFeed

    @JvmStatic
    fun shouldShowRemainingTime(): Boolean = UiPrefs.shouldShowRemainingTime()

    @JvmStatic
    fun shouldShowLastPageOfHome(): Boolean = UiPrefs.shouldShowLastPageOfHome()

    @JvmStatic
    fun setShowRemainTimeSetting(showRemain: Boolean?) = UiPrefs.setShowRemainTimeSetting(showRemain)

    @JvmStatic
    val notifyPriority: Int
        get() = UiPrefs.notifyPriority

    @JvmStatic
    val isPersistNotify: Boolean
        get() = UiPrefs.isPersistNotify

    @JvmStatic
    fun setLockscreenBackground(): Boolean = UiPrefs.setLockscreenBackground()

    @JvmStatic
    fun showDownloadReport(): Boolean = UiPrefs.showDownloadReport()

    @JvmStatic
    val showDownloadReportRaw: Boolean
        get() = UiPrefs.showDownloadReportRaw

    @JvmStatic
    fun showAutoDownloadReport(): Boolean = UiPrefs.showAutoDownloadReport()

    @JvmStatic
    val showAutoDownloadReportRaw: Boolean
        get() = UiPrefs.showAutoDownloadReportRaw

    @JvmStatic
    fun enqueueDownloadedEpisodes(): Boolean = DownloadPrefs.enqueueDownloadedEpisodes()

    @VisibleForTesting
    @JvmStatic
    fun setEnqueueDownloadedEpisodes(enqueueDownloadedEpisodes: Boolean) =
        DownloadPrefs.setEnqueueDownloadedEpisodes(enqueueDownloadedEpisodes)

    @JvmStatic
    var enqueueLocation: EnqueueLocation
        get() = DownloadPrefs.enqueueLocation
        set(location) {
            DownloadPrefs.enqueueLocation = location
        }

    @JvmStatic
    val isPauseOnHeadsetDisconnect: Boolean
        get() = PlaybackPrefs.isPauseOnHeadsetDisconnect

    @JvmStatic
    val isUnpauseOnHeadsetReconnect: Boolean
        get() = PlaybackPrefs.isUnpauseOnHeadsetReconnect

    @JvmStatic
    val isUnpauseOnBluetoothReconnect: Boolean
        get() = PlaybackPrefs.isUnpauseOnBluetoothReconnect

    @JvmStatic
    val hardwareForwardButton: Int
        get() = PlaybackPrefs.hardwareForwardButton

    @JvmStatic
    val hardwarePreviousButton: Int
        get() = PlaybackPrefs.hardwarePreviousButton

    @set:VisibleForTesting
    @JvmStatic
    var isFollowQueue: Boolean
        get() = PlaybackPrefs.isFollowQueue
        set(value) {
            PlaybackPrefs.isFollowQueue = value
        }

    @JvmStatic
    fun shouldSkipKeepEpisode(): Boolean = PlaybackPrefs.shouldSkipKeepEpisode()

    @JvmStatic
    fun shouldFavoriteKeepEpisode(): Boolean = PlaybackPrefs.shouldFavoriteKeepEpisode()

    @JvmStatic
    val isAutoDelete: Boolean
        get() = DownloadPrefs.isAutoDelete

    @JvmStatic
    val smartMarkAsPlayedSecs: Int
        get() = PlaybackPrefs.smartMarkAsPlayedSecs

    @JvmStatic
    fun shouldDeleteRemoveFromQueue(): Boolean = DownloadPrefs.shouldDeleteRemoveFromQueue()

    @JvmStatic
    fun getPlaybackSpeed(mediaType: MediaType?): Float = PlaybackPrefs.getPlaybackSpeed(mediaType)

    @JvmStatic
    var podcastSearchEngineList: List<CategoryInfo>
        get() = SyncPrefs.podcastSearchEngineList
        set(value) {
            SyncPrefs.podcastSearchEngineList = value
        }

    @JvmStatic
    var videoPlaybackSpeed: Float
        get() = PlaybackPrefs.videoPlaybackSpeed
        set(speed) {
            PlaybackPrefs.videoPlaybackSpeed = speed
        }

    @JvmStatic
    var isSkipSilence: Boolean
        get() = PlaybackPrefs.isSkipSilence
        set(skipSilence) {
            PlaybackPrefs.isSkipSilence = skipSilence
        }

    @JvmStatic
    var playbackSpeedArray: List<Float>
        get() = PlaybackPrefs.playbackSpeedArray
        set(speeds) {
            PlaybackPrefs.playbackSpeedArray = speeds
        }

    @JvmStatic
    fun shouldPauseForFocusLoss(): Boolean = PlaybackPrefs.shouldPauseForFocusLoss()

    @JvmStatic
    var updateInterval: Long
        get() = NetworkPrefs.updateInterval
        set(hours) {
            NetworkPrefs.updateInterval = hours
        }

    @JvmStatic
    val updateTimeOfDay: IntArray
        get() = NetworkPrefs.updateTimeOfDay

    @JvmStatic
    val isAutoUpdateDisabled: Boolean
        get() = NetworkPrefs.isAutoUpdateDisabled

    @JvmStatic
    var isAllowMobileFeedRefresh: Boolean
        get() = NetworkPrefs.isAllowMobileFeedRefresh
        set(allow) {
            NetworkPrefs.isAllowMobileFeedRefresh = allow
        }

    @JvmStatic
    var isAllowMobileEpisodeDownload: Boolean
        get() = NetworkPrefs.isAllowMobileEpisodeDownload
        set(allow) {
            NetworkPrefs.isAllowMobileEpisodeDownload = allow
        }

    @JvmStatic
    var isAllowMobileAutoDownload: Boolean
        get() = NetworkPrefs.isAllowMobileAutoDownload
        set(allow) {
            NetworkPrefs.isAllowMobileAutoDownload = allow
        }

    @JvmStatic
    var isAllowMobileStreaming: Boolean
        get() = NetworkPrefs.isAllowMobileStreaming
        set(allow) {
            NetworkPrefs.isAllowMobileStreaming = allow
        }

    @JvmStatic
    var isAllowMobileImages: Boolean
        get() = NetworkPrefs.isAllowMobileImages
        set(allow) {
            NetworkPrefs.isAllowMobileImages = allow
        }

    @JvmStatic
    val parallelDownloads: Int
        get() = DownloadPrefs.parallelDownloads

    @JvmStatic
    val episodeCacheSizeUnlimited: Int
        get() = DownloadPrefs.episodeCacheSizeUnlimited

    @JvmStatic
    val episodeCacheSize: Int
        get() = DownloadPrefs.episodeCacheSize

    @set:VisibleForTesting
    @JvmStatic
    var isEnableAutodownload: Boolean
        get() = DownloadPrefs.isEnableAutodownload
        set(enabled) {
            DownloadPrefs.isEnableAutodownload = enabled
        }

    @JvmStatic
    val isEnableAutodownloadOnBattery: Boolean
        get() = DownloadPrefs.isEnableAutodownloadOnBattery

    @JvmStatic
    var fastForwardSecs: Int
        get() = PlaybackPrefs.fastForwardSecs
        set(secs) {
            PlaybackPrefs.fastForwardSecs = secs
        }

    @JvmStatic
    var rewindSecs: Int
        get() = PlaybackPrefs.rewindSecs
        set(secs) {
            PlaybackPrefs.rewindSecs = secs
        }

    @JvmStatic
    var proxyConfig: ProxyConfig
        get() = NetworkPrefs.proxyConfig
        set(config) {
            NetworkPrefs.proxyConfig = config
        }

    @JvmStatic
    fun shouldResumeAfterCall(): Boolean = PlaybackPrefs.shouldResumeAfterCall()

    @JvmStatic
    fun showSnowFall(): Boolean = UiPrefs.showSnowFall()

    @JvmStatic
    fun showExtraMiniButtons(): Boolean = UiPrefs.showExtraMiniButtons()

    @JvmStatic
    val isAdapterColor: Boolean
        get() = UiPrefs.isAdapterColor

    @JvmStatic
    var nowPlayingScreen: NowPlayingScreen
        get() = UiPrefs.nowPlayingScreen
        set(nowPlayingScreen) {
            UiPrefs.nowPlayingScreen = nowPlayingScreen
        }

    @JvmStatic
    var ALBUM_COVER_STYLE: String
        get() = UiPrefs.ALBUM_COVER_STYLE
        set(value) {
            UiPrefs.ALBUM_COVER_STYLE = value
        }

    @JvmStatic
    var albumCoverStyle: AlbumCoverStyle?
        get() = UiPrefs.albumCoverStyle
        set(albumCoverStyle) {
            UiPrefs.albumCoverStyle = albumCoverStyle
        }

    @JvmStatic
    val blurAmount: Int
        get() = UiPrefs.blurAmount

    @JvmStatic
    var isPlaylistLocked: Boolean
        get() = PlaybackPrefs.isPlaylistLocked
        set(locked) {
            PlaybackPrefs.isPlaylistLocked = locked
        }

    @JvmStatic
    fun setPlaybackSpeed(speed: Float) = PlaybackPrefs.setPlaybackSpeed(speed)

    @JvmStatic
    var versionCode: Int
        get() = SyncPrefs.versionCode
        set(versionCode) {
            SyncPrefs.versionCode = versionCode
        }

    @JvmStatic
    var notifyVersionCode: Int
        get() = SyncPrefs.notifyVersionCode
        set(versionCode) {
            SyncPrefs.notifyVersionCode = versionCode
        }

    @JvmStatic
    fun lastVersionChecked(newVersion: Int): Boolean = SyncPrefs.lastVersionChecked(newVersion)

    @JvmStatic
    fun lastNotifyVersionChecked(newVersion: Int): Boolean =
        SyncPrefs.lastNotifyVersionChecked(newVersion)

    @JvmStatic
    fun setUpdateTimeOfDay(hourOfDay: Int, minute: Int) =
        NetworkPrefs.setUpdateTimeOfDay(hourOfDay, minute)

    @JvmStatic
    fun disableAutoUpdate(context: Context?) = NetworkPrefs.disableAutoUpdate(context)

    @JvmStatic
    fun gpodnetNotificationsEnabled(): Boolean = SyncPrefs.gpodnetNotificationsEnabled()

    @JvmStatic
    val gpodnetNotificationsEnabledRaw: Boolean
        get() = SyncPrefs.gpodnetNotificationsEnabledRaw

    @JvmStatic
    fun setGpodnetNotificationsEnabled() = SyncPrefs.setGpodnetNotificationsEnabled()

    @JvmStatic
    fun useExoplayer(): Boolean = PlaybackPrefs.useExoplayer()

    @JvmStatic
    fun stereoToMono(): Boolean = PlaybackPrefs.stereoToMono()

    @JvmStatic
    fun stereoToMono(enable: Boolean) = PlaybackPrefs.stereoToMono(enable)

    @JvmStatic
    fun audioLoudness(): Boolean = PlaybackPrefs.audioLoudness()

    @JvmStatic
    fun setAudioLoudness(enable: Boolean) = PlaybackPrefs.setAudioLoudness(enable)

    @JvmStatic
    val episodeCleanupAlgorithm: EpisodeCleanupAlgorithm
        get() = DownloadPrefs.episodeCleanupAlgorithm

    @JvmStatic
    var episodeCleanupValue: Int
        get() = DownloadPrefs.episodeCleanupValue
        set(episodeCleanupValue) {
            DownloadPrefs.episodeCleanupValue = episodeCleanupValue
        }

    @JvmStatic
    fun getDataFolder(type: String?): File? = DownloadPrefs.getDataFolder(type)

    @JvmStatic
    fun setDataFolder(dir: String) = DownloadPrefs.setDataFolder(dir)

    @JvmStatic
    val isAutoUpdateTimeOfDay: Boolean
        get() = NetworkPrefs.isAutoUpdateTimeOfDay

    @JvmStatic
    val backButtonBehavior: BackButtonBehavior
        get() = UiPrefs.backButtonBehavior

    @JvmStatic
    fun timeRespectsSpeed(): Boolean = PlaybackPrefs.timeRespectsSpeed()

    @JvmStatic
    var isStreamOverDownload: Boolean
        get() = DownloadPrefs.isStreamOverDownload
        set(stream) {
            DownloadPrefs.isStreamOverDownload = stream
        }

    @JvmStatic
    var isPlaylistKeepSorted: Boolean
        get() = PlaybackPrefs.isPlaylistKeepSorted
        set(keepSorted) {
            PlaybackPrefs.isPlaylistKeepSorted = keepSorted
        }

    @JvmStatic
    var queueKeepSortedOrder: SortOrder?
        get() = PlaybackPrefs.queueKeepSortedOrder
        set(sortOrder) {
            PlaybackPrefs.queueKeepSortedOrder = sortOrder
        }

    @JvmStatic
    var subscriptionsFilter: SubscriptionsFilter
        get() = UiPrefs.subscriptionsFilter
        set(value) {
            UiPrefs.subscriptionsFilter = value
        }

    @JvmStatic
    fun shouldShowSubscriptionTitle(): Boolean = UiPrefs.shouldShowSubscriptionTitle()

    @JvmStatic
    var onlinePodcastSearchHistory: List<String?>
        get() = SyncPrefs.onlinePodcastSearchHistory
        set(keywords) {
            SyncPrefs.onlinePodcastSearchHistory = keywords
        }

    @JvmStatic
    fun clearOnlinePodcastSearchHistory() = SyncPrefs.clearOnlinePodcastSearchHistory()

    @JvmStatic
    val isFullLockScreen: Boolean
        get() = UiPrefs.isFullLockScreen

    @JvmStatic
    val appBarMode: AppBarMode
        get() = UiPrefs.appBarMode

    /** The master switch of the ad auto-skip feature. Off on a fresh install. */
    @JvmStatic
    var isAdSkipEnabled: Boolean
        get() = AdSkipPrefs.isAdSkipEnabled
        set(enabled) {
            AdSkipPrefs.isAdSkipEnabled = enabled
        }

    /** "low", "medium" or "high"; see [AdSkipPrefs]. */
    @JvmStatic
    var adSkipSensitivity: String
        get() = AdSkipPrefs.adSkipSensitivity
        set(sensitivity) {
            AdSkipPrefs.adSkipSensitivity = sensitivity
        }

    /**
     * The confidence a detected segment needs before the player skips it. Chapter and manual
     * segments are exact and ignore this threshold.
     */
    @JvmStatic
    val adSkipMinConfidence: Float
        get() = AdSkipPrefs.adSkipMinConfidence

    /** Whether a finished download queues an ad analysis run for that episode. */
    @JvmStatic
    var isAdSkipAnalyzeOnDownload: Boolean
        get() = AdSkipPrefs.isAdSkipAnalyzeOnDownload
        set(analyze) {
            AdSkipPrefs.isAdSkipAnalyzeOnDownload = analyze
        }

    /** Whether the app layer shows a snackbar with Undo after an ad was skipped. */
    @JvmStatic
    var isAdSkipShowSnackbar: Boolean
        get() = AdSkipPrefs.isAdSkipShowSnackbar
        set(show) {
            AdSkipPrefs.isAdSkipShowSnackbar = show
        }

    /** Whether the ad analysis has already completed for a media id; see [AdSkipPrefs]. */
    @JvmStatic
    fun isAdAnalyzed(mediaId: Long): Boolean = AdSkipPrefs.isAdAnalyzed(mediaId)

    /** Remembers that the ad analysis completed for a media id. */
    @JvmStatic
    fun markAdAnalyzed(mediaId: Long) = AdSkipPrefs.markAdAnalyzed(mediaId)

    fun shouldShowColumnInLandscape(): Boolean = UiPrefs.shouldShowColumnInLandscape()

    fun shouldSyncOnStart(): Boolean = NetworkPrefs.shouldSyncOnStart()

    enum class EnqueueLocation {
        BACK, FRONT, AFTER_CURRENTLY_PLAYING
    }

    enum class BackButtonBehavior {
        DEFAULT, OPEN_DRAWER, DOUBLE_TAP, SHOW_PROMPT
    }
}
