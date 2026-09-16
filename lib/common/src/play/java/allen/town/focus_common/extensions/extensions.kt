@file:JvmName("ExtensionsUtils")

package allen.town.focus_common.extensions

import allen.town.focus_common.activity.ClearAllActivityInterface
import allen.town.focus_common.activity.ToolbarBaseActivity
import allen.town.focus_common.util.TopSnackbarUtil
import android.content.Context
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import java.util.*

/**
 * 如果没有继承ToolbarBaseActivity，那么就把ClearAllActivityInterface传过来
 */
fun FragmentActivity.installLanguageAndRecreate(
    code: String,
    clearAllActivityInterface: ClearAllActivityInterface? = null
) {
    var mySessionId = 0

    val manager = SplitInstallManagerFactory.create(this)
    val listener = object : SplitInstallStateUpdatedListener {
        override fun onStateUpdate(state: SplitInstallSessionState) {
            if (state.sessionId() == mySessionId) {
                (this@installLanguageAndRecreate as? ToolbarBaseActivity)?.clearAllAppcompactActivities(
                    true
                )
                clearAllActivityInterface?.clearAllAppcompactActivities(
                    true
                )
                manager.unregisterListener(this)
            }
        }
    }
    manager.registerListener(listener)

    if (code != "auto") {
        // Try to download language resources
        val request =
            SplitInstallRequest.newBuilder().addLanguage(Locale.forLanguageTag(code))
                .build()
        manager.startInstall(request)
            // Recreate the activity on download complete
            .addOnSuccessListener {
                mySessionId = it
            }
            .addOnFailureListener {
                TopSnackbarUtil.showSnack(this, "Language download failed.", Toast.LENGTH_LONG)
            }
    } else {
        (this@installLanguageAndRecreate as? ToolbarBaseActivity)?.clearAllAppcompactActivities(true)
        clearAllActivityInterface?.clearAllAppcompactActivities(
            true
        )
    }
}


fun Context.installSplitCompat() {
    SplitCompat.install(this)
}