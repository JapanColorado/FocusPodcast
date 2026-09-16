package allen.town.focus_common.views

import android.content.Context
import android.util.AttributeSet
import code.name.monkey.appthemehelper.util.EditTextUtil
import com.google.android.material.textfield.TextInputEditText

class CursorAccentTextInputEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextInputEditText(context, attrs) {
    init {
        EditTextUtil.setCursorDrawable(this)
    }
}