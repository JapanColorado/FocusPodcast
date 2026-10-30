package dev.russell.yagi.common.views

import dev.russell.yagi.common.util.BasePreferenceUtil
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.widget.ProgressBar
import dev.russell.yagi.theme.ThemeStore

class AccentProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ProgressBar(context, attrs) {
    init {
        if (!isInEditMode && !BasePreferenceUtil.materialYou) {
            getIndeterminateDrawable().setColorFilter(
                PorterDuffColorFilter(ThemeStore.accentColor(context), PorterDuff.Mode.SRC_IN)
            )
        }

    }
}