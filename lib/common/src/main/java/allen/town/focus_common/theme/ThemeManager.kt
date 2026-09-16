package code.name.monkey.retromusic.util.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import code.name.monkey.retromusic.extensions.generalThemeValue
import code.name.monkey.retromusic.util.theme.ThemeMode.*

object ThemeManager {

    //重要，这里必须要用application的context!!! 否则使用md3主题无法跟随系统自动切换主题
    fun getNightMode(context: Context): Int = when (context.generalThemeValue) {
        LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        DARK -> AppCompatDelegate.MODE_NIGHT_YES
        BLACK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}