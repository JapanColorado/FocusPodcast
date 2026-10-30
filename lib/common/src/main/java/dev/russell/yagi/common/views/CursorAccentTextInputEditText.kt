package dev.russell.yagi.common.views

import android.content.Context
import android.util.AttributeSet
import dev.russell.yagi.theme.util.EditTextUtil
import com.google.android.material.textfield.TextInputEditText

class CursorAccentTextInputEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextInputEditText(context, attrs) {
    init {
        EditTextUtil.setCursorDrawable(this)
    }
}