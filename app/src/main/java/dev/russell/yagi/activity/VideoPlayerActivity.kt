package dev.russell.yagi.activity

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.provider.Settings
import android.view.Gravity
import android.view.OrientationEventListener
import android.view.KeyEvent
import android.view.MenuItem
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import dev.russell.yagi.R
import dev.russell.yagi.activity.MainActivity.Companion.getIntentToOpenFeedWithId
import dev.russell.yagi.activity.video.SeekBarMarkers
import dev.russell.yagi.activity.video.VideoGestures
import dev.russell.yagi.activity.video.VideoPlayerOpenedEvent
import dev.russell.yagi.activity.video.VideoPlayerLayout
import dev.russell.yagi.common.activity.ToolbarBaseActivity
import dev.russell.yagi.common.util.Timber
import dev.russell.yagi.core.feed.util.ImageResourceUtils
import dev.russell.yagi.core.feed.util.VideoModeUtils
import dev.russell.yagi.core.pref.Prefs
import dev.russell.yagi.core.service.playback.PlaybackService
import dev.russell.yagi.core.storage.DBReader
import dev.russell.yagi.core.storage.DBWriter
import dev.russell.yagi.core.util.ChapterUtils
import dev.russell.yagi.core.util.Converter
import dev.russell.yagi.core.util.FeedItemUtil
import dev.russell.yagi.core.util.IntentUtils.openInBrowser
import dev.russell.yagi.core.util.ShareUtils
import dev.russell.yagi.core.util.StorageUtils
import dev.russell.yagi.core.util.TimeSpeedConverter
import dev.russell.yagi.core.util.playback.PlaybackController
import dev.russell.yagi.core.util.playback.PlaybackServiceStarter
import dev.russell.yagi.core.util.ui.PictureInPictureUtil
import dev.russell.yagi.databinding.VideoplayerActivityBinding
import dev.russell.yagi.dialog.BookmarkNoteDialog
import dev.russell.yagi.dialog.PlaySpeedDialog
import dev.russell.yagi.dialog.PlaybackControlsDialog
import dev.russell.yagi.dialog.ShareDialog
import dev.russell.yagi.dialog.SkipPrefDialog
import dev.russell.yagi.dialog.SleepTimerDialog
import dev.russell.yagi.event.BookmarksChangedEvent
import dev.russell.yagi.event.PlayerErrorEvent
import dev.russell.yagi.event.adskip.AdSegmentsChangedEvent
import dev.russell.yagi.event.adskip.AdSkipUndoEvent
import dev.russell.yagi.event.adskip.AdSkippedEvent
import dev.russell.yagi.event.playback.BufferUpdateEvent
import dev.russell.yagi.event.playback.PlaybackPositionEvent
import dev.russell.yagi.event.playback.PlaybackServiceEvent
import dev.russell.yagi.event.playback.SleepTimerMode
import dev.russell.yagi.event.playback.SleepTimerUpdatedEvent
import dev.russell.yagi.event.playback.SpeedChangedEvent
import dev.russell.yagi.event.playback.VideoFirstFrameEvent
import dev.russell.yagi.event.playback.VideoSizeChangedEvent
import dev.russell.yagi.event.settings.AdSkipChangedEvent
import dev.russell.yagi.fragment.FeedItemExtraInfoFragment
import dev.russell.yagi.model.feed.Bookmark
import dev.russell.yagi.model.feed.FeedItem
import dev.russell.yagi.model.feed.FeedMedia
import dev.russell.yagi.model.playback.Playable
import dev.russell.yagi.playback.base.PlayerStatus
import dev.russell.yagi.ui.startintent.MainActivityStarter
import dev.russell.yagi.ui.startintent.VideoPlayerActivityStarter
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Plays the video of an episode. Portrait shows the video above the title and controls;
 * landscape is full screen with controls that hide. Leaving it never pauses: Back, the headphones
 * button and a downward swipe switch the episode to audio only and open the player sheet, Home
 * goes to picture-in-picture, and with the screen off or the activity in the background the
 * episode keeps playing as audio. What survives is the per-episode choice in [VideoModeUtils].
 */
class VideoPlayerActivity : ToolbarBaseActivity(), OnSeekBarChangeListener, VideoGestures.Callbacks {
    private lateinit var binding: VideoplayerActivityBinding
    private lateinit var layout: VideoPlayerLayout
    private lateinit var markers: SeekBarMarkers
    private var controller: PlaybackController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val disposables = CompositeDisposable()

