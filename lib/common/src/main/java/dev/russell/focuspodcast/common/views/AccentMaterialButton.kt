package dev.russell.focuspodcast.common.views

import android.content.Context
import android.util.AttributeSet
import dev.russell.focuspodcast.common.extensions.accentColor
import com.google.android.material.button.MaterialButton

class AccentMaterialButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : MaterialButton(context, attrs) {
    init {
        accentColor()
    }
}