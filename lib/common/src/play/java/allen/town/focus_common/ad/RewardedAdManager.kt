package allen.town.focus_common.ad

import allen.town.core.service.AdService
import allen.town.core.service.PayService
import allen.town.focus_common.util.Timber
import allen.town.focus_common.util.TopSnackbarUtil
import android.app.Activity
import android.widget.Toast
import com.google.android.gms.ads.*
import allen.town.focus_common.ads.OnUserEarnedRewardListener
import allen.town.focus_common.R
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.wyjson.router.GoRouter


object RewardedAdManager {
    private var currentRewardedAd: RewardedAd? = null
    var isLoading = false
    private var isEarned = false

    @JvmStatic
    fun loadRewardedAd(context: Activity) {

        if (GoRouter.getInstance().getService(PayService::class.java)!!.isPurchase(context,false)) {
            //去广告会员不加载激励视频
            return
        }

        if (currentRewardedAd == null) {
            isLoading = true
            isEarned = false
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(
                context,
                GoRouter.getInstance().getService(AdService::class.java)!!.getRewardedAdUnitId(),
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        // Handle the error.
                        Timber.e("on reward ads loaded failed " + loadAdError.message)
                        currentRewardedAd = null
                        isLoading = false
                    }

                    override fun onAdLoaded(rewardedAd: RewardedAd) {
                        currentRewardedAd = rewardedAd;
                        Timber.d("on reward ads onAdLoaded")
                        isLoading = false;
                    }
                });
        }
    }

    @JvmStatic
     fun showRewardedVideo(context: Activity,onUserEarnedRewardListener: OnUserEarnedRewardListener) {
        if (currentRewardedAd == null) {
            Timber.d("The rewarded ad wasn't ready yet.")
            TopSnackbarUtil.showSnack(context,R.string.rewarded_ad_not_ready, Toast.LENGTH_LONG)
            return
        }
//        showVideoButton.setVisibility(View.INVISIBLE)
        currentRewardedAd!!.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                // Called when ad is shown.
                Timber.d("onAdShowedFullScreenContent")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                // Called when ad fails to show.
                Timber.d("onAdFailedToShowFullScreenContent")
                // Don't forget to set the ad reference to null so you
                // don't show the ad a second time.
                currentRewardedAd = null
            }

            override fun onAdDismissedFullScreenContent() {
                // Called when ad is dismissed.
                // Don't forget to set the ad reference to null so you
                // don't show the ad a second time.
                currentRewardedAd = null
                Timber.d("onAdDismissedFullScreenContent")
                onUserEarnedRewardListener.onClosed(isEarned)
                // Preload the next rewarded ad.
                loadRewardedAd(context)
            }
        }
        currentRewardedAd!!.show(
            context
        ) { rewardItem -> // Handle the reward.
            Timber.i("The user earned the reward.")
            val rewardAmount = rewardItem.amount
            val rewardType = rewardItem.type
            isEarned = true
            onUserEarnedRewardListener.onUserEarnedReward()
        }
    }

}