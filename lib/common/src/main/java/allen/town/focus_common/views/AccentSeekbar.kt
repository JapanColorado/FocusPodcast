package allen.town.focus_common.views

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar
import allen.town.focus_common.extensions.addAccentColor

class AccentSeekbar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SeekBar(context, attrs) {
    init {
        addAccentColor()
    }
}