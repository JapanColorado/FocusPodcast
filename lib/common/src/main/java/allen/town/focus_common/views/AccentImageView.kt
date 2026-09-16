package allen.town.focus_common.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import allen.town.focus_common.extensions.applyAccentColor

class AccentImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    init {
        applyAccentColor()
    }
}