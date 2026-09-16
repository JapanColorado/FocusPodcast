package code.name.monkey.retromusic.util.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import code.name.monkey.retromusic.extensions.generalThemeValue
import code.name.monkey.retromusic.util.theme.ThemeMode.*

object ThemeManager {

    // Important: this must use the application context, otherwise the MD3 theme cannot follow the system theme automatically
    fun getNightMode(context: Context): Int = when (context.generalThemeValue) {
        LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        DARK -> AppCompatDelegate.MODE_NIGHT_YES
        BLACK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}