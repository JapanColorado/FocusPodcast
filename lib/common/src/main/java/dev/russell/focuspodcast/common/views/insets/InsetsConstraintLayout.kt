package dev.russell.focuspodcast.common.views.insets

import dev.russell.focuspodcast.common.util.RetroUtil
import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import dev.russell.focuspodcast.common.extensions.drawAboveSystemBarsWithPadding

class InsetsConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    init {
        if (!RetroUtil.isLandscape(context))
            drawAboveSystemBarsWithPadding()
    }
}