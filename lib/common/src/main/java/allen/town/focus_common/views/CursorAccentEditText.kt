package allen.town.focus_common.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import code.name.monkey.appthemehelper.util.EditTextUtil

open class CursorAccentEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {
    init {
        EditTextUtil.setCursorDrawable(this)
    }
}