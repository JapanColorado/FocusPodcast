package dev.russell.yagi.common.views

import android.content.Context
import android.util.AttributeSet
import dev.russell.yagi.common.extensions.elevatedAccentColor
import com.google.android.material.chip.Chip

class AccentElevatedChip @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : Chip(context, attrs) {
    init {
        elevatedAccentColor()
    }
}