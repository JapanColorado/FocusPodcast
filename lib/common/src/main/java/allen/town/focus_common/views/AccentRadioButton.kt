package allen.town.focus_common.views

import allen.town.focus_common.util.BasePreferenceUtil
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatRadioButton
import code.name.monkey.appthemehelper.ATH
import code.name.monkey.appthemehelper.ThemeStore

class AccentRadioButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatRadioButton(context, attrs) {
    init {
        if(!BasePreferenceUtil.materialYou){
            ATH.setTint(this,ThemeStore.accentColor(context))
        }
    }
}