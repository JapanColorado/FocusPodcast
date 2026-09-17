package allen.town.podcast.common.views

import allen.town.podcast.common.util.BasePreferenceUtil
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatRadioButton
import allen.town.podcast.theme.ATH
import allen.town.podcast.theme.ThemeStore

class AccentRadioButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatRadioButton(context, attrs) {
    init {
        if(!BasePreferenceUtil.materialYou){
            ATH.setTint(this,ThemeStore.accentColor(context))
        }
    }
}