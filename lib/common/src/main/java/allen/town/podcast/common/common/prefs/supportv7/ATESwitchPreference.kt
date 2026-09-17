package allen.town.podcast.common.common.prefs.supportv7

import allen.town.podcast.common.R
import android.content.Context
import android.util.AttributeSet
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.preference.CheckBoxPreference
import allen.town.podcast.theme.ThemeStore

/**
 * @author Aidan Follestad (afollestad)
 */
class ATESwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = -1,
    defStyleRes: Int = -1
) :
    CheckBoxPreference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        widgetLayoutResource = R.layout.ate_preference_switch_support
        icon?.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
            ThemeStore.accentColor(context), BlendModeCompat.SRC_IN
        )
    }
}