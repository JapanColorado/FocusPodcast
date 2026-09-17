package allen.town.podcast.common.views.insets

import allen.town.podcast.common.util.RetroUtil
import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import allen.town.podcast.common.extensions.drawAboveSystemBarsWithPadding

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