package allen.town.podcast.common.views.insets

import allen.town.podcast.common.util.RetroUtil
import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import allen.town.podcast.common.extensions.drawAboveSystemBarsWithPadding

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