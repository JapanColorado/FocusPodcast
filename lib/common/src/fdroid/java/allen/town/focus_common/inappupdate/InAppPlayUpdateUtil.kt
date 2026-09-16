package allen.town.focus_common.inappupdate

import android.app.Activity
object InAppPlayUpdateUtil {

    const val UPDATE_IN_APP_REQUEST_CODE = 1


    //FLEXIBLE(灵活更新，不会打断用户与app的交互)
    @JvmStatic
    fun checkGooglePlayInAppUpdate(activity: Activity, versionCode: Int) {
    }
}