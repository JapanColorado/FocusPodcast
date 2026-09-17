package allen.town.podcast.common.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import allen.town.podcast.common.extensions.addAccentColor

class AccentSeekbar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatSeekBar(context, attrs) {
    init {
        addAccentColor()
    }
}