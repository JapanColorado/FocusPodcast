package allen.town.focus_common.ad

import allen.town.core.service.AdService
import allen.town.core.service.AppService
import allen.town.core.service.PayService
import allen.town.focus_common.util.BasePreferenceUtil
import allen.town.focus_common.util.Timber
import android.app.Activity
import com.wyjson.router.GoRouter
import com.zh.pocket.ads.fullscreen_video.FullscreenVideoAD
import com.zh.pocket.ads.fullscreen_video.FullscreenVideoADListener
import com.zh.pocket.error.ADError


object InterstitialAdManager {

    private var mAdIsLoaded: Boolean = false
    private lateinit var mFullscreenVideoAD: FullscreenVideoAD

    @JvmStatic
    fun loadAd(context: Activity, showImmediately: Boolean = false) {

        if (GoRouter.getInstance().getService(PayService::class.java)!!.isAdBlocker()) {
            //去广告会员不显示广告
            return
        }

        if (!BasePreferenceUtil.interstitialAdTimeValid) {
            Timber.i("Interstitial ads last show in 20m, so ignore")
            return
        }
        if (showImmediately && mAdIsLoaded) {
            Timber.i("Interstitial ads play when call load")
            showInterstitial(context)
            return
        }

        mFullscreenVideoAD =
            FullscreenVideoAD(context, GoRouter.getInstance().getService(AdService::class.java)!!.getInterstitialAdUnitId())
        mFullscreenVideoAD.setFullscreenVideoADListener(object : FullscreenVideoADListener {
            override fun onADLoaded() {
                mAdIsLoaded = true
                Timber.d("Interstitial ads was loaded.")
            }

            override fun onVideoCached() {}
            override fun onADShow() {
                BasePreferenceUtil.setInterstitialAdTime(System.currentTimeMillis())
                Timber.d("on Interstitial ads show")
            }

            override fun onADExposure() {}
            override fun onADClicked() {}
            override fun onVideoComplete() {}
            override fun onADClosed() {
                Timber.i("on Interstitial ads closed")
                mAdIsLoaded = false
//                loadAd(context)
            }

            override fun onSuccess() {}
            override fun onFailed(error: ADError) {
                mAdIsLoaded = false
                Timber.e("Interstitial ads load failed ${error.toString()}")
            }

            override fun onSkippedVideo() {}
            override fun onPreload() {}
        })
        mFullscreenVideoAD.loadAD()
    }


    // Show the ad if it's ready.
    @JvmStatic
    fun showInterstitial(context: Activity) {

        if (GoRouter.getInstance().getService(PayService::class.java)!!.isAdBlocker()) {
            //去广告会员不显示广告
            return
        }

        if (!GoRouter.getInstance().getService(AppService::class.java)!!.isForeground()) {
            Timber.i("Interstitial ads return case not in Foreground")
            return
        }
        if (mAdIsLoaded) {
            Timber.d("Interstitial ads try to show.")
            mFullscreenVideoAD.showAD(context)
        } else {
            Timber.d("Interstitial ads wasn't loaded.")
        }

    }
}