package allen.town.focus_common.util

import android.app.Activity
import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.Window

object StatusBarUtils {

    @JvmStatic
    fun setPaddingStatusBarTop(activity: Activity, resId: Int) {
        setPaddingStatusBarTop(activity, activity.findViewById(resId))
    }

    @JvmStatic
    fun setPaddingStatusBarTop(activity: Activity, view: View?) {
        val statusBarHeight: Int = getStatusBarHeight(activity.resources)
        if (view != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Timber.i("statusBarHeight %s", statusBarHeight)
                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop + statusBarHeight,
                    view.paddingRight,
                    view.paddingBottom
                )
            } else {
                view.post {

                    //pixel5 12第一种方式返回的高度好像不对，只能说这种方式可行
                    val rectangle = Rect()
                    val window: Window = activity.window
                    window.decorView.getWindowVisibleDisplayFrame(rectangle)
                    Timber.i("statusBarHeight2 %s", rectangle.top)
                    //如果计算getPaddingTop，二级页面已经是状态栏高度了，原因未知
                    view.setPadding(
                        view.paddingLeft, rectangle.top,
                        view.paddingRight,
                        view.paddingBottom
                    )
                }
            }

        }
    }

    @JvmStatic
    fun setMarginStatusBarTop(activity: Activity, view: View?) {
        val statusBarHeight: Int = getStatusBarHeight(activity.resources)
        if (view != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Timber.i("statusBarHeight %s", statusBarHeight)
                val toolParams = view.layoutParams as MarginLayoutParams
                toolParams.topMargin = statusBarHeight
                view.layoutParams = toolParams
            } else {
                view.post {
                    //pixel5 12第一种方式返回的高度好像不对，只能说这种方式可行
                    val rectangle = Rect()
                    val window: Window = activity.window
                    window.decorView.getWindowVisibleDisplayFrame(rectangle)
                    Timber.i("statusBarHeight2 %s", rectangle.top)
                    //如果计算getPaddingTop，二级页面已经是状态栏高度了，原因未知
                    val toolParams = view.layoutParams as MarginLayoutParams
                    toolParams.topMargin = rectangle.top
                    view.layoutParams = toolParams

                }
            }

        }
    }

    private fun getStatusBarHeight(r: Resources): Int {
        var result = 0
        val resourceId = r.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = r.getDimensionPixelSize(resourceId)
        }
        return result
    }

    @JvmStatic
    fun setHeight(activity: Activity, view: View?) {
        val statusBarHeight: Int = getStatusBarHeight(activity.resources)
        if (view != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Timber.i("statusBarHeight %s", statusBarHeight)
                val toolParams = view.layoutParams
                toolParams.height = statusBarHeight
//                view.layoutParams = toolParams
            } else {
                view.post {
                    //pixel5 12第一种方式返回的高度好像不对，只能说这种方式可行
                    val rectangle = Rect()
                    val window: Window = activity.window
                    window.decorView.getWindowVisibleDisplayFrame(rectangle)
                    Timber.i("statusBarHeight2 %s", rectangle.top)
                    //如果计算getPaddingTop，二级页面已经是状态栏高度了，原因未知
                    val toolParams = view.layoutParams
                    toolParams.height = rectangle.top
//                    view.layoutParams = toolParams

                }
            }

        }
    }
}