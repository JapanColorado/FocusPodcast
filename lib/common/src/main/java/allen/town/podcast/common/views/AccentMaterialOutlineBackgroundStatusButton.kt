package allen.town.podcast.common.views

import android.content.Context
import android.util.AttributeSet
import allen.town.podcast.common.extensions.accentOutlineBackgroundColor
import com.google.android.material.button.MaterialButton

class AccentMaterialOutlineBackgroundStatusButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : MaterialButton(context, attrs) {
    init {
        accentOutlineBackgroundColor()
    }
}