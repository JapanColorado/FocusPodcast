package allen.town.focus_common.ad

import allen.town.core.service.AdService
import allen.town.core.service.PayService
import allen.town.focus_common.R
import allen.town.focus_common.ads.OnUserEarnedRewardListener
import allen.town.focus_common.util.Timber
import allen.town.focus_common.util.TopSnackbarUtil
import android.app.Activity
import android.widget.Toast
import com.wyjson.router.GoRouter
import com.zh.pocket.ads.reward_video.RewardVideoAD
import com.zh.pocket.ads.reward_video.RewardVideoADListener
import com.zh.pocket.error.ADError
import java.util.*


object RewardedAdManager {
    private var mRewardVideoAD: RewardVideoAD? = null
    var isLoaded = false
    private var loadTime: Long = 0
    private var isEarned = false

    @JvmStatic
    fun loadRewardedAd(context: Activity) {
        if (!isLoaded) {
            isEarned = false
            mRewardVideoAD =
                RewardVideoAD(context, GoRouter.getInstance().getService(AdService::class.java)!!.getRewardedAdUnitId())
            mRewardVideoAD!!.setRewardVideoADListener(object : RewardVideoADListener {
                override fun onADLoaded() {
//                    mRewardVideoAD!!.getExpireTimestamp()
                    Timber.d("on reward ads onAdLoaded")
                    loadTime = Date().time
                    isLoaded = true
                }

                override fun onVideoCached() {}
                override fun onADShow() {}
                override fun onADExposure() {}
                override fun onReward() {}
                override fun onADClicked() {}
                override fun onVideoComplete() {}
                override fun onADClosed() {}
                override fun onSuccess() {}
                override fun onFailed(error: ADError) {
                    Timber.e("on reward ads loaded failed $error")
                }

                override fun onSkippedVideo() {}
            })
            mRewardVideoAD!!.loadAD()
        }

    }

    @JvmStatic
    fun showRewardedVideo(
        context: Activity,
        onUserEarnedRewardListener: OnUserEarnedRewardListener
    ) {
        if (!isLoaded) {
            Timber.d("The rewarded ad wasn't ready yet.")
            TopSnackbarUtil.showSnack(context, R.string.rewarded_ad_not_ready, Toast.LENGTH_LONG)
            return
        } else if (!isAdAvailable()) {
            Timber.d("The rewarded ad expired.")
            TopSnackbarUtil.showSnack(context, R.string.rewarded_ad_not_ready, Toast.LENGTH_LONG)
            return
        }
        mRewardVideoAD!!.setRewardVideoADListener(object : RewardVideoADListener {
            override fun onADLoaded() {
            }

            override fun onVideoCached() {}
            override fun onADShow() {
                Timber.d("on reward ads show")
            }

            override fun onADExposure() {}
            override fun onReward() {
                Timber.i("The user earned the reward.")
                isEarned = true
                onUserEarnedRewardListener.onUserEarnedReward()
            }

            override fun onADClicked() {}
            override fun onVideoComplete() {
                Timber.i("on reward ads complete")
            }

            override fun onADClosed() {
                Timber.i("on reward ads closed")
                isLoaded = false
                onUserEarnedRewardListener.onClosed(isEarned)
                // Preload the next rewarded ad.
                loadRewardedAd(context)
            }

            override fun onSuccess() {}
            override fun onFailed(error: ADError) {
                Timber.e("on reward ads show failed $error")
                isLoaded = false
            }

            override fun onSkippedVideo() {}
        })
        mRewardVideoAD!!.showAD()
    }

    /** Utility method to check if ad was loaded more than n hours ago.  */
    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference: Long = Date().time - this.loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    /** Utility method that checks if ad exists and can be shown.  */
    fun isAdAvailable(): Boolean {
        return isLoaded && wasLoadTimeLessThanNHoursAgo(1)
    }
}