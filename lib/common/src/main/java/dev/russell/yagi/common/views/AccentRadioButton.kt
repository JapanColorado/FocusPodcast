package dev.russell.yagi.common.views

import dev.russell.yagi.common.util.BasePreferenceUtil
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatRadioButton
import dev.russell.yagi.theme.ATH
import dev.russell.yagi.theme.ThemeStore

class AccentRadioButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatRadioButton(context, attrs) {
    init {
        if(!BasePreferenceUtil.materialYou){
            ATH.setTint(this,ThemeStore.accentColor(context))
        }
    }
}