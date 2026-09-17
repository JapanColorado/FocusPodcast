@file:Suppress("UNUSED_PARAMETER", "unused")
@file:JvmName("ExtensionsUtils")

package allen.town.podcast.common.extensions

import androidx.fragment.app.FragmentActivity
import allen.town.podcast.common.activity.ToolbarBaseActivity
import allen.town.podcast.common.activity.ClearAllActivityInterface

fun FragmentActivity.installLanguageAndRecreate(code: String,
                                                clearAllActivityInterface: ClearAllActivityInterface? = null) {
    (this as? ToolbarBaseActivity)?.clearAllAppcompactActivities(true)
}

