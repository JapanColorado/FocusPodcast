package allen.town.focus_common.util

import allen.town.focus_common.R
import allen.town.focus_common.views.AccentMaterialDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import code.name.monkey.appthemehelper.constants.ThemeConstants
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject
import java.util.Scanner

/**
 * 检查重要通知
 */
object NotifyUtils {
    @JvmStatic
    fun checkImportantNotify(context: Context, sharedPrefs: SharedPreferences,versionCode: Long) {
        Observable.just(0).subscribeOn(Schedulers.io())
            .map {
                val assetManager: AssetManager = context.assets
                val rawJSON = StringBuilder()
                try {
                    val `in`: Scanner = Scanner(assetManager.open("notify.json"))
                    while (`in`.hasNextLine()) {
                        rawJSON.append(`in`.nextLine()).append("\n")
                    }
                } catch (ignored: Exception) {
                    Timber.e(ignored, "init notify Json From File failed")
                }
                rawJSON.toString()
            }
            .observeOn(AndroidSchedulers.mainThread()).subscribe { result: String? ->
                try {
                    val notiJson = JSONObject(result)
                    val lastCheckVersion: Long =
                        sharedPrefs.getLong(ThemeConstants.LAST_CHECK_APP_VERSION, 0)
                    val notiVersion = notiJson.getLong("versionCode")
                    Timber.i("notiVersion: $notiVersion lastChekVersion: $lastCheckVersion")
                    if (notiVersion > lastCheckVersion && lastCheckVersion > 0) {
                        showNotiDialog(context, notiJson.getString("notify"))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "initApiJsonFromFile failed")
                }
                sharedPrefs.edit()
                    .putLong(ThemeConstants.LAST_CHECK_APP_VERSION, versionCode)
                    .commit()
            }
    }

    private fun showNotiDialog(context: Context, message: String) {
        AccentMaterialDialog(
            context,
            R.style.MaterialAlertDialogTheme
        )
            .setMessage(message) /*                .setPositiveButton(R.string.menu_logout, (dialogInterface, i) -> {
                    EventBus.getDefault().post(
                            new AuthFailedEvent()
                    );
                })*/
            .setCancelable(false)
            .setNegativeButton(android.R.string.ok, null)
            .create()
            .show()
    }
}
