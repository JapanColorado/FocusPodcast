package dev.russell.focuspodcast.common.views

import dev.russell.focuspodcast.common.R
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import dev.russell.focuspodcast.theme.ThemeStore
import dev.russell.focuspodcast.theme.util.ATHUtil.resolveColor
import dev.russell.focuspodcast.theme.util.ColorUtil
import dev.russell.focuspodcast.theme.util.ColorUtil.isColorLight

class SettingsImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    init {
        val orientation = GradientDrawable.Orientation.TL_BR
        val iArr = IntArray(2)
        iArr[0] = 0
        iArr[1] = calculateAccentSecondaryColor()
        val gradientDrawable = GradientDrawable(orientation, iArr)
        gradientDrawable.cornerRadius =
            resources.getDimensionPixelSize(R.dimen.settings_main_screen_button_icon_rounded_corners_radius)
                .toFloat()
        background = gradientDrawable
        val dimensionPixelSize: Int =
            resources.getDimensionPixelSize(R.dimen.settings_main_screen_button_icon_padding)
        setPadding(dimensionPixelSize, dimensionPixelSize, dimensionPixelSize, dimensionPixelSize)
    }

    fun calculateAccentSecondaryColor(): Int {
        var accentSecondaryColorCalculated: Int = 0
        val colorBg = resolveColor(context, android.R.attr.colorBackground)
        if (!isColorLight(colorBg)) {
            accentSecondaryColorCalculated =
                ColorUtil.adjustAlpha(ThemeStore.accentColor(context), 0.3f)
        } else {
            accentSecondaryColorCalculated =
                ColorUtil.adjustAlpha(ThemeStore.accentColor(context), 0.1f)
        }
        return accentSecondaryColorCalculated
    }
}