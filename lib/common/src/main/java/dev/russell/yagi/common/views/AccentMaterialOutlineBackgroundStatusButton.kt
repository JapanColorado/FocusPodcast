package dev.russell.yagi.common.views

import android.content.Context
import android.util.AttributeSet
import dev.russell.yagi.common.extensions.accentOutlineBackgroundColor
import com.google.android.material.button.MaterialButton

class AccentMaterialOutlineBackgroundStatusButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : MaterialButton(context, attrs) {
    init {
        accentOutlineBackgroundColor()
    }
}