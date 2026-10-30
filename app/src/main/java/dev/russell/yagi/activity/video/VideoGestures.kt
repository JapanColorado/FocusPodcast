package dev.russell.yagi.activity.video

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Touch handling on the video: a single tap shows or hides the controls, a double tap on the left
 * or right half skips back or forward, and a downward swipe leaves the video for the audio player.
 */
internal class VideoGestures(
    private val view: View,
    private val callbacks: Callbacks
) : GestureDetector.SimpleOnGestureListener() {

    interface Callbacks {
        fun onSingleTap()
        fun onDoubleTap(forward: Boolean)
        fun onSwipeDown()
    }

    private val detector = GestureDetector(view.context, this)
    private val minFlingVelocity = ViewConfiguration.get(view.context).scaledMinimumFlingVelocity
    private val minSwipeDistance = view.resources.displayMetrics.density * MIN_SWIPE_DP

    // The detector does the click detection; performClick() is called for single taps below.
    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        view.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
        view.setOnClickListener { callbacks.onSingleTap() }
    }

    override fun onDown(e: MotionEvent): Boolean = true

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        view.performClick()
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        callbacks.onDoubleTap(forward = e.x > view.width / 2f)
        return true
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        val start = e1 ?: return false
        val dy = e2.y - start.y
        if (dy > minSwipeDistance && abs(dy) > abs(e2.x - start.x) * 2
            && velocityY > minFlingVelocity * FLING_VELOCITY_FACTOR) {
            callbacks.onSwipeDown()
            return true
        }
        return false
    }

    private companion object {
        const val MIN_SWIPE_DP = 96f
        const val FLING_VELOCITY_FACTOR = 4
    }
}
