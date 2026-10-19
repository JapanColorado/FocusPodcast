package dev.russell.focuspodcast.common.extensions

import dev.russell.focuspodcast.common.util.RetroUtil
import dev.russell.focuspodcast.common.util.BasePreferenceUtil
import android.content.Context
import androidx.core.view.WindowInsetsCompat

fun WindowInsetsCompat?.safeGetBottomInsets(context: Context): Int {
    return if (BasePreferenceUtil.isFullScreenMode) {
        return 0
    } else {
        this?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: RetroUtil.getNavigationBarHeight(context)
    }
}
