package allen.town.podcast.common.views

import android.content.Context
import android.util.AttributeSet
import allen.town.podcast.common.extensions.accent
import com.google.android.material.slider.Slider

class AccentAlphaSlider @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : Slider(context, attrs) {
    init {
        accent()
    }
}