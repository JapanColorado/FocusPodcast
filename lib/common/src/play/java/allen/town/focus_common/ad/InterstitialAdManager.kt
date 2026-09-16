package allen.town.focus_common.ad

import allen.town.core.service.AdService
import allen.town.core.service.AppService
import allen.town.core.service.PayService
import allen.town.focus_common.util.Timber
import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.wyjson.router.GoRouter


object InterstitialAdManager {

    private var mInterstitialAd: InterstitialAd? = null
    private var mAdIsLoading: Boolean = false


    @JvmStatic
    fun loadAd(context: Activity, showImmediately: Boolean = false) {

        if (GoRouter.getInstance().getService(PayService::class.java)!!.isAdBlocker()) {
            //去广告会员不显示广告
            return
        }

        if(showImmediately && mInterstitialAd != null){
            Timber.i("Interstitial ads play when call load")
            showInterstitial(context)
            return
        }

        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            GoRouter.getInstance().getService(AdService::class.java)!!.getInterstitialAdUnitId(),
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Timber.e("Interstitial ads load failed ${adError.message}")
                    mInterstitialAd = null
                    mAdIsLoading = false
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Timber.d("Interstitial ads was loaded.")
                    mInterstitialAd = interstitialAd
                    mAdIsLoading = false
                }
            }
        )
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

        if (mInterstitialAd != null) {
            Timber.d("Interstitial ads try to show.")
            mInterstitialAd?.fullScreenContentCallback =
                object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Timber.d("Interstitial ads was dismissed.")
                        // Don't forget to set the ad reference to null so you
                        // don't show the ad a second time.
                        mInterstitialAd = null
//                        loadAd(context)
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Timber.d("Interstitial ads failed to show.")
                        // Don't forget to set the ad reference to null so you
                        // don't show the ad a second time.
                        mInterstitialAd = null
                    }

                    override fun onAdShowedFullScreenContent() {
                        Timber.d("Interstitial ads showed fullscreen content.")
                        // Called when ad is dismissed.
                    }
                }
            mInterstitialAd?.show(context)
        } else {
            Timber.d("Interstitial ads wasn't loaded.")
        }
    }
}