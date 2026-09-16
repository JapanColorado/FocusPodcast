package allen.town.focus_common.util

import allen.town.focus_common.R
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity


object Util {

    @JvmStatic
    fun dp2Px(context: Context, dp: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    @JvmStatic
    fun copyToClipboard(context: Context, str: String?) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(null, str))
        if (context is AppCompatActivity) {
            TopSnackbarUtil.showSnack(context, R.string.copied_to_clipboard)
        } else {
            Timber.i("copyToClipboard not notify")
        }
    }

    @JvmStatic
    fun px2Dp(context: Context, px: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (px / scale + 0.5f).toInt()
    }


    fun hasNavigation(context: Activity): Boolean {
        val decorViewHeight: Int = context.getWindow().getDecorView().getHeight()
        val dm = DisplayMetrics()
        context.getWindowManager().getDefaultDisplay().getMetrics(dm)
        val useableScreenHeight = dm.heightPixels
        val hasNavigation = decorViewHeight != useableScreenHeight
        return hasNavigation
    }


    fun m1210i(context: Context): Int {
        val defaultDisplay = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        return if (defaultDisplay.width < defaultDisplay.height) defaultDisplay.width else defaultDisplay.height
    }

    fun m1216c(i: Int): Int {
        return Math.round(i * Resources.getSystem().getDisplayMetrics().density).toInt()
    }

    /* renamed from: j */
    fun m1209j(context: Context): Int {
        return (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.width
    }


}