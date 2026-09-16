package allen.town.focus_common.views

import android.content.Context
import android.util.AttributeSet
import allen.town.focus_common.extensions.accentColor
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class AccentExtendFloatingActionButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ExtendedFloatingActionButton(context, attrs) {
    init {
        accentColor()
    }
}