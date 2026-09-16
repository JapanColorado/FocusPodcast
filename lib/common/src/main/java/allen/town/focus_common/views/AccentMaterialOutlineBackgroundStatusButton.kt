package allen.town.focus_common.views

import android.content.Context
import android.util.AttributeSet
import allen.town.focus_common.extensions.accentOutlineBackgroundColor
import com.google.android.material.button.MaterialButton

class AccentMaterialOutlineBackgroundStatusButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : MaterialButton(context, attrs) {
    init {
        accentOutlineBackgroundColor()
    }
}