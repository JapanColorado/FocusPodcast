package dev.russell.focuspodcast.common.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import dev.russell.focuspodcast.common.extensions.accentColor

class AccentTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {
    init {
        accentColor()
    }
}