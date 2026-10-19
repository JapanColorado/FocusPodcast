package dev.russell.focuspodcast.common.views

import android.content.Context
import android.util.AttributeSet
import dev.russell.focuspodcast.common.extensions.accentColor
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class AccentExtendFloatingActionButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ExtendedFloatingActionButton(context, attrs) {
    init {
        accentColor()
    }
}