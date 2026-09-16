package code.name.monkey.retromusic.views.insets

import allen.town.focus_common.util.RetroUtil
import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import allen.town.focus_common.extensions.drawAboveSystemBarsWithPadding

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