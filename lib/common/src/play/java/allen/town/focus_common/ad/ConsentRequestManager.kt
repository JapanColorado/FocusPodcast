package allen.town.focus_common.ad

import allen.town.focus_common.util.Timber
import android.app.Activity
import com.google.android.ump.*
import java.util.Random

/**
 * https://developers.google.com/admob/android/privacy?hl=zh-cn#kotlin
 * 欧盟GDPR广告授权弹窗
 */
class ConsentRequestManager {
    private lateinit var consentInformation: ConsentInformation

    fun showConsentForm(context: Activity) {
        val code = Random().nextInt(4)
        Timber.d("code $code")
        if(code != 1){
            Timber.d("random failed")
            return
        }
        //每个应用的id不一样控制台看日志搜索test
        val debugSettings = ConsentDebugSettings.Builder(context)
            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            .addTestDeviceHashedId("6DF6E786F4A83929838C90452EB8C9A1")
            .build()

        // Set tag for under age of consent. false means users are not under
        // age.
        val params = ConsentRequestParameters
            .Builder()
            .setTagForUnderAgeOfConsent(false)
//            .setConsentDebugSettings(debugSettings)
            .build()

        consentInformation = UserMessagingPlatform.getConsentInformation(context)
        //通过 UMP SDK 测试应用时，您可能会发现重置 SDK 的状态很实用，因为您可以模拟用户的首次安装体验
//        consentInformation.reset()
        consentInformation.requestConsentInfoUpdate(
            context,
            params,
            {
                // The consent information state was updated.
                // You are now ready to check if a form is available.
                if (consentInformation.isConsentFormAvailable) {
                    loadForm(context)
                }
            },
            {
                // Handle the error.
                Timber.w("requestConsentInfoUpdate failed ${it.message}")
            })
    }

    private fun loadForm(context: Activity) {
        // Loads a consent form. Must be called on the main thread.
        UserMessagingPlatform.loadConsentForm(
            context,
            {
                if (consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                    it.show(
                        context
                    ) {
                        if (consentInformation.consentStatus == ConsentInformation.ConsentStatus.OBTAINED) {
                            // App can start requesting ads.
                        }

                        // Handle dismissal by reloading form.
                        loadForm(context)
                    }
                }
            },
            {
                // Handle the error.
                Timber.w("loadForm failed ${it.message}")
            }
        )
    }
}