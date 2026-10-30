package dev.russell.yagi.common.views

import android.content.Context
import android.util.AttributeSet
import dev.russell.yagi.common.extensions.elevatedAccentColor
import com.google.android.material.button.MaterialButton

/**
 * A tonal button: the accent colour blended into the surface as its fill, the accent as its icon
 * tint. Meant for `Widget.Material3.Button.TonalButton(.Icon)`; with Material You the style's own
 * secondary-container colours apply unchanged.
 */
class AccentTonalButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : MaterialButton(context, attrs) {
    init {
        elevatedAccentColor()
    }
}
