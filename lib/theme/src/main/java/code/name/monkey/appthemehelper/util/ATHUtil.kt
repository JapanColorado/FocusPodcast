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
     * Whether the app background color is dark.
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
                // The attribute resolved to something that is not a color (a reference to a
                // state list, say). Black is the documented fallback for an unreadable theme
                // attribute; callers only need *a* color, so there is nothing to propagate.
                Color.BLACK
            }
        }
    }
}