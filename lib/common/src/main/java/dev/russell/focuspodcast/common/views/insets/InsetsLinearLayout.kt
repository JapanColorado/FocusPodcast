package dev.russell.focuspodcast.common.views.insets

import dev.russell.focuspodcast.common.util.RetroUtil
import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import dev.russell.focuspodcast.common.extensions.drawAboveSystemBarsWithPadding

class InsetsLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    init {
        if (!RetroUtil.isLandscape(context))
            drawAboveSystemBarsWithPadding()
    }
}