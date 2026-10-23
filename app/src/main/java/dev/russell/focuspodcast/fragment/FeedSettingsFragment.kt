package dev.russell.focuspodcast.fragment

import dev.russell.focuspodcast.common.common.prefs.supportv7.ATESwitchPreference
import dev.russell.focuspodcast.common.views.AccentMaterialDialog
import dev.russell.focuspodcast.common.util.TopSnackbarUtil.showSnack
import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.core.feed.util.AdSkipUtils
import dev.russell.focuspodcast.core.feed.util.VideoModeUtils
import dev.russell.focuspodcast.core.feed.util.PlaybackSpeedUtils
import dev.russell.focuspodcast.core.pref.Prefs
import dev.russell.focuspodcast.core.pref.Prefs.isEnableAutodownload
import dev.russell.focuspodcast.core.storage.DBReader
import dev.russell.focuspodcast.core.storage.DBTasks
import dev.russell.focuspodcast.core.storage.DBWriter
import dev.russell.focuspodcast.databinding.PlaybackSpeedFeedSettingDialogBinding
import dev.russell.focuspodcast.dialog.AuthenticationDialog
import dev.russell.focuspodcast.dialog.FeedSkipPreDialog
import dev.russell.focuspodcast.dialog.TagEditDialog
import dev.russell.focuspodcast.dialog.filter.KeywordFilterSheet
import dev.russell.focuspodcast.event.playback.TitleChangeEvent
import dev.russell.focuspodcast.event.settings.AdSkipChangedEvent
import dev.russell.focuspodcast.event.settings.SkipIntroEndingChangedEvent
import dev.russell.focuspodcast.event.settings.VolumeAdaptionChangedEvent
import dev.russell.focuspodcast.fragment.pref.AbsSettingsFragment
import dev.russell.focuspodcast.fragment.pref.AudioEffectFragment
import dev.russell.focuspodcast.fragment.pref.DiscardingPreferenceDataStore
import dev.russell.focuspodcast.model.feed.*
import dev.russell.focuspodcast.model.feed.FeedPreferences.AutoDeleteAction
import dev.russell.focuspodcast.model.playback.MediaType
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.CollapsingToolbarLayout
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.MaybeEmitter
import io.reactivex.MaybeOnSubscribe
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

