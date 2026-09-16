package allen.town.focus_common.util

import allen.town.focus_common.util.Util.dp2Px
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
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
            context?.run { showToast(this, getString(resId), duration) }
        }
    }

    @JvmStatic
    fun showSnack(context: Context?, str: String?, duration: Int = Toast.LENGTH_SHORT) {
        val activity = context as? FragmentActivity
        if (activity == null) {
            context?.run { showToast(this, str, duration) }
        }
        activity?.run {
            val decorView = window.decorView
            // Deferred by one frame so the content view is laid out before TSnackbar measures
            // it. The callback is cancelled again as soon as the decor view leaves the window,
            // so it can never outlive the activity it captures.
            postUntilDetached(decorView) {
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
                    // The top margin is roughly the navigation bar height; setting paddingTop has no effect
                    marginLayoutParams?.topMargin = dp2Px(this, 18f)
                    textView.textSize = 16f
                    textView.setTextColor(Color.WHITE)
                    snackbar.show()

                } catch (e: Exception) {
                    // The activity has no usable content view (or TSnackbar could not inflate
                    // into it). A Toast still gets the message across, so fall back to one.
                    Timber.w(e, "show toast instead")
                    showToast(activity, str, duration)
                }


            }
        }
    }

    /**
     * Posts [action] on [view] and cancels it again as soon as the view leaves the window, so a
     * deferred snackbar cannot keep its activity alive past teardown.
     */
    private fun postUntilDetached(view: View, action: () -> Unit) {
        val callback = Runnable { action() }
        view.post(callback)
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                // Nothing to do: the callback is posted once, before the view can be re-attached.
            }

            override fun onViewDetachedFromWindow(v: View) {
                v.removeCallbacks(callback)
                v.removeOnAttachStateChangeListener(this)
            }
        })
    }

    /**
     * Toast.makeText needs a Looper, so a call from a worker thread would throw. Hop to the main
     * thread when we are not already on it.
     */
    private fun showToast(context: Context, text: String?, duration: Int) {
        val length = if (duration == Toast.LENGTH_SHORT) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(context, text, length).show()
        } else {
            val appContext = context.applicationContext
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, text, length).show()
            }
        }
    }
}
