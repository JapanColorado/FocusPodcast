package dev.russell.yagi.common.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import dev.russell.yagi.theme.util.EditTextUtil

open class CursorAccentEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {
    init {
        EditTextUtil.setCursorDrawable(this)
    }
}