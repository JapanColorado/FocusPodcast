package allen.town.podcast.common.views

import android.content.Context
import android.util.AttributeSet
import allen.town.podcast.theme.util.EditTextUtil
import com.google.android.material.textfield.TextInputEditText

class CursorAccentTextInputEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextInputEditText(context, attrs) {
    init {
        EditTextUtil.setCursorDrawable(this)
    }
}