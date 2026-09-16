package allen.town.focus_common.ad

import allen.town.core.service.AdService
import allen.town.core.service.PayService
import allen.town.focus_common.util.Timber
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.*
import com.wyjson.router.GoRouter

object BannerAdManager {
    var bottomAdView: AdView? = null

    @JvmStatic
    fun showBannerAd(context: Activity, containerView: ViewGroup) {
        if (!GoRouter.getInstance().getService(PayService::class.java)!!.isAdBlocker()) {
            Timber.d("show bottom ads")
            if (AdRequest.Builder().build().isTestDevice(context)) {
                Timber.i("this is a test devices")
                //实际不进来
                //https://developers.google.com/admob/android/test-ads，手机被设置为测试手机不会有风险,两种方式一种是admob后台配置，一种是代码中动态设置
/*                val testDeviceIds = Arrays.asList("4A63F5D16CE801F5D321D208D681B58C")
                val configuration =
                    RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
                MobileAds.setRequestConfiguration(configuration)*/
            }

            bottomAdView = AdView(context)
            bottomAdView!!.setAdSize(AdSize.BANNER)
            bottomAdView!!.adUnitId = GoRouter.getInstance().getService(AdService::class.java)!!.getBannerAdUnitId()
            if (bottomAdView!!.childCount > 0) {
                Timber.i("banner ads will refresh it self,do not need add again")
            } else {
                containerView.addView(bottomAdView)
            }
            bottomAdView!!.loadAd(AdRequest.Builder().build())
            bottomAdView!!.adListener = object : AdListener() {
                override fun onAdClosed() {
                    // Load the next interstitial.
                    bottomAdView!!.loadAd(AdRequest.Builder().build())
                }

                override fun onAdLoaded() {
                    if (GoRouter.getInstance().getService(PayService::class.java)!!.isAdBlocker()) {
                        //虽然隐藏了广告view，但是banner广告会自动刷新，依旧会调用onAdLoaded
                        Timber.i("destroy bottom ads view ")
                        bottomAdView?.destroy()
                    } else {
                        containerView.visibility = View.VISIBLE
                        Timber.i("show banner ads success")
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Timber.i("bottom ads failed to loaded ${error.message}")
                }
            }
        } else {
            containerView.visibility = View.GONE
            Timber.d("hide ads button")
        }
    }
}