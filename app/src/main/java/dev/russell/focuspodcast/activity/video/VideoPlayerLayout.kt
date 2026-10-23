package dev.russell.focuspodcast.activity.video

import android.app.Activity
import android.content.res.Configuration
import android.util.TypedValue
import android.view.View
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.databinding.VideoplayerActivityBinding
import kotlin.math.abs

/**
 * Arranges the video player for the current window and shows or hides its controls.
 *
 * Landscape (and picture-in-picture) is the layout file as written: the video fills the screen,
 * the controls overlay it and hide after a few seconds, and the system bars are hidden. Portrait
 * puts the video under the toolbar at its own aspect ratio, with the title and the controls below
 * it, always visible, and the system bars shown. Both are [ConstraintSet]s of the same views, so
 * switching never re-creates the video surface.
 */
internal class VideoPlayerLayout(
    private val activity: Activity,
    private val binding: VideoplayerActivityBinding
) {
    private val landscapeSet = ConstraintSet().apply { clone(binding.root) }

    /** Views that fade out with the controls in landscape. */
    private val controlViews: List<View> = listOf(
        binding.toolbar, binding.topScrim, binding.bottomScrim,
        binding.transportControls, binding.seekRow, binding.actionsRow
    )

    /** Views that keep clear of the system bars and display cutouts, with their padding from XML. */
    private val insetViews: Map<View, IntArray> = listOf(
        binding.toolbar, binding.infoContainer, binding.seekRow, binding.actionsRow
    ).associateWith { intArrayOf(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom) }

    private var videoAspectRatio = DEFAULT_ASPECT_RATIO
    private var inPictureInPicture = false
    private var systemInsets = Insets.NONE
    private var screenWidthPx = 0
    private var screenHeightPx = 0
    /** Height the portrait layout needs for everything but the video, once it has been laid out. */
    private var portraitControlsHeight = 0

    var isPortrait = false
        private set

    /** Whether the controls are showing. Always true in portrait. */
    var controlsShown = true
        private set

    init {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            // Ignoring visibility keeps the controls from jumping when the bars hide in landscape.
            systemInsets = Insets.max(
                insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()),
                insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            )
            applyInsets()
            WindowInsetsCompat.CONSUMED
        }
        // Portrait gives the video what the controls below it leave over; that is only known
        // after they are laid out (text size, insets), so the video is resized once it is.
        binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!isPortrait) {
                return@addOnLayoutChangeListener
            }
            val needed = listOf(binding.toolbar, binding.infoContainer, binding.seekRow,
                binding.transportControls, binding.actionsRow).sumOf { it.height } + dp(PORTRAIT_MARGINS_DP)
            if (abs(needed - portraitControlsHeight) > 1) {
                portraitControlsHeight = needed
                binding.root.post {
                    if (isPortrait) {
                        portraitSet().applyTo(binding.root)
                        renderControls(animate = false)
                    }
                }
            }
        }
    }

    /** Re-arranges everything for [configuration]; call on creation and on every change. */
    fun apply(configuration: Configuration, pictureInPicture: Boolean) {
        inPictureInPicture = pictureInPicture
        screenWidthPx = dp(configuration.screenWidthDp)
        screenHeightPx = dp(configuration.screenHeightDp)
        isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT && !pictureInPicture
        (if (isPortrait) portraitSet() else landscapeSet).applyTo(binding.root)
        binding.infoContainer.isVisible = isPortrait
        controlsShown = !pictureInPicture
        renderControls(animate = false)
        updateSystemBars()
        val fullscreenLabel = if (isPortrait) R.string.video_enter_fullscreen else R.string.video_exit_fullscreen
        binding.fullscreenButton.setImageResource(
            if (isPortrait) R.drawable.ic_fullscreen else R.drawable.ic_fullscreen_exit
        )
        binding.fullscreenButton.contentDescription = activity.getString(fullscreenLabel)
        applyInsets()
    }

    /** The video's width / height; the portrait layout sizes the video area to it. */
    fun setVideoAspectRatio(ratio: Float) {
        if (ratio <= 0f || ratio == videoAspectRatio) {
            return
        }
        videoAspectRatio = ratio
        if (isPortrait) {
            portraitSet().applyTo(binding.root)
            renderControls(animate = false)
        }
    }

    fun showControls() {
        if (controlsShown || inPictureInPicture) {
            return
        }
        controlsShown = true
        renderControls(animate = true)
    }

    /** Hides the controls; portrait keeps them, since they do not cover the video there. */
    fun hideControls(animate: Boolean = true) {
        if (!controlsShown || isPortrait) {
            return
        }
        controlsShown = false
        renderControls(animate)
    }

    fun toggleControls() {
        if (controlsShown) hideControls() else showControls()
    }

    /** Hides every control for picture-in-picture; [apply] brings them back. */
    fun hideAllForPictureInPicture() {
        controlsShown = false
        renderControls(animate = false)
    }

    private fun renderControls(animate: Boolean) {
        val shown = controlsShown || isPortrait
        for (view in controlViews) {
            // The scrims only darken a video the controls lie on, which portrait never does.
            val target = shown && !(isPortrait && (view === binding.topScrim || view === binding.bottomScrim))
            view.animate().cancel()
            if (!animate) {
                view.alpha = 1f
                view.visibility = if (target) View.VISIBLE else View.INVISIBLE
            } else if (target) {
                view.alpha = 0f
                view.visibility = View.VISIBLE
                view.animate().alpha(1f).setDuration(FADE_MS).start()
            } else {
                view.animate().alpha(0f).setDuration(FADE_MS)
                    .withEndAction { view.visibility = View.INVISIBLE; view.alpha = 1f }
                    .start()
            }
        }
    }

    private fun updateSystemBars() {
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (isPortrait) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun applyInsets() {
        val insets = if (inPictureInPicture) Insets.NONE else systemInsets
        for ((view, base) in insetViews) {
            view.updatePadding(left = base[0] + insets.left, right = base[2] + insets.right)
        }
        binding.toolbar.updatePadding(top = insetViews.getValue(binding.toolbar)[1] + insets.top)
        binding.actionsRow.updatePadding(bottom = insetViews.getValue(binding.actionsRow)[3] + insets.bottom)
    }

    /**
     * The landscape set with the video moved under the toolbar at its aspect ratio (no taller
     * than the controls below it leave room for, for tall videos) and the controls stacked below.
     */
    private fun portraitSet(): ConstraintSet = ConstraintSet().apply {
        clone(landscapeSet)
        val video = R.id.videoFrame
        clear(video, ConstraintSet.BOTTOM)
        connect(video, ConstraintSet.TOP, R.id.toolbar, ConstraintSet.BOTTOM)
        val width = if (binding.root.width > 0) binding.root.width else screenWidthPx
        val height = if (binding.root.height > 0) binding.root.height else screenHeightPx
        val room = if (portraitControlsHeight > 0) height - portraitControlsHeight
            else (height * MAX_PORTRAIT_VIDEO_HEIGHT).toInt()
        constrainHeight(video, minOf(width / videoAspectRatio, room.toFloat()).toInt().coerceAtLeast(0))

        setVisibility(R.id.infoContainer, View.VISIBLE)
        setVisibility(R.id.topScrim, View.GONE)
        setVisibility(R.id.bottomScrim, View.GONE)

        clear(R.id.seekRow, ConstraintSet.BOTTOM)
        connect(R.id.seekRow, ConstraintSet.TOP, R.id.infoContainer, ConstraintSet.BOTTOM, dp(12))

        clear(R.id.transportControls, ConstraintSet.TOP)
        clear(R.id.transportControls, ConstraintSet.BOTTOM)
        connect(R.id.transportControls, ConstraintSet.TOP, R.id.seekRow, ConstraintSet.BOTTOM, dp(8))

        clear(R.id.actionsRow, ConstraintSet.BOTTOM)
        connect(R.id.actionsRow, ConstraintSet.TOP, R.id.transportControls, ConstraintSet.BOTTOM, dp(16))
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), activity.resources.displayMetrics
    ).toInt()

    private companion object {
        const val DEFAULT_ASPECT_RATIO = 16f / 9f
        /** Before the controls are measured: at most this share of the height for the video. */
        const val MAX_PORTRAIT_VIDEO_HEIGHT = 0.5f
        /** The gaps between the portrait rows (see [portraitSet]). */
        const val PORTRAIT_MARGINS_DP = 12 + 8 + 16
        const val FADE_MS = 200L
    }
}
