package allen.town.focus_common.views

import android.content.Context
import android.util.AttributeSet
import allen.town.focus_common.extensions.accent
import com.google.android.material.slider.Slider

class AccentAlphaSlider @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : Slider(context, attrs) {
    init {
        accent()
    }
}