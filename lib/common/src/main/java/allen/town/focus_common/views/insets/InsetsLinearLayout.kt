package code.name.monkey.retromusic.views.insets

import allen.town.focus_common.util.RetroUtil
import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import allen.town.focus_common.extensions.drawAboveSystemBarsWithPadding

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