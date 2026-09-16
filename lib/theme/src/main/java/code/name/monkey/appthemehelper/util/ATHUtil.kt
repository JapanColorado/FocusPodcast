package code.name.monkey.appthemehelper.util

import android.content.Context
import android.graphics.Color
import androidx.annotation.AttrRes
import androidx.core.content.res.use
import java.lang.Exception

/**
 * @author Aidan Follestad (afollestad)
 */
object ATHUtil {

    /**
     * app背景色是否是暗色
     */
    @JvmStatic
    fun isWindowBackgroundDark(context: Context): Boolean {
        return !ColorUtil.isColorLight(resolveColor(context, android.R.attr.windowBackground))
    }

    @JvmStatic
    @JvmOverloads
    fun resolveColor(context: Context, @AttrRes attr: Int, fallback: Int = 0): Int {
        context.theme.obtainStyledAttributes(intArrayOf(attr)).use {
            return try {
                it.getColor(0, fallback);
            } catch (e: Exception) {
                Color.BLACK
            }
        }
    }
}