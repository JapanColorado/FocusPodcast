@file:Suppress("UNUSED_PARAMETER", "unused")
@file:JvmName("ExtensionsUtils")

package allen.town.focus_common.extensions

import android.content.Context
import androidx.fragment.app.FragmentActivity
import allen.town.focus_common.activity.ToolbarBaseActivity
import allen.town.focus_common.activity.ClearAllActivityInterface

fun FragmentActivity.installLanguageAndRecreate(code: String,
                                                clearAllActivityInterface: ClearAllActivityInterface? = null) {
    (this as? ToolbarBaseActivity)?.clearAllAppcompactActivities(true)
}


fun Context.installSplitCompat() {}