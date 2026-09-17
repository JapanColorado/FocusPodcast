package allen.town.podcast.common.views

import android.content.Context
import android.util.AttributeSet
import allen.town.podcast.common.extensions.accentColor
import com.google.android.material.textfield.TextInputLayout

class AccentTextInputLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextInputLayout(context, attrs) {
    init {
        accentColor()
    }
}