    private var videoSurfaceCreated = false
    /** Set once this activity hands over to the audio player, so nothing re-attaches the video. */
    private var leaving = false
    /** True while the full-screen button holds the screen in landscape; Back leaves it first. */
    private var forcedLandscape = false
    private var showTimeLeft = false
    private var isFavorite = false
    private var seeking = false
    private var seekFraction = 0f
    private var currentChapterIndex = NO_CHAPTER
    /** The episode on screen, kept for when the controller is already released. */
    private var shownMedia: Playable? = null
    /**
     * The episode this player was opened for, until the playback service has switched to it.
     * Before that, the episode the service reports is the previous one and says nothing about
     * whether this player should stay.
     */
    private var expectedMediaKey: String? = null
    private val instanceId = ++instanceCounter

    private var skipFeedbackForward: Boolean? = null
    private var skipFeedbackSeconds = 0

    private val hideControls = Runnable {
        if (controller?.status == PlayerStatus.PLAYING && !seeking) {
            layout.hideControls()
        }
    }

    private val fadeSkipFeedback = Runnable {
        for (view in listOf(binding.skipBackFeedback, binding.skipForwardFeedback, binding.skipFeedbackLabel)) {
            view.animate().alpha(0f).setDuration(SKIP_FEEDBACK_FADE_MS).start()
        }
        skipFeedbackForward = null
        skipFeedbackSeconds = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Yagi_VideoPlayer)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        StorageUtils.checkStorageAvailability(this)
        binding = VideoplayerActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        layout = VideoPlayerLayout(this, binding)
        markers = SeekBarMarkers(binding.sbPosition)
        expectedMediaKey = intent.getStringExtra(VideoPlayerActivityStarter.EXTRA_MEDIA_KEY)
        setupView()
        layout.apply(resources.configuration, isInPictureInPicture())
        onBackPressedDispatcher.addCallback(this) { onBack() }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        surfaceOwner = instanceId
        EventBus.getDefault().post(VideoPlayerOpenedEvent(instanceId))
        val playbackController = newPlaybackController()
        controller = playbackController
        playbackController.init()
        // Shows what is known before the service is bound; only the service decides to leave.
        loadMediaInfo(fromService = false)
    }

    override fun onResume() {
        super.onResume()
        StorageUtils.checkStorageAvailability(this)
        orientationListener.enable()
    }

    override fun onPause() {
        orientationListener.disable()
        super.onPause()
    }

    override fun onStop() {
        handler.removeCallbacks(hideControls)
        // Stopped (switched to audio, Home without picture-in-picture, screen off, or the
        // picture-in-picture window closed): the episode plays on as audio only.
        abandonVideoSurface()
        controller?.release()
        controller = null
        EventBus.getDefault().unregister(this)
        disposables.clear()
        markers.dispose()
        binding.progressBar.visibility = View.INVISIBLE
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        disposables.dispose()
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // From Android 12 the system enters picture-in-picture itself (setAutoEnterEnabled).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && controller?.status == PlayerStatus.PLAYING
            && !isInPictureInPicture()) {
            enterPictureInPicture()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        layout.apply(newConfig, isInPictureInPicture())
        renderTitles()
        scheduleHideControls()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
            // The picture-in-picture window was closed: carry on as audio only.
            shownMedia?.let { VideoModeUtils.setShowVideo(it, false) }
            leaving = true
            finish()
            return
        }
        layout.apply(newConfig, isInPictureInPictureMode)
        renderTitles()
    }

    private fun newPlaybackController(): PlaybackController = object : PlaybackController(this) {
        override fun onPositionObserverUpdate() {
            this@VideoPlayerActivity.onPositionObserverUpdate()
        }

        override fun onReloadNotification(code: Int) {
            if (code == PlaybackService.EXTRA_CODE_AUDIO && expectedMediaKey == null) {
                // The next episode in the queue is audio: it belongs in the player sheet.
                leaveToAudioPlayer()
            }
        }

        override fun updatePlayButtonShowsPlay(showPlay: Boolean) {
            binding.playButton.setIsShowPlay(showPlay)
            if (showPlay) {
                handler.removeCallbacks(hideControls)
                layout.showControls()
            } else {
                scheduleHideControls()
            }
            updatePictureInPictureParams()
        }

        override fun loadMediaInfo() {
            this@VideoPlayerActivity.loadMediaInfo(fromService = true)
        }

        override fun onAwaitingVideoSurface() {
            attachVideoSurface()
        }

        override fun onPlaybackEnd() {
            finish()
        }

        override fun setScreenOn(enable: Boolean) {
            if (enable) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun setupView() {
        showTimeLeft = Prefs.shouldShowRemainingTime()
        binding.toolbar.inflateMenu(R.menu.videoplayer)
        binding.toolbar.overflowIcon?.setTint(Color.WHITE)
        binding.toolbar.setOnMenuItemClickListener { onMenuItemClick(it) }
        binding.toolbar.setNavigationOnClickListener { switchToAudio() }

        binding.durationLabel.setOnClickListener {
            showTimeLeft = !showTimeLeft
            Prefs.setShowRemainTimeSetting(showTimeLeft)
            onPositionObserverUpdate()
        }
        binding.sbPosition.setOnSeekBarChangeListener(this)
        binding.rewindLabel.text = Prefs.rewindSecs.toString()
        binding.fastForwardLabel.text = Prefs.fastForwardSecs.toString()
        binding.rewindButton.setOnClickListener { onRewind() }
        binding.rewindButton.setOnLongClickListener {
            SkipPrefDialog.showSkipPreference(this, SkipPrefDialog.SkipDirection.SKIP_REWIND, null)
            true
        }
        binding.fastForwardButton.setOnClickListener { onFastForward() }
        binding.fastForwardButton.setOnLongClickListener {
            SkipPrefDialog.showSkipPreference(this, SkipPrefDialog.SkipDirection.SKIP_FORWARD, null)
            true
        }
        binding.playButton.setIsVideoScreen(true)
        binding.playButton.setOnClickListener { onPlayPause() }

        binding.speedButton.setOnClickListener {
            PlaySpeedDialog().show(supportFragmentManager, null)
            scheduleHideControls()
        }
        binding.sleepTimerButton.setOnClickListener {
            SleepTimerDialog().show(supportFragmentManager, "SleepTimerDialog")
            scheduleHideControls()
        }
        binding.bookmarkButton.setOnClickListener { addBookmark(false) }
        binding.bookmarkButton.setOnLongClickListener {
            addBookmark(true)
            true
        }
        binding.skipAdButton.setOnClickListener { controller?.skipCurrentAd() }
        binding.fullscreenButton.setOnClickListener { toggleFullscreen() }
        binding.pipButton.isVisible = PictureInPictureUtil.supportsPictureInPicture(this)
        binding.pipButton.setOnClickListener { enterPictureInPicture() }
        binding.errorRetryButton.setOnClickListener { restartPlayback(asVideo = true) }
        binding.errorAudioButton.setOnClickListener { restartPlayback(asVideo = false) }

        // Taps on the control rows must not reach the video below them and toggle the controls.
        binding.seekRow.isClickable = true
        binding.actionsRow.isClickable = true
        binding.videoView.holder.addCallback(surfaceHolderCallback)
        VideoGestures(binding.videoFrame, this).attach()
        updateSleepTimerButton(false, 0, null)
    }

    /**
     * [fromService] is true when the bound playback service reported the episode; only then may
     * an episode that is not shown as video send this player away.
     */
    private fun loadMediaInfo(fromService: Boolean) {
        val controller = this.controller ?: return
        val media = controller.media ?: return
        if (leaving) {
            return
        }
        val expected = expectedMediaKey
        if (expected != null) {
            if (media.identifier.toString() != expected) {
                return
            }
            if (fromService) {
                expectedMediaKey = null
            }
        }
        if (!controller.isShowingVideo) {
            // An audio episode, or one switched to audio only (for example from a notification
            // that still pointed here): the player sheet shows it.
            if (fromService) {
                leaveToAudioPlayer()
            }
            return
        }
        val mediaChanged = media.identifier != shownMedia?.identifier
        shownMedia = media
        if (mediaChanged) {
            loadPlaceholder(media)
        }
        attachVideoSurface()
        showTimeLeft = Prefs.shouldShowRemainingTime()
        if (mediaChanged) {
            currentChapterIndex = NO_CHAPTER
        }
        renderTitles()
        updateSpeedButton(controller.currentPlaybackSpeedMultiplier)
        // The timer events are not sticky, so the button learns the current state here.
        updateSleepTimerButton(controller.sleepTimerActive(), controller.sleepTimerTimeLeft,
            controller.sleepTimerMode)
        prepareMenu(media)
        checkFavorite(media)
        markers.load(media)
        loadChapters(media)
        applyVideoSize(controller.videoSize?.first ?: 0, controller.videoSize?.second ?: 0)
        updatePictureInPictureParams()
        onPositionObserverUpdate()
    }

    private fun loadChapters(media: Playable) {
        if (media.chapters != null) {
            return
        }
        disposables.add(Completable.fromAction { ChapterUtils.loadChapters(media, this) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                if (media === shownMedia) {
                    markers.showChapters(media)
                    currentChapterIndex = NO_CHAPTER
                    onPositionObserverUpdate()
                }
            }, { error -> Log.e(TAG, "could not load the chapters", error) }))
    }

    /**
     * Landscape names the episode in the toolbar; portrait leaves the toolbar empty and names it
     * under the video. Either way the podcast is followed by the chapter playing, if any.
     */
    private fun renderTitles() {
        val media = shownMedia ?: return
        val chapterTitle = media.chapters?.getOrNull(currentChapterIndex)?.title
        val subtitle = listOfNotNull(media.feedTitle, chapterTitle?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
        binding.episodeTitle.text = media.episodeTitle
        binding.episodeSubtitle.text = subtitle
        binding.toolbar.title = if (layout.isPortrait) null else media.episodeTitle
        binding.toolbar.subtitle = if (layout.isPortrait) null else subtitle
    }

    /** The episode's cover, shown while the video decoder starts (see [VideoFirstFrameEvent]). */
    private fun loadPlaceholder(media: Playable) {
        Glide.with(this)
            .load(ImageResourceUtils.getEpisodeListImageLocation(media))
            .error(Glide.with(this).load(ImageResourceUtils.getFallbackImageLocation(media)))
            .dontAnimate()
            .into(binding.videoPlaceholder)
    }

    private fun showPlaceholder() {
        binding.videoPlaceholder.animate().cancel()
        binding.videoPlaceholder.alpha = 1f
        binding.videoPlaceholder.visibility = View.VISIBLE
    }

    private fun attachVideoSurface() {
        val controller = this.controller ?: return
        if (videoSurfaceCreated && !leaving) {
            controller.setVideoSurface(binding.videoView.holder)
        }
    }

    private fun applyVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }
        binding.videoView.setVideoSize(width, height)
        layout.setVideoAspectRatio(width.toFloat() / height)
        updatePictureInPictureParams()
    }

    private val surfaceHolderCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

        override fun surfaceCreated(holder: SurfaceHolder) {
            videoSurfaceCreated = true
            // A new surface means the video track was off; it shows again with the next frame.
            showPlaceholder()
            attachVideoSurface()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            videoSurfaceCreated = false
            abandonVideoSurface()
        }
    }

    // ------------------------------------------------------------------ leaving the video

    private fun onBack() {
        if (forcedLandscape && !layout.isPortrait) {
            exitFullscreen()
        } else {
            switchToAudio()
        }
    }

    /** "Audio only": this episode goes on in the player sheet, without the picture. */
    private fun switchToAudio() {
        val controller = this.controller
        if (controller != null) {
            controller.setShowVideo(false)
        } else {
            shownMedia?.let { VideoModeUtils.setShowVideo(it, false) }
        }
        leaveToAudioPlayer()
    }

    /**
     * Hands the episode over to the player sheet. In picture-in-picture or in the background the
     * player only closes: opening the app there would jump over whatever the user is doing.
     */
    private fun leaveToAudioPlayer() {
        if (leaving) {
            return
        }
        leaving = true
        abandonVideoSurface()
        // Leaving for the player sheet must not count as "going home" for picture-in-picture.
        updatePictureInPictureParams()
        if (!isInPictureInPicture() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            MainActivityStarter(this).withOpenPlayer().start()
        }
        finish()
    }

    /** Detaches the video surface, unless a newer player instance owns it by now. */
    private fun abandonVideoSurface() {
        if (surfaceOwner == instanceId) {
            controller?.notifyVideoSurfaceAbandoned()
        }
    }

    /**
     * After the full-screen button forced an orientation, hand rotation back to the sensor once
     * the device is held that way, so turning it afterwards rotates the player again. With
     * auto-rotate off the forced orientation stays, as the button is the only way to rotate.
     */
    private val orientationListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(degrees: Int) {
                val forced = requestedOrientation
                if (degrees == ORIENTATION_UNKNOWN || forced == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    || !isAutoRotateOn()) {
                    return
                }
                val heldPortrait = degrees < ROTATION_SLACK || degrees > 360 - ROTATION_SLACK
                    || abs(degrees - 180) < ROTATION_SLACK
                val heldLandscape = abs(degrees - 90) < ROTATION_SLACK || abs(degrees - 270) < ROTATION_SLACK
                if ((forced == ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT && heldPortrait)
                    || (forced == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE && heldLandscape)) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    private fun isAutoRotateOn(): Boolean =
        Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private fun toggleFullscreen() {
        if (layout.isPortrait) {
            forcedLandscape = true
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            exitFullscreen()
        }
    }

    private fun exitFullscreen() {
        forcedLandscape = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
    }

    private fun isInPictureInPicture(): Boolean = PictureInPictureUtil.isInPictureInPictureMode(this)

    private fun enterPictureInPicture() {
        if (!PictureInPictureUtil.supportsPictureInPicture(this) || leaving) {
            return
        }
        layout.hideAllForPictureInPicture()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPictureInPictureMode(pictureInPictureParams())
            } else {
                @Suppress("DEPRECATION")
                enterPictureInPictureMode()
            }
        } catch (e: IllegalStateException) {
            // The system refused (for example while the activity is being stopped).
            Log.w(TAG, "could not enter picture-in-picture", e)
            layout.apply(resources.configuration, false)
        }
    }

    private fun updatePictureInPictureParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && PictureInPictureUtil.supportsPictureInPicture(this)) {
            setPictureInPictureParams(pictureInPictureParams())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pictureInPictureParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        val ratio = binding.videoView.videoAspectRatio
        if (ratio > 0f) {
            val clamped = ratio.coerceIn(MIN_PIP_RATIO, MAX_PIP_RATIO)
            builder.setAspectRatio(Rational((clamped * PIP_RATIO_SCALE).toInt(), PIP_RATIO_SCALE))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(controller?.status == PlayerStatus.PLAYING && !leaving)
        }
        return builder.build()
    }

    // ------------------------------------------------------------------ errors

    private fun showError(message: String?) {
        binding.errorMessage.text = message.orEmpty()
        binding.errorMessage.isVisible = !message.isNullOrBlank()
        binding.errorPanel.isVisible = true
        binding.progressBar.visibility = View.INVISIBLE
        layout.showControls()
    }

    /** Starts the episode again after an error, as video (Retry) or as audio only. */
    private fun restartPlayback(asVideo: Boolean) {
        val media = shownMedia ?: return
        binding.errorPanel.isVisible = false
        VideoModeUtils.setShowVideo(media, asVideo)
        PlaybackServiceStarter(this, media)
            .callEvenIfRunning(true)
            .start()
        if (!asVideo) {
            leaveToAudioPlayer()
        }
    }

    // ------------------------------------------------------------------ events

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMediaPlayerError(event: PlayerErrorEvent) {
        Timber.e("media player error " + event.message)
        showError(event.message)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackServiceChanged(event: PlaybackServiceEvent) {
        if (event.action == PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN && !binding.errorPanel.isVisible) {
            finish()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPositionEvent(event: PlaybackPositionEvent) {
        onPositionObserverUpdate()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun bufferUpdate(event: BufferUpdateEvent) {
        when {
            event.hasStarted() -> binding.progressBar.visibility = View.VISIBLE
            event.hasEnded() -> binding.progressBar.visibility = View.INVISIBLE
            else -> binding.sbPosition.secondaryProgress = (event.progress * binding.sbPosition.max).toInt()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onVideoSizeChanged(event: VideoSizeChangedEvent) {
        applyVideoSize(event.width, event.height)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onOtherPlayerOpened(event: VideoPlayerOpenedEvent) {
        if (event.instanceId != instanceId) {
            leaving = true
            finish()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onVideoFirstFrame(event: VideoFirstFrameEvent) {
        binding.videoPlaceholder.animate().alpha(0f).setDuration(PLACEHOLDER_FADE_MS)
            .withEndAction { binding.videoPlaceholder.visibility = View.INVISIBLE }
            .start()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSpeedChanged(event: SpeedChangedEvent) {
        updateSpeedButton(event.newSpeed)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun sleepTimerUpdate(event: SleepTimerUpdatedEvent) {
        updateSleepTimerButton(!event.isCancelled && !event.isOver, event.timeLeft, event.mode)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBookmarksChanged(event: BookmarksChangedEvent) {
        val media = shownMedia ?: return
        if (event.feedItemId == markers.bookmarksItemId) {
            markers.loadBookmarks(media)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAdSegmentsChanged(event: AdSegmentsChangedEvent) {
        val media = shownMedia ?: return
        if (event.feedItemId == markers.adSegmentsItemId) {
            markers.loadAdSegments(media)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAdSkipSettingChanged(event: AdSkipChangedEvent) {
        shownMedia?.let { markers.loadAdSegments(it) }
    }

    /** Tells the user what was skipped and offers to take it back, like the audio player. */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAdSkipped(event: AdSkippedEvent) {
        if (!Prefs.isAdSkipShowSnackbar) {
            return
        }
        val skipped = Converter.getDurationStringShort(event.skippedMs.toInt(), false)
        Snackbar.make(binding.root, getString(R.string.ad_skipped_snackbar, skipped), Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                EventBus.getDefault().post(AdSkipUndoEvent(event.feedItemId, event.segmentId, event.fromMs))
            }
            .show()
    }

    // ------------------------------------------------------------------ position and seeking

    fun onPositionObserverUpdate() {
        val controller = this.controller ?: return
        val position = controller.position
        val duration = controller.duration
        if (position == PlaybackService.INVALID_TIME || duration == PlaybackService.INVALID_TIME || duration <= 0) {
            return
        }
        val converter = TimeSpeedConverter(controller.currentPlaybackSpeedMultiplier)
        binding.positionLabel.text = Converter.getDurationStringLong(converter.convert(position))
        binding.durationLabel.text = if (showTimeLeft) {
            "-" + Converter.getDurationStringLong(converter.convert(duration - position))
        } else {
            Converter.getDurationStringLong(converter.convert(duration))
        }
        if (!seeking) {
            binding.sbPosition.progress = (position.toFloat() / duration * binding.sbPosition.max).toInt()
        }
        val chapterIndex = ChapterUtils.getCurrentChapterIndex(controller.media, position)
        if (chapterIndex != currentChapterIndex) {
            currentChapterIndex = chapterIndex
            renderTitles()
        }
        binding.skipAdButton.isVisible = controller.isInSkippableAdSegment
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        val controller = this.controller ?: return
        if (!fromUser) {
            return
        }
        seekFraction = progress / seekBar.max.toFloat()
        val target = (seekFraction * controller.duration).toInt()
        val converter = TimeSpeedConverter(controller.currentPlaybackSpeedMultiplier)
        val time = Converter.getDurationStringLong(converter.convert(target))
        val media = controller.media
        val chapter = media?.chapters?.getOrNull(ChapterUtils.getCurrentChapterIndex(media, target))?.title
        binding.seekPositionLabel.text = if (chapter.isNullOrBlank()) time else "$time · $chapter"
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        seeking = true
        handler.removeCallbacks(hideControls)
        binding.seekCardView.scaleX = .8f
        binding.seekCardView.scaleY = .8f
        binding.seekCardView.animate()
            .setInterpolator(FastOutSlowInInterpolator())
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(SEEK_CARD_ANIMATION_MS)
            .start()
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        seeking = false
        controller?.let { it.seekTo((seekFraction * it.duration).toInt()) }
        binding.seekCardView.animate()
            .setInterpolator(FastOutSlowInInterpolator())
            .alpha(0f).scaleX(.8f).scaleY(.8f)
            .setDuration(SEEK_CARD_ANIMATION_MS)
            .start()
        scheduleHideControls()
    }

    private fun onRewind() {
        val controller = this.controller ?: return
        controller.seekTo(controller.position - Prefs.rewindSecs * 1000)
        scheduleHideControls()
    }

    private fun onFastForward() {
        val controller = this.controller ?: return
        controller.seekTo(controller.position + Prefs.fastForwardSecs * 1000)
        scheduleHideControls()
    }

    private fun onPlayPause() {
        val controller = this.controller ?: return
        if (binding.errorPanel.isVisible) {
            return
        }
        controller.playPause()
        scheduleHideControls()
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, HIDE_CONTROLS_DELAY_MS)
    }

    // ------------------------------------------------------------------ gestures

    override fun onSingleTap() {
        if (isInPictureInPicture() || layout.isPortrait) {
            return
        }
        layout.toggleControls()
        if (layout.controlsShown) {
            scheduleHideControls()
        }
    }

    override fun onDoubleTap(forward: Boolean) {
        if (isInPictureInPicture()) {
            return
        }
        if (forward) onFastForward() else onRewind()
        showSkipFeedback(forward, if (forward) Prefs.fastForwardSecs else Prefs.rewindSecs)
    }

    override fun onSwipeDown() {
        if (!isInPictureInPicture()) {
            switchToAudio()
        }
    }

    /** The glow on the tapped side and "N seconds back/forward", adding up repeated taps. */
    private fun showSkipFeedback(forward: Boolean, seconds: Int) {
        if (skipFeedbackForward != forward) {
            skipFeedbackSeconds = 0
        }
        skipFeedbackForward = forward
        skipFeedbackSeconds += seconds
        val label = binding.skipFeedbackLabel
        label.text = getString(
            if (forward) R.string.video_skipped_forward else R.string.video_skipped_back, skipFeedbackSeconds
        )
        val icon = AppCompatResources.getDrawable(
            this, if (forward) R.drawable.ic_fast_forward_video_white else R.drawable.ic_fast_rewind_video_white
        )
        val iconSize = resources.getDimensionPixelSize(R.dimen.video_skip_feedback_icon)
        icon?.setBounds(0, 0, iconSize, iconSize)
        label.setCompoundDrawablesRelative(null, icon, null, null)
        val params = label.layoutParams as FrameLayout.LayoutParams
        params.gravity = Gravity.CENTER_VERTICAL or (if (forward) Gravity.END else Gravity.START)
        label.layoutParams = params
        val glow = if (forward) binding.skipForwardFeedback else binding.skipBackFeedback
        val other = if (forward) binding.skipBackFeedback else binding.skipForwardFeedback
        other.animate().cancel()
        other.alpha = 0f
        for (view in listOf(glow, label)) {
            view.animate().cancel()
            view.alpha = 1f
        }
        handler.removeCallbacks(fadeSkipFeedback)
        handler.postDelayed(fadeSkipFeedback, SKIP_FEEDBACK_SHOW_MS)
    }

    // ------------------------------------------------------------------ action row

    private fun updateSpeedButton(speed: Float) {
        val text = DecimalFormat("0.0#").format(speed) + "×"
        binding.speedButton.text = text
        binding.speedButton.contentDescription = getString(R.string.playback_speed) + " " + text
    }

    /** The moon alone while no timer runs; with a timer, the time left ("--:--" if unknown). */
    private fun updateSleepTimerButton(active: Boolean, timeLeftMs: Long, mode: SleepTimerMode?) {
        val unknown = timeLeftMs == SleepTimerUpdatedEvent.TIME_LEFT_UNKNOWN
                && mode != null && mode != SleepTimerMode.DURATION
        val button = binding.sleepTimerButton
        if (!active || (timeLeftMs <= 0 && !unknown)) {
            button.text = ""
            button.compoundDrawablePadding = 0
            button.contentDescription = getString(R.string.sleep_timer_label)
            return
        }
        val text = if (unknown) "--:--" else formatTimeLeft(timeLeftMs)
        button.text = text
        button.compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.video_chip_icon_padding)
        button.contentDescription = getString(R.string.sleep_timer_label) + " " + text
    }

    private fun addBookmark(withNote: Boolean) {
        val controller = this.controller ?: return
        val media = controller.media
        if (media !is FeedMedia || media.itemId <= 0) {
            Snackbar.make(binding.root, R.string.bookmark_unavailable, Snackbar.LENGTH_SHORT).show()
            return
        }
        val itemId = media.itemId
        val position = controller.position.coerceAtLeast(0).toLong()
        if (withNote) {
            BookmarkNoteDialog.show(this, R.string.bookmark_add, position, null) { note ->
                DBWriter.addBookmark(Bookmark(itemId, position, note, System.currentTimeMillis()))
                Snackbar.make(binding.root, R.string.bookmark_added, Snackbar.LENGTH_SHORT).show()
            }
            return
        }
        val bookmark = Bookmark(itemId, position, null, System.currentTimeMillis())
        DBWriter.addBookmark(bookmark)
        Snackbar.make(binding.root, R.string.bookmark_added, Snackbar.LENGTH_LONG)
            .setAction(R.string.bookmark_add_note) {
                BookmarkNoteDialog.show(this, R.string.bookmark_add_note, position, null) { note ->
                    DBWriter.setBookmarkNote(bookmark, note)
                }
            }
            .show()
        scheduleHideControls()
    }

    // ------------------------------------------------------------------ toolbar menu

    private fun prepareMenu(media: Playable) {
        val menu = binding.toolbar.menu
        val feedItem = getFeedItem(media)
        val hasWebsiteLink = getWebsiteLinkWithFallback(media) != null
        val hasItemLink = feedItem != null && ShareUtils.hasLinkToShare(feedItem)
        val hasDownloadLink = (media as? FeedMedia)?.download_url != null
        menu.findItem(R.id.open_feed_item).isVisible = feedItem != null
        menu.findItem(R.id.visit_website_item).isVisible = hasWebsiteLink
        menu.findItem(R.id.share_item).isVisible = hasWebsiteLink || hasItemLink || hasDownloadLink
        menu.findItem(R.id.add_to_favorites_item).isVisible = feedItem != null && !isFavorite
        menu.findItem(R.id.remove_from_favorites_item).isVisible = feedItem != null && isFavorite
    }

    private fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.audio_only_item) {
            switchToAudio()
            return true
        }
        val media = shownMedia ?: return false
        val feedItem = getFeedItem(media)
        when (item.itemId) {
            R.id.audio_controls -> PlaybackControlsDialog.newInstance(feedItem?.feedId ?: 0)
                .show(supportFragmentManager, "playback_controls")
            R.id.extra_info_item -> FeedItemExtraInfoFragment.newInstance(feedItem?.hasTranscript() == true)
                .show(supportFragmentManager, "FeedItemExtraInfoFragment")
            R.id.add_to_favorites_item -> feedItem?.let {
                DBWriter.addFavoriteItem(it)
                isFavorite = true
                prepareMenu(media)
            }
            R.id.remove_from_favorites_item -> feedItem?.let {
                DBWriter.removeFavoriteItem(it)
                isFavorite = false
                prepareMenu(media)
            }
            R.id.open_feed_item -> feedItem?.let { startActivity(getIntentToOpenFeedWithId(this, it.feedId)) }
            R.id.visit_website_item -> openInBrowser(this, getWebsiteLinkWithFallback(media))
            R.id.share_item -> feedItem?.let {
                ShareDialog.newInstance(it).show(supportFragmentManager, "ShareEpisodeDialog")
            }
            else -> return false
        }
        return true
    }

    private fun checkFavorite(media: Playable) {
        val feedItem = getFeedItem(media) ?: return
        disposables.add(Observable.fromCallable { DBReader.getFeedItem(feedItem.id) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ item: FeedItem? ->
                val favorite = item?.isTagged(FeedItem.TAG_FAVORITE) == true
                if (favorite != isFavorite) {
                    isFavorite = favorite
                    shownMedia?.let { prepareMenu(it) }
                }
            }, { error -> Log.e(TAG, "could not read the favorite state", error) }))
    }

    // ------------------------------------------------------------------ hardware keyboard

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (currentFocus is EditText) {
            return super.onKeyUp(keyCode, event)
        }
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        when (keyCode) {
            KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_SPACE -> {
                onPlayPause()
                layout.showControls()
                return true
            }
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_COMMA -> {
                onDoubleTap(forward = false)
                return true
            }
            KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_PERIOD -> {
                onDoubleTap(forward = true)
                return true
            }
            KeyEvent.KEYCODE_F -> {
                toggleFullscreen()
                return true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            KeyEvent.KEYCODE_I -> {
                enterPictureInPicture()
                return true
            }
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_W -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_S -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_M -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                return true
            }
        }
        // Go to x% of the video
        val controller = this.controller
        if (controller != null && keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            controller.seekTo((0.1f * (keyCode - KeyEvent.KEYCODE_0) * controller.duration).toInt())
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        private const val TAG = "VideoPlayerActivity"
        private const val NO_CHAPTER = -1
        private const val HIDE_CONTROLS_DELAY_MS = 3000L
        private const val SEEK_CARD_ANIMATION_MS = 200L
        private const val SKIP_FEEDBACK_SHOW_MS = 700L
        private const val SKIP_FEEDBACK_FADE_MS = 300L
        private const val PLACEHOLDER_FADE_MS = 150L
        /** How far from upright or sideways the device may be held to count as held that way. */
        private const val ROTATION_SLACK = 20

        /** Numbers each instance; the one started last owns the video surface. */
        private var instanceCounter = 0
        private var surfaceOwner = 0
        /** Android's picture-in-picture limits: between 1:2.39 and 2.39:1. */
        private const val MIN_PIP_RATIO = 1f / 2.39f
        private const val MAX_PIP_RATIO = 2.39f
        private const val PIP_RATIO_SCALE = 1000

        /** Remaining time as mm:ss, or h:mm:ss from an hour up, rounded up to the next second. */
        private fun formatTimeLeft(timeLeftMs: Long): String {
            val seconds = (timeLeftMs + 999) / 1000
            val hours = seconds / 3600
            val minutes = seconds % 3600 / 60
            val rest = seconds % 60
            return if (hours > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, rest)
            else String.format(Locale.getDefault(), "%02d:%02d", minutes, rest)
        }

        private fun getWebsiteLinkWithFallback(media: Playable?): String? = when {
            media == null -> null
            StringUtils.isNotBlank(media.websiteLink) -> media.websiteLink
            media is FeedMedia -> FeedItemUtil.getLinkWithFallback(media.item)
            else -> null
        }

        private fun getFeedItem(playable: Playable?): FeedItem? = (playable as? FeedMedia)?.item
    }
}
