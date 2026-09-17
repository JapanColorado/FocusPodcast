package allen.town.podcast.common.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import allen.town.podcast.common.extensions.generalThemeValue
import allen.town.podcast.common.theme.ThemeMode.*

object ThemeManager {

    // Important: this must use the application context, otherwise the MD3 theme cannot follow the system theme automatically
    fun getNightMode(context: Context): Int = when (context.generalThemeValue) {
        LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        DARK -> AppCompatDelegate.MODE_NIGHT_YES
        BLACK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}