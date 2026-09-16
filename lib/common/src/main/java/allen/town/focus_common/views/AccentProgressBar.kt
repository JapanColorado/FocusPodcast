package allen.town.focus_common.views

import allen.town.focus_common.util.BasePreferenceUtil
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.widget.ProgressBar
import code.name.monkey.appthemehelper.ThemeStore

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