package dev.russell.focuspodcast.common.views

import dev.russell.focuspodcast.common.util.BasePreferenceUtil
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatRadioButton
import dev.russell.focuspodcast.theme.ATH
import dev.russell.focuspodcast.theme.ThemeStore

class AccentRadioButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatRadioButton(context, attrs) {
    init {
        if(!BasePreferenceUtil.materialYou){
            ATH.setTint(this,ThemeStore.accentColor(context))
        }
    }
}