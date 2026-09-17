package allen.town.podcast.common.views

import android.content.Context
import android.util.AttributeSet
import allen.town.podcast.common.extensions.accentColor
import com.google.android.material.floatingactionbutton.FloatingActionButton

class AccentFloatingActionButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FloatingActionButton(context, attrs) {
    init {
        accentColor()
    }
}