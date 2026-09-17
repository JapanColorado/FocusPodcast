package allen.town.podcast.common.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import allen.town.podcast.theme.util.EditTextUtil

open class CursorAccentEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {
    init {
        EditTextUtil.setCursorDrawable(this)
    }
}