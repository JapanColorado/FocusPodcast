package code.name.monkey.appthemehelper.util

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ProgressBar
import android.widget.SeekBar
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat

object ViewUtil {
    fun removeOnGlobalLayoutListener(v: View, listener: ViewTreeObserver.OnGlobalLayoutListener) {
        v.viewTreeObserver.removeOnGlobalLayoutListener(listener)
    }

    fun setBackgroundCompat(view: View, drawable: Drawable?) {
        view.background = drawable
    }

    fun setProgressDrawable(progressSlider: SeekBar, newColor: Int, thumbTint: Boolean = false) {

        if (thumbTint) {
            progressSlider.thumbTintList = ColorStateList.valueOf(newColor)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val layerDrawable = progressSlider.progressDrawable as LayerDrawable
            val progressDrawable = layerDrawable.findDrawableByLayerId(android.R.id.progress)
            progressDrawable.colorFilter =
                BlendModeColorFilterCompat.createBlendModeColorFilterCompat(newColor,
                    BlendModeCompat.SRC_IN
                )
        } else {
            progressSlider.progressTintList = ColorStateList.valueOf(newColor)
        }
    }


    fun setProgressDrawable(progressSlider: ProgressBar, newColor: Int) {

        val layerDrawable = progressSlider.progressDrawable as LayerDrawable

        val progress = layerDrawable.findDrawableByLayerId(android.R.id.progress)
        progress.colorFilter =
            BlendModeColorFilterCompat.createBlendModeColorFilterCompat(newColor,
                BlendModeCompat.SRC_IN
            )

        val background = layerDrawable.findDrawableByLayerId(android.R.id.background)
        val primaryColor =
            ATHUtil.resolveColor(progressSlider.context, android.R.attr.windowBackground)
        background.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
            MaterialValueHelper.getPrimaryDisabledTextColor(
                progressSlider.context,
                ColorUtil.isColorLight(primaryColor)
            ), BlendModeCompat.SRC_IN
        )

        val secondaryProgress = layerDrawable.findDrawableByLayerId(android.R.id.secondaryProgress)
        secondaryProgress?.colorFilter =
            BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                ColorUtil.withAlpha(
                    newColor,
                    0.65f
                ), BlendModeCompat.SRC_IN
            )
    }
}
