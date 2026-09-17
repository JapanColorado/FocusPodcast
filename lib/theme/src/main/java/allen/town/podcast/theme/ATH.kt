package allen.town.podcast.theme

import android.content.Context
import android.view.View
import androidx.annotation.ColorInt
import allen.town.podcast.theme.util.TintHelper

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
object ATH {

    fun didThemeValuesChange(context: Context, since: Long): Boolean {
        return ThemeStore.isConfigured(context) && ThemeStore.prefs(context).getLong(
            ThemeStorePrefKeys.VALUES_CHANGED,
            -1
        ) > since
    }

    @JvmStatic
    fun setTint(view: View, @ColorInt color: Int) {
        TintHelper.setTintAuto(view, color, false)
    }

    fun setBackgroundTint(view: View, @ColorInt color: Int) {
        TintHelper.setTintAuto(view, color, true)
    }
}