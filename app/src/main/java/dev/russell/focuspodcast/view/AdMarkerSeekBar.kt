package dev.russell.focuspodcast.view

import dev.russell.focuspodcast.core.util.BookmarkUtils
import dev.russell.focuspodcast.model.feed.AdSegment
import dev.russell.focuspodcast.theme.ThemeStore
import dev.russell.focuspodcast.theme.util.ATHUtil
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.graphics.ColorUtils

/**
 * The player position bar with the ad segments and the bookmarks of the current episode painted
 * onto the track.
 *
 * Enabled segments get a solid translucent band, segments the user switched off get a fainter
 * band with a hairline outline so they are still findable without suggesting they will be
 * skipped. The colour comes from the theme (its error colour, falling back to the accent), never
 * from a literal, so it follows the app's palette and both light and dark themes.
 *
 * Bookmarks are thin ticks in the accent colour that stand out above and below the track, so they
 * read differently from the ad bands and stay visible over the played part of the bar.
 *
 * The view holds no subscriptions and does no loading: the player fragment hands it the segments
 * and bookmarks it has already loaded, and hands it an empty list when ad skipping is off.
 */
class AdMarkerSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.seekBarStyle
) : AppCompatSeekBar(context, attrs, defStyleAttr) {

    private var segments: List<AdSegment> = emptyList()
    private var durationMs: Long = 0

    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val band = RectF()

    private var bookmarkPositionsMs: LongArray = LongArray(0)
    private var bookmarkDurationMs: Long = 0
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ThemeStore.accentColor(context)
    }
    private val tick = RectF()

    /** Cached so onDraw does not resolve theme attributes on every frame. */
    private var bandColor: Int = resolveBandColor()

    /**
     * Replaces the painted segments. [durationMs] is the length of the episode the segments
     * belong to; a non-positive duration or an empty list simply paints nothing.
     */
    fun setAdSegments(segments: List<AdSegment>, durationMs: Long) {
        this.segments = segments
        this.durationMs = durationMs
        invalidate()
    }

    /**
     * Replaces the bookmark ticks. [durationMs] is the length of the episode they belong to; a
     * non-positive duration or an empty list paints none.
     */
    fun setBookmarks(positionsMs: List<Long>, durationMs: Long) {
        this.bookmarkPositionsMs = positionsMs.toLongArray()
        this.bookmarkDurationMs = durationMs
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // The track drawable spans the padded width, and AbsSeekBar adds the thumb offset back
        // into the thumb's travel, so the thumb reaches both of these edges too.
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val usableWidth = right - left
        if (usableWidth <= 0f) {
            return
        }
        val bandHeight = bandHeight()
        val centerY = paddingTop + (height - paddingTop - paddingBottom) / 2f
        drawAdSegments(canvas, left, usableWidth, centerY, bandHeight)
        drawBookmarkTicks(canvas, left, usableWidth, centerY, bandHeight)
    }

    private fun drawBookmarkTicks(canvas: Canvas, left: Float, usableWidth: Float, centerY: Float,
                                  bandHeight: Float) {
        if (bookmarkPositionsMs.isEmpty() || bookmarkDurationMs <= 0) {
            return
        }
        val tickWidth = dp(TICK_WIDTH_DP)
        val paddedHeight = (height - paddingTop - paddingBottom).toFloat()
        val tickHeight = minOf(bandHeight + dp(TICK_OVERHANG_DP) * 2, paddedHeight)
        val radius = tickWidth / 2f
        for (positionMs in bookmarkPositionsMs) {
            val fraction = BookmarkUtils.tickFraction(positionMs, bookmarkDurationMs)
            if (fraction < 0f) {
                continue
            }
            val x = left + fraction * usableWidth
            tick.set(x - tickWidth / 2f, centerY - tickHeight / 2f, x + tickWidth / 2f, centerY + tickHeight / 2f)
            canvas.drawRoundRect(tick, radius, radius, tickPaint)
        }
    }

    private fun drawAdSegments(canvas: Canvas, left: Float, usableWidth: Float, centerY: Float,
                               bandHeight: Float) {
        if (segments.isEmpty() || durationMs <= 0) {
            return
        }
        val radius = bandHeight / 2f

        for (segment in segments) {
            val startFraction = fractionOf(segment.startMs)
            val endFraction = fractionOf(segment.endMs)
            if (endFraction <= startFraction) {
                continue
            }
            band.set(
                left + startFraction * usableWidth,
                centerY - bandHeight / 2f,
                left + endFraction * usableWidth,
                centerY + bandHeight / 2f
            )
            if (segment.isEnabled) {
                bandPaint.color = ColorUtils.setAlphaComponent(bandColor, ALPHA_ENABLED)
                canvas.drawRoundRect(band, radius, radius, bandPaint)
            } else {
                bandPaint.color = ColorUtils.setAlphaComponent(bandColor, ALPHA_DISABLED)
                canvas.drawRoundRect(band, radius, radius, bandPaint)
                outlinePaint.color = ColorUtils.setAlphaComponent(bandColor, ALPHA_OUTLINE)
                canvas.drawRoundRect(band, radius, radius, outlinePaint)
            }
        }
    }

    private fun fractionOf(positionMs: Long): Float =
        (positionMs.coerceIn(0, durationMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    /**
     * The visual thickness of the track. The progress drawable's bounds cover the whole padded
     * height rather than the visible track, so its intrinsic height is used when it reports a
     * sane one and a plain 4dp band otherwise.
     */
    private fun bandHeight(): Float {
        val paddedHeight = (height - paddingTop - paddingBottom).toFloat()
        val intrinsic = progressDrawable?.intrinsicHeight ?: -1
        val preferred = if (intrinsic > 0) intrinsic.toFloat() else dp(DEFAULT_BAND_HEIGHT_DP)
        val maximum = if (paddedHeight > 0f) minOf(paddedHeight, dp(MAX_BAND_HEIGHT_DP))
            else dp(MAX_BAND_HEIGHT_DP)
        return preferred.coerceIn(dp(MIN_BAND_HEIGHT_DP), maximum)
    }

    private fun resolveBandColor(): Int {
        val accent = ThemeStore.accentColor(context)
        return ATHUtil.resolveColor(context, androidx.appcompat.R.attr.colorError, accent)
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    )

    private companion object {
        /** ~45 % of the theme colour for a segment that will actually be skipped. */
        const val ALPHA_ENABLED = 115

        /** ~20 % for a segment the user switched off. */
        const val ALPHA_DISABLED = 51

        /** The hairline around a switched-off segment, so it stays visible on a busy track. */
        const val ALPHA_OUTLINE = 153

        const val DEFAULT_BAND_HEIGHT_DP = 4f
        const val MIN_BAND_HEIGHT_DP = 3f
        const val MAX_BAND_HEIGHT_DP = 8f

        /** A bookmark tick: this wide, and standing this far out above and below the track. */
        const val TICK_WIDTH_DP = 2f
        const val TICK_OVERHANG_DP = 4f
    }
}
