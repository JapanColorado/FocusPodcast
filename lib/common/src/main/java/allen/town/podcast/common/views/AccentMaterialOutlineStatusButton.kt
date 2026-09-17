package allen.town.podcast.common.views

import android.content.Context
import android.util.AttributeSet
import allen.town.podcast.common.extensions.accentOutlineColor
import com.google.android.material.button.MaterialButton

class AccentMaterialOutlineStatusButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : MaterialButton(context, attrs) {
    init {
        accentOutlineColor()
    }
}