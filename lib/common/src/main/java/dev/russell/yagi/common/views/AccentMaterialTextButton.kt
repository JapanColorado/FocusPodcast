package dev.russell.yagi.common.views

import android.content.Context
import android.util.AttributeSet
import dev.russell.yagi.common.extensions.accentTextColor
import com.google.android.material.button.MaterialButton

class AccentMaterialTextButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : MaterialButton(context, attrs) {
    init {
        accentTextColor(context)
    }
}