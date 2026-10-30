package dev.russell.yagi.common.extensions

import dev.russell.yagi.common.util.RetroUtil
import dev.russell.yagi.common.util.BasePreferenceUtil
import android.content.Context
import androidx.core.view.WindowInsetsCompat

fun WindowInsetsCompat?.safeGetBottomInsets(context: Context): Int {
    return if (BasePreferenceUtil.isFullScreenMode) {
        return 0
    } else {
        this?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: RetroUtil.getNavigationBarHeight(context)
    }
}
