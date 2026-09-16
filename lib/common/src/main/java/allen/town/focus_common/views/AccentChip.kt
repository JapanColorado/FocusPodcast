package allen.town.focus_common.views

import android.content.Context
import android.util.AttributeSet
import allen.town.focus_common.extensions.accentBackgroundColor
import com.google.android.material.chip.Chip

class AccentChip @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : Chip(context, attrs) {
    init {
        accentBackgroundColor()
    }
}