package dev.russell.focuspodcast.common.views

import dev.russell.focuspodcast.common.util.BasePreferenceUtil
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.widget.ProgressBar
import dev.russell.focuspodcast.theme.ThemeStore

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