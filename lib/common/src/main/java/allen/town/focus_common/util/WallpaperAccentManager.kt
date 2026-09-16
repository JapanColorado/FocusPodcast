package allen.town.focus_common.util

import android.app.WallpaperManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import code.name.monkey.appthemehelper.ThemeStore
import code.name.monkey.appthemehelper.util.VersionUtils

class WallpaperAccentManager(val context: Context) {

    private val mOnColorsChangedListener by lazy {
        WallpaperManager.OnColorsChangedListener { _, _ ->
            updateColors()
        }
    }

    private var onColorsChangedListener: WallpaperManager.OnColorsChangedListener? = null
    fun init(onColorsChangedListener: WallpaperManager.OnColorsChangedListener? = null) {
        if (VersionUtils.hasOreoMR1()) {
            this.onColorsChangedListener = onColorsChangedListener

            with(WallpaperManager.getInstance(context)) {
                updateColors()
                if (BasePreferenceUtil.wallpaperAccent) {
                    addOnColorsChangedListener(
                        onColorsChangedListener ?: mOnColorsChangedListener,
                        Handler(Looper.getMainLooper())
                    )
                }
            }
        }
    }

    fun release() {
        if (VersionUtils.hasOreoMR1()) {
            WallpaperManager.getInstance(context)
                .removeOnColorsChangedListener(onColorsChangedListener ?: mOnColorsChangedListener)
        }
    }

    private fun updateColors() {
        if (VersionUtils.hasOreoMR1()) {
            val colors = WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            if (colors != null) {
                val primaryColor = colors.primaryColor.toArgb()
                    ThemeStore.editTheme(context).wallpaperColor(context, primaryColor).commit()
            }
        }
    }
}