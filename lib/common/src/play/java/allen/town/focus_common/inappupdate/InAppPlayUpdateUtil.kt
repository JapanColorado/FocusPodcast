package allen.town.focus_common.inappupdate

import allen.town.focus_common.util.Timber
import android.app.Activity
import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

object InAppPlayUpdateUtil {

    const val UPDATE_IN_APP_REQUEST_CODE = 1


    //FLEXIBLE(灵活更新，不会打断用户与app的交互)
    @JvmStatic
    fun checkGooglePlayInAppUpdate(activity: Activity, versionCode: Int) {
        val inAppLastUpdatePreference = InAppLastUpdatePreference(activity)
        val appUpdateManager = AppUpdateManagerFactory.create(activity)
        val installListener = object : InstallStateUpdatedListener {
            override fun onStateUpdate(installState: InstallState) {
                when (installState.installStatus()) {
                    InstallStatus.DOWNLOADING -> {
                        //下载中，可以在此获取进度

                        //已下载字节
                        val bytesDownloaded = installState.bytesDownloaded()
                        //需要下载的字节
                        val totalBytesToDownload = installState.totalBytesToDownload()
                        Timber.v("$bytesDownloaded / $totalBytesToDownload")
                    }
                    InstallStatus.DOWNLOADED -> {
                        //通知用户更新已下载完毕，重启以安装应用
                        appUpdateManager.unregisterListener(this)
                        popupSnackbarForCompleteUpdate(activity, appUpdateManager)
                    }
                    else -> {}
                }
            }
        }

        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            // If the update is downloaded but not installed,
            // notify the user to complete the update.
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                popupSnackbarForCompleteUpdate(activity, appUpdateManager)
                return@addOnSuccessListener
            }

            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            ) {
                Timber.d(
                    "there is a update"
                )

                if (appUpdateInfo.availableVersionCode() > versionCode
                ) {
                    if (!inAppLastUpdatePreference.lastVersionChecked(
                            appUpdateInfo.availableVersionCode()
                        )
                    ) {
                        Timber.d(
                            "need to update"
                        )
                        inAppLastUpdatePreference.versionCode = appUpdateInfo.availableVersionCode()

                        var daysBeforeUpdate = 1
                        val updatePriority = appUpdateInfo.updatePriority()
                        var updateType = AppUpdateType.FLEXIBLE

                        if (updatePriority < 1) {
                            daysBeforeUpdate = 3
                        } else if (updatePriority in 1..3) {
                            daysBeforeUpdate = 2
                        } else if (updatePriority in 4..5) {
                            updateType = AppUpdateType.IMMEDIATE
                            daysBeforeUpdate = 0
                        }
                        val lastNotiUpdateDays = (appUpdateInfo.clientVersionStalenessDays() ?: -1)
                        if (lastNotiUpdateDays >= daysBeforeUpdate) {
                            Timber.d("try to update")
                            appUpdateManager.registerListener(installListener)
                            appUpdateManager.startUpdateFlowForResult(
                                appUpdateInfo,
                                updateType,
                                activity,
                                UPDATE_IN_APP_REQUEST_CODE
                            )
                        } else {
                            Timber.d("lastNotiUpdateDays $lastNotiUpdateDays")
                        }
                    }
                }
            } else if (appUpdateInfo.updateAvailability()
                == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            ) {
                Timber.i("resume update")
                // If an in-app update is already running, resume the update.
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.IMMEDIATE,
                    activity,
                    UPDATE_IN_APP_REQUEST_CODE
                )
            } else {
                Timber.d("no update")
            }

        }
    }

    // Displays the snackbar notification and call to action.
    fun popupSnackbarForCompleteUpdate(activity: Activity, appUpdateManager: AppUpdateManager) {
        try {
            Timber.i("popupSnackbarForCompleteUpdate")
            Snackbar.make(
                activity.findViewById<ViewGroup>(android.R.id.content).getRootView(),
                "An update has just been downloaded.",
                Snackbar.LENGTH_INDEFINITE
            ).apply {
                setAction("RESTART") { appUpdateManager.completeUpdate() }
                show()
            }
        } catch (e: Exception) {
            appUpdateManager.completeUpdate()
        }
    }
}