class FeedSettingsFragment : Fragment() {
    private var disposable: Disposable? = null
    fun setTitle(@StringRes titleId: Int) {
        // the event can arrive before onCreateView or after onDestroyView
        collapsingToolbarLayout?.title = getString(titleId)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun titleChange(titleChangeEvent: TitleChangeEvent) {
        setTitle(titleChangeEvent.title)
    }

    private var collapsingToolbarLayout: CollapsingToolbarLayout? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.feedsettings, container, false)
        val feedId = requireArguments().getLong(EXTRA_FEED_ID)
        val toolbar = root.findViewById<Toolbar>(R.id.toolbar)
        collapsingToolbarLayout = root.findViewById(R.id.collapsingToolbarLayout)
        toolbar.setNavigationOnClickListener { v: View? -> parentFragmentManager.popBackStack() }
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.settings_fragment_container,
                FeedSettingsPreferenceFragment.newInstance(feedId), "settings_fragment"
            )
            .commitAllowingStateLoss()
        disposable = Maybe.create(MaybeOnSubscribe { emitter: MaybeEmitter<Feed> ->
            val feed = DBReader.getFeed(feedId)
            if (feed != null) {
                emitter.onSuccess(feed)
            } else {
                emitter.onComplete()
            }
        } as MaybeOnSubscribe<Feed>)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: Feed -> toolbar.subtitle = result.title },
                { error: Throwable? -> Log.d(TAG, Log.getStackTraceString(error)) }
            ) {}
        return root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        collapsingToolbarLayout = null
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable?.dispose()
        EventBus.getDefault().unregister(this)
    }

    class FeedSettingsPreferenceFragment : AbsSettingsFragment(), KeywordFilterSheet.Host {
        private var feed: Feed? = null
        private var disposable: Disposable? = null
        private val preferenceWrites = CompositeDisposable()
        private var feedPreferences: FeedPreferences? = null
        override fun onResume() {
            super.onResume()
            EventBus.getDefault().post(TitleChangeEvent(R.string.feed_settings_label))
        }

        override fun onCreateRecyclerView(
            inflater: LayoutInflater,
            parent: ViewGroup,
            state: Bundle?
        ): RecyclerView {
            val view = super.onCreateRecyclerView(inflater, parent, state)
            // To prevent transition animation because of summary update
            view.itemAnimator = null
            view.layoutAnimation = null
            return view
        }

        /** Every key used here is declared in res/xml/feed_settings.xml. */
        private fun <T : Preference> requirePreference(key: CharSequence): T =
            checkNotNull(findPreference(key)) { "feed_settings.xml has no preference '$key'" }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Every value on this screen belongs to one feed and is loaded from its database row
            // below, so nothing may be persisted to (or restored from) the app-wide
            // SharedPreferences: those keys would be shared by all feeds, and a key whose type
            // changed (feedAdSkip went from a switch to a list) would throw on inflation.
            preferenceManager.preferenceDataStore = DiscardingPreferenceDataStore()
            addPreferencesFromResource(R.xml.feed_settings)
            // To prevent displaying partially loaded data
            requirePreference<Preference>(PREF_SCREEN).isVisible = false
            val feedId = requireArguments().getLong(EXTRA_FEED_ID)
            disposable = Maybe.create(
                MaybeOnSubscribe { emitter: MaybeEmitter<Feed?> ->
                    val feed = DBReader.getFeed(feedId)
                    if (feed != null) {
                        emitter.onSuccess(feed)
                    } else {
                        emitter.onComplete()
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result: Feed? ->
                    val loadedFeed = result
                    val loadedPreferences = loadedFeed?.preferences
                    if (loadedFeed == null || loadedPreferences == null) {
                        Log.e(TAG, "feed $feedId has no preferences, keeping the screen hidden")
                        return@subscribe
                    }
                    feed = loadedFeed
                    feedPreferences = loadedPreferences
                    setupAutoDownloadGlobalPreference()
                    setupAutoDownloadPreference()
                    setupKeepUpdatedPreference()
                    setupAutoDeletePreference()
                    setupVolumeReductionPreferences()
                    setupAuthentificationPreference()
                    setupEpisodeFilterPreference()
                    setupPlaybackSpeedPreference()
                    setupFeedAutoSkipPreference()
                    setupFeedAdSkipPreference()
                    setupFeedVideoModePreference()
                    setupEpisodeNotificationPreference()
                    setupAudiobookPreference()
                    setupTags()
                    updateAutoDeleteSummary()
                    updateVolumeReductionValue()
                    updateAutoDownloadEnabled()
                    setupAudioEffectPreference(loadedPreferences, loadedFeed)
                    setupResetDismissedWarningsPreference(loadedFeed.id)
                    if (loadedFeed.isLocalFeed) {
                        requirePreference<Preference>(PREF_AUTHENTICATION).isVisible = false
                        requirePreference<Preference>(PREF_AUTO_DELETE).isVisible = false
                        requirePreference<Preference>(PREF_CATEGORY_AUTO_DOWNLOAD).isVisible = false
                    }
                    requirePreference<Preference>(PREF_SCREEN).isVisible = true
                }, { error: Throwable? -> Log.d(TAG, Log.getStackTraceString(error)) }) {}
        }

        override fun onDestroy() {
            super.onDestroy()
            disposable?.dispose()
            preferenceWrites.dispose()
        }

        private fun setupFeedAutoSkipPreference() {
            val feedPreferences = this.feedPreferences ?: return
            val feed = this.feed ?: return
            requirePreference<Preference>(PREF_AUTO_SKIP).onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    object : FeedSkipPreDialog(
                        context,
                        feedPreferences.feedSkipIntro,
                        feedPreferences.feedSkipEnding
                    ) {
                        override fun onConfirmed(skipIntro: Int, skipEnding: Int) {
                            feedPreferences.feedSkipIntro = skipIntro
                            feedPreferences.feedSkipEnding = skipEnding
                            DBWriter.setFeedPreferences(feedPreferences)
                            EventBus.getDefault().post(
                                SkipIntroEndingChangedEvent(
                                    feedPreferences.feedSkipIntro,
                                    feedPreferences.feedSkipEnding,
                                    feed.id
                                )
                            )
                        }
                    }.show()
                    false
                }
        }

        /**
         * The per-podcast ad-skip choice: follow the default from the playback settings, or on or
         * off for this podcast only. The entries are built here so that the "use default" entry
         * can say what the default currently is. Writes go through [AdSkipUtils], which stores the
         * override and then posts an [AdSkipChangedEvent], so a playing episode of this feed
         * re-evaluates immediately.
         */
        private fun setupFeedAdSkipPreference() {
            val feedPreferences = this.feedPreferences ?: return
            val feed = this.feed ?: return
            val pref = requirePreference<ListPreference>(PREF_AD_SKIP)
            pref.entries = arrayOf(
                getString(
                    if (Prefs.isAdSkipEnabled) R.string.feed_ad_skip_default_on
                    else R.string.feed_ad_skip_default_off
                ),
                getString(R.string.feed_ad_skip_on),
                getString(R.string.feed_ad_skip_off)
            )
            pref.entryValues = arrayOf(AD_SKIP_DEFAULT, AD_SKIP_ON, AD_SKIP_OFF)
            updateFeedAdSkipSummary(pref, feedPreferences.adSkipOverride)
            pref.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                    when (newValue as? String) {
                        AD_SKIP_ON -> AdSkipUtils.setAdSkipForFeed(feed, true)
                        AD_SKIP_OFF -> AdSkipUtils.setAdSkipForFeed(feed, false)
                        else -> AdSkipUtils.resetAdSkipForFeed(feed)
                    }
                    updateFeedAdSkipSummary(pref, feedPreferences.adSkipOverride)
                    false
                }
        }

        /**
         * How this podcast's video episodes play: follow the default from the playback settings,
         * or video / audio only for this podcast. Built like the ad-skip choice above; nothing is
         * playing differently until the next video episode starts, so no event is posted.
         */
        private fun setupFeedVideoModePreference() {
            val feedPreferences = this.feedPreferences ?: return
            val feed = this.feed ?: return
            val pref = requirePreference<ListPreference>(PREF_VIDEO_MODE)
            pref.entries = arrayOf(
                getString(
                    if (Prefs.isPlayVideoAsVideo) R.string.feed_video_default_video
                    else R.string.feed_video_default_audio
                ),
                getString(R.string.video_episodes_as_video),
                getString(R.string.video_episodes_as_audio)
            )
            pref.entryValues = arrayOf(VIDEO_MODE_DEFAULT, VIDEO_MODE_VIDEO, VIDEO_MODE_AUDIO)
            updateFeedVideoModeSummary(pref, feedPreferences.videoOverride)
            pref.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                    when (newValue as? String) {
                        VIDEO_MODE_VIDEO -> VideoModeUtils.setVideoForFeed(feed, true)
                        VIDEO_MODE_AUDIO -> VideoModeUtils.setVideoForFeed(feed, false)
                        else -> VideoModeUtils.resetVideoForFeed(feed)
                    }
                    updateFeedVideoModeSummary(pref, feedPreferences.videoOverride)
                    false
                }
        }

        /** Selects the stored choice and spells out the effective state in the summary. */
        private fun updateFeedVideoModeSummary(pref: ListPreference, override: Boolean?) {
            when (override) {
                null -> {
                    pref.value = VIDEO_MODE_DEFAULT
                    pref.setSummary(
                        if (Prefs.isPlayVideoAsVideo) R.string.feed_video_default_video_sum
                        else R.string.feed_video_default_audio_sum
                    )
                }
                true -> {
                    pref.value = VIDEO_MODE_VIDEO
                    pref.setSummary(R.string.feed_video_video_sum)
                }
                false -> {
                    pref.value = VIDEO_MODE_AUDIO
                    pref.setSummary(R.string.feed_video_audio_sum)
                }
            }
        }

        /**
         * "Reset dismissed warnings (n)": only shown while the user has hidden at least one
         * duplicate-episode warning of this podcast in the download log.
         */
        private fun setupResetDismissedWarningsPreference(feedId: Long) {
            val pref = requirePreference<Preference>(PREF_RESET_DISMISSED_WARNINGS)
            pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val activity = requireActivity()
                val reset = DBWriter.resetDismissedWarnings(feedId)
                preferenceWrites.add(
                    Completable.fromAction { reset.get() }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            showSnack(activity, R.string.reset_dismissed_warnings_done, Toast.LENGTH_SHORT)
                            loadDismissedWarningCount(feedId)
                        }, { error: Throwable ->
                            Log.e(TAG, "resetting the dismissed warnings failed", error)
                        })
                )
                true
            }
            loadDismissedWarningCount(feedId)
        }

        private fun loadDismissedWarningCount(feedId: Long) {
            preferenceWrites.add(
                Single.fromCallable { DBReader.getDismissedFeedWarningCount(feedId) }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ count: Int ->
                        val pref = findPreference<Preference>(PREF_RESET_DISMISSED_WARNINGS)
                            ?: return@subscribe
                        pref.title = getString(R.string.reset_dismissed_warnings_title, count)
                        pref.isVisible = count > 0
                    }, { error: Throwable ->
                        Log.e(TAG, "reading the dismissed warning count failed", error)
                    })
            )
        }

        /** Selects the stored choice and spells out the effective state in the summary. */
        private fun updateFeedAdSkipSummary(pref: ListPreference, override: Boolean?) {
            when (override) {
                null -> {
                    pref.value = AD_SKIP_DEFAULT
                    pref.setSummary(
                        if (Prefs.isAdSkipEnabled) R.string.feed_ad_skip_default_on_sum
                        else R.string.feed_ad_skip_default_off_sum
                    )
                }
                true -> {
                    pref.value = AD_SKIP_ON
                    pref.setSummary(R.string.feed_ad_skip_on_sum)
                }
                false -> {
                    pref.value = AD_SKIP_OFF
                    pref.setSummary(R.string.feed_ad_skip_off_sum)
                }
            }
        }

        /**
         * The podcast follows the default speed until a different speed is saved here (or picked
         * in the player); while it has its own, the dialog offers "Reset to default" and "Set as
         * default", like the player's speed dialog.
         */
        private fun setupPlaybackSpeedPreference() {
            val feedPreferences = this.feedPreferences ?: return
            val feed = this.feed ?: return
            val feedPlaybackSpeedPreference =
                requirePreference<Preference>(PREF_FEED_PLAYBACK_SPEED)
            updatePlaybackSpeedSummary(feedPlaybackSpeedPreference, feedPreferences)
            feedPlaybackSpeedPreference.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val viewBinding = PlaybackSpeedFeedSettingDialogBinding.inflate(
                        layoutInflater
                    )
                    viewBinding.useGlobalCheckbox.visibility = View.GONE
                    viewBinding.seekBar.setProgressChangedListener { speed: Float? ->
                        viewBinding.currentSpeedLabel.text = String.format(
                            Locale.getDefault(), "%.1fx", speed
                        )
                    }
                    // A podcast that follows the default starts from the default speed.
                    viewBinding.seekBar.updateSpeed(effectiveSpeed(feedPreferences))
                    val shownSpeed = viewBinding.seekBar.currentSpeed
                    val hasOwnSpeed = PlaybackSpeedUtils.hasOwnSpeed(feedPreferences)
                    val card = viewBinding.ownSettingsCard
                    card.root.visibility = if (hasOwnSpeed) View.VISIBLE else View.GONE
                    card.ownSettingsText.setText(R.string.podcast_own_speed_applied)
                    val dialog = AccentMaterialDialog(
                        requireContext(),
                        R.style.MaterialAlertDialogTheme
                    )
                        .setTitle(R.string.playback_speed)
                        .setView(viewBinding.root)
                        .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                            val newSpeed = viewBinding.seekBar.currentSpeed
                            // Only a change gives a podcast that follows the default its own speed.
                            if (hasOwnSpeed || newSpeed != shownSpeed) {
                                PlaybackSpeedUtils.setFeedSpeed(feed.id, feedPreferences, newSpeed)
                                updatePlaybackSpeedSummary(feedPlaybackSpeedPreference, feedPreferences)
                            }
                        }
                        .setNegativeButton(R.string.cancel_label, null)
                        .create()
                    card.resetToDefault.setOnClickListener {
                        PlaybackSpeedUtils.setFeedSpeed(feed.id, feedPreferences, FeedPreferences.SPEED_USE_GLOBAL)
                        updatePlaybackSpeedSummary(feedPlaybackSpeedPreference, feedPreferences)
                        dialog.dismiss()
                    }
                    card.setAsDefault.setOnClickListener {
                        PlaybackSpeedUtils.makeDefault(feed.id, feedPreferences)
                        updatePlaybackSpeedSummary(feedPlaybackSpeedPreference, feedPreferences)
                        Toast.makeText(requireContext(), R.string.default_updated, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    dialog.show()
                    true
                }
        }

        private fun effectiveSpeed(feedPreferences: FeedPreferences): Float =
            PlaybackSpeedUtils.resolveSpeed(feedPreferences, Prefs.getPlaybackSpeed(MediaType.AUDIO))

        /** Shows the speed in effect and whether it is the default or this podcast's own. */
        private fun updatePlaybackSpeedSummary(pref: Preference, feedPreferences: FeedPreferences) {
            val speed = String.format(Locale.getDefault(), "%.2fx", effectiveSpeed(feedPreferences))
            pref.summary = getString(
                if (PlaybackSpeedUtils.hasOwnSpeed(feedPreferences)) R.string.feed_speed_own_sum
                else R.string.feed_speed_default_sum,
                speed
            )
        }

        private fun setupEpisodeFilterPreference() {
            val feedPreferences = this.feedPreferences ?: return
            requirePreference<Preference>(PREF_EPISODE_FILTER).onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    if (childFragmentManager.findFragmentByTag(KeywordFilterSheet.TAG) == null) {
                        KeywordFilterSheet.newInstance(feedPreferences.feedID, feedPreferences.filter)
                            .show(childFragmentManager, KeywordFilterSheet.TAG)
                    }
                    false
                }
        }

        override fun onKeywordFilterSaved(filter: FeedFilter) {
            val feedPreferences = this.feedPreferences
            if (feedPreferences == null) {
                // recreated and not loaded again yet: store into the current row
                val feedId = requireArguments().getLong(EXTRA_FEED_ID)
                DBWriter.updateFeedPreferences(feedId) { it.filter = filter }
                return
            }
            feedPreferences.filter = filter
            DBWriter.setFeedPreferences(feedPreferences)
        }

        private fun setupAuthentificationPreference() {
            val feedPreferences = this.feedPreferences ?: return
            requirePreference<Preference>(PREF_AUTHENTICATION).onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    object : AuthenticationDialog(
                        context,
                        R.string.authentication_label, true,
                        feedPreferences.username, feedPreferences.password
                    ) {
                        override fun onConfirmed(username: String?, password: String?) {
                            feedPreferences.username = username
                            feedPreferences.password = password
                            val setPreferencesFuture = DBWriter.setFeedPreferences(feedPreferences)
                            // the refresh has to wait for the new credentials to be written,
                            // otherwise it authenticates with the old ones
                            val context = requireContext().applicationContext
                            val refreshedFeed = feed
                            preferenceWrites.add(
                                Completable.fromAction {
                                    setPreferencesFuture.get()
                                    DBTasks.forceRefreshFeed(context, refreshedFeed, true)
                                }
                                    .subscribeOn(Schedulers.io())
                                    .subscribe(
                                        { },
                                        { error: Throwable ->
                                            Log.e(
                                                TAG,
                                                "refreshing after a credential change failed",
                                                error
                                            )
                                        })
                            )
                        }
                    }.show()
                    false
                }
        }

        private fun setupAutoDeletePreference() {
            val feedPreferences = this.feedPreferences ?: return
            requirePreference<Preference>(PREF_AUTO_DELETE).onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
                    when (newValue as String?) {
                        "global" -> feedPreferences.autoDeleteAction = AutoDeleteAction.GLOBAL
                        "always" -> feedPreferences.autoDeleteAction = AutoDeleteAction.YES
                        "never" -> feedPreferences.autoDeleteAction = AutoDeleteAction.NO
                    }
                    DBWriter.setFeedPreferences(feedPreferences)
                    updateAutoDeleteSummary()
                    false
                }
        }

        private fun updateAutoDeleteSummary() {
            val feedPreferences = this.feedPreferences ?: return
            val autoDeletePreference = requirePreference<ListPreference>(PREF_AUTO_DELETE)
            when (feedPreferences.autoDeleteAction) {
                AutoDeleteAction.GLOBAL -> {
                    autoDeletePreference.setSummary(R.string.feed_auto_download_global)
                    autoDeletePreference.value = "global"
                }
                AutoDeleteAction.YES -> {
                    autoDeletePreference.setSummary(R.string.feed_auto_download_always)
                    autoDeletePreference.value = "always"
                }
                AutoDeleteAction.NO -> {
                    autoDeletePreference.setSummary(R.string.feed_auto_download_never)
                    autoDeletePreference.value = "never"
                }
            }
        }

        private fun setupVolumeReductionPreferences() {
            val feedPreferences = this.feedPreferences ?: return
            val feed = this.feed ?: return
            val volumeReductionPreference = requirePreference<ListPreference>(PREF_VOLUME_REDUCTION)
            volumeReductionPreference.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
                    when (newValue as String?) {
                        "off" -> feedPreferences.volumeAdaptionSetting = VolumeAdaptionSetting.OFF
                        "light" -> feedPreferences.volumeAdaptionSetting =
                            VolumeAdaptionSetting.LIGHT_REDUCTION
                        "heavy" -> feedPreferences.volumeAdaptionSetting =
                            VolumeAdaptionSetting.HEAVY_REDUCTION
                    }
                    DBWriter.setFeedPreferences(feedPreferences)
                    updateVolumeReductionValue()
                    EventBus.getDefault().post(
                        VolumeAdaptionChangedEvent(
                            feedPreferences.volumeAdaptionSetting,
                            feed.id
                        )
                    )
                    false
                }
        }

        private fun updateVolumeReductionValue() {
            val feedPreferences = this.feedPreferences ?: return
            val volumeReductionPreference = requirePreference<ListPreference>(PREF_VOLUME_REDUCTION)
            when (feedPreferences.volumeAdaptionSetting) {
                VolumeAdaptionSetting.OFF -> volumeReductionPreference.value = "off"
                VolumeAdaptionSetting.LIGHT_REDUCTION -> volumeReductionPreference.value = "light"
                VolumeAdaptionSetting.HEAVY_REDUCTION -> volumeReductionPreference.value = "heavy"
            }
        }

        private fun setupKeepUpdatedPreference() {
            val feedPreferences = this.feedPreferences ?: return
            val pref = requirePreference<ATESwitchPreference>(PREF_KEEP_UPDATED)
            pref.isChecked = feedPreferences.keepUpdated
            pref.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any ->
                    val checked = newValue === java.lang.Boolean.TRUE
                    feedPreferences.keepUpdated = checked
                    DBWriter.setFeedPreferences(feedPreferences)
                    pref.isChecked = checked
                    false
                }
        }

        private fun setupAudioEffectPreference(feedPreferences: FeedPreferences?, feed: Feed?) {
            requirePreference<Preference>(PREF_AUDIO_EFFECT).onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    getParentFragmentManager().beginTransaction()
                        .setCustomAnimations(
                            R.anim.retro_fragment_open_enter,
                            R.anim.retro_fragment_open_exit,
                            R.anim.retro_fragment_close_enter,
                            R.anim.retro_fragment_close_exit
                        )
                        .replace(
                            R.id.settings_fragment_container,
                            AudioEffectFragment(feedPreferences, feed)
                        )
                        .addToBackStack(getString(R.string.audio_effects)).commit()
                    true
                }
        }

        private fun setupAutoDownloadGlobalPreference() {
            if (!isEnableAutodownload) {
                val autodl = requirePreference<ATESwitchPreference>(PREF_AUTO_DOWNLOAD)
                autodl.isChecked = false
                autodl.isEnabled = false
                autodl.setSummary(R.string.auto_download_disabled_globally)
                requirePreference<Preference>(PREF_EPISODE_FILTER).isEnabled = false
            }
        }

        private fun setupAutoDownloadPreference() {
            val feedPreferences = this.feedPreferences ?: return
            val pref = requirePreference<ATESwitchPreference>(PREF_AUTO_DOWNLOAD)
            pref.isEnabled = isEnableAutodownload
            if (isEnableAutodownload) {
                pref.isChecked = feedPreferences.autoDownload
            } else {
                pref.isChecked = false
                pref.setSummary(R.string.auto_download_disabled_globally)
            }
            pref.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any ->
                    val checked = newValue === java.lang.Boolean.TRUE
                    feedPreferences.autoDownload = checked
                    DBWriter.setFeedPreferences(feedPreferences)
                    updateAutoDownloadEnabled()
                    pref.isChecked = checked
                    false
                }
        }

        private fun updateAutoDownloadEnabled() {
            val preferences = feed?.preferences ?: return
            val enabled = preferences.autoDownload && isEnableAutodownload
            requirePreference<Preference>(PREF_EPISODE_FILTER).isEnabled = enabled
        }

        private fun setupAudiobookPreference() {
            val feedPreferences = this.feedPreferences ?: return
            val pref = requirePreference<ATESwitchPreference>(PREF_AUDIOBOOK)
            pref.isChecked = feedPreferences.isAudiobook
            pref.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                    val checked = newValue === java.lang.Boolean.TRUE
                    // Keep the in-memory copy in step: the other switches here save the whole
                    // FeedPreferences object, which would otherwise write the old flag back.
                    feedPreferences.isAudiobook = checked
                    DBWriter.setFeedAudiobook(feedPreferences.feedID, checked)
                    pref.isChecked = checked
                    false
                }
        }

        private fun setupTags() {
            val feedPreferences = this.feedPreferences ?: return
            requirePreference<Preference>(PREF_TAGS).onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference ->
                    TagEditDialog.newInstance(listOf(feedPreferences))
                        .show(childFragmentManager, TagEditDialog.TAG)
                    true
                }
        }

        private fun setupEpisodeNotificationPreference() {
            val feedPreferences = this.feedPreferences ?: return
            val pref = requirePreference<ATESwitchPreference>(PREF_EPISODE_NOTIFICATION)
            pref.isChecked = feedPreferences.showEpisodeNotification
            pref.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any ->
                    val checked = newValue === java.lang.Boolean.TRUE
                    feedPreferences.showEpisodeNotification = checked
                    DBWriter.setFeedPreferences(feedPreferences)
                    pref.isChecked = checked
                    false
                }
        }

        override fun invalidateSettings() {}

        companion object {
            private val PREF_EPISODE_FILTER: CharSequence = "episodeFilter"
            private val PREF_SCREEN: CharSequence = "feedSettingsScreen"
            private val PREF_AUDIOBOOK: CharSequence = "audiobook"
            private val PREF_AUTHENTICATION: CharSequence = "authentication"
            private val PREF_AUTO_DELETE: CharSequence = "autoDelete"
            private val PREF_CATEGORY_AUTO_DOWNLOAD: CharSequence = "autoDownloadCategory"
            private const val PREF_FEED_PLAYBACK_SPEED = "feedPlaybackSpeed"
            private const val PREF_AUTO_SKIP = "feedAutoSkip"
            private const val PREF_AD_SKIP = "feedAdSkip"
            private const val AD_SKIP_DEFAULT = "default"
            private const val AD_SKIP_ON = "on"
            private const val AD_SKIP_OFF = "off"
            private const val PREF_VIDEO_MODE = "feedVideoMode"
            private const val VIDEO_MODE_DEFAULT = "default"
            private const val VIDEO_MODE_VIDEO = "video"
            private const val VIDEO_MODE_AUDIO = "audio"
            private const val PREF_AUDIO_EFFECT = "feed_audio_effect_pref"
            private const val PREF_TAGS = "tags"
            private const val PREF_VOLUME_REDUCTION = "volumeReduction"
            private const val PREF_KEEP_UPDATED = "keepUpdated"
            private const val PREF_AUTO_DOWNLOAD = "autoDownload"
            private const val PREF_EPISODE_NOTIFICATION = "episodeNotification"
            private const val PREF_RESET_DISMISSED_WARNINGS = "resetDismissedWarnings"
            fun newInstance(feedId: Long): FeedSettingsPreferenceFragment {
                val fragment = FeedSettingsPreferenceFragment()
                val arguments = Bundle()
                arguments.putLong(EXTRA_FEED_ID, feedId)
                fragment.arguments = arguments
                return fragment
            }
        }
    }

    companion object {
        private const val TAG = "FeedSettingsFragment"
        private const val EXTRA_FEED_ID = "dev.russell.focuspodcast.extra.feedId"
        fun newInstance(feed: Feed): FeedSettingsFragment {
            val fragment = FeedSettingsFragment()
            val arguments = Bundle()
            arguments.putLong(EXTRA_FEED_ID, feed.id)
            fragment.arguments = arguments
            return fragment
        }
    }
}