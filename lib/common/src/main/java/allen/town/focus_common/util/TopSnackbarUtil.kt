package allen.town.focus_common.util

import allen.town.focus_common.util.Util.dp2Px
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import code.name.monkey.appthemehelper.ThemeStore
import com.androidadvance.topsnackbar.TSnackbar

object TopSnackbarUtil {
    @JvmStatic
    fun showSnack(context: Context?, resId: Int, duration: Int = Toast.LENGTH_SHORT) {
        if (context is FragmentActivity) {
            showSnack(context, context.getString(resId), duration)
        } else {
            Timber.i("showSnack failed")
            context?.run {
                Toast.makeText(
                    context,
                    resId,
                    if (duration == Toast.LENGTH_SHORT) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                )
                    .show()
            }

        }
    }

    @JvmStatic
    fun showSnack(context: Context?, str: String?, duration: Int = Toast.LENGTH_SHORT) {
        val activity = context as? FragmentActivity
        if (activity == null) {
            context?.run {
                Toast.makeText(
                    context,
                    str,
                    if (duration == Toast.LENGTH_SHORT) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                )
                    .show()
            }

        }
        activity?.run {
            window.decorView.postDelayed({
                try {

                    val snackbar = TSnackbar.make(
                        findViewById<ViewGroup>(android.R.id.content).rootView, str as CharSequence,
                        if (duration == Toast.LENGTH_SHORT) TSnackbar.LENGTH_SHORT else
                            TSnackbar
                                .LENGTH_LONG
                    )
                    snackbar.setActionTextColor(Color.WHITE)
                    val snackbarView = snackbar.view
                    snackbarView.setBackgroundColor(ThemeStore.accentColor(context))
                    val textView =
                        snackbarView.findViewById(com.androidadvance.topsnackbar.R.id.snackbar_text) as TextView
                    val marginLayoutParams = (textView.layoutParams as? LinearLayout.LayoutParams)
                    //设置margintop的值大约是导航栏的值，设置paddingtop没效果
                    marginLayoutParams?.topMargin = dp2Px(this, 18f)
                    textView.textSize = 16f
                    textView.setTextColor(Color.WHITE)
                    snackbar.show()

                } catch (e: Exception) {
                    //如果遇到异常降级为Toast处理
                    Timber.i("show toast instead")
                    Toast.makeText(
                        activity,
                        str,
                        if (duration == Toast.LENGTH_SHORT) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                    )
                        .show()
                }


            }, 0)
        }

    }
}