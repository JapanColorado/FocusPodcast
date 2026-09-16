package allen.town.focus_common.ad

import allen.town.core.service.AdService
import allen.town.core.service.PayService
import allen.town.focus_common.ads.OnOpenAdFinishListener
import allen.town.focus_common.util.BasePreferenceUtil
import allen.town.focus_common.util.Timber
import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.os.CountDownTimer
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.wyjson.router.GoRouter
import java.util.*

private const val COUNTER_TIME = 2L

class AppOpenAdManager : LifecycleObserver, ActivityLifecycleCallbacks {
    private var appOpenAd: AppOpenAd? = null
    private var currentActivity: Activity? = null
    private var loadTime: Long = 0
    private lateinit var loadCallback: AppOpenAdLoadCallback
    private var myApplication: Application? = null

    /** Constructor  */
    constructor(myApplication: Application?) {
        this.myApplication = myApplication
        this.myApplication!!.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    constructor() {}

    /** Utility method to check if ad was loaded more than n hours ago.  */
    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    /** Utility method that checks if ad exists and can be shown.  */
    val isAdAvailable: Boolean
        get() = appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)

    /** LifecycleObserver methods  */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        fetchAd()
        Timber.d("open ads manager onStart")
    }

    /** Request an ad  */
    fun fetchAd() {
        // Have unused ad, no need to fetch another.
        if (isAdAvailable || GoRouter.getInstance().getService(PayService::class.java)!!.isAdBlocker()) {
            return
        }
        loadCallback = object : AppOpenAdLoadCallback() {
            /**
             * Called when an app open ad has loaded.
             *
             * @param ad the loaded app open ad.
             */
            override fun onAdLoaded(ad: AppOpenAd) {
                appOpenAd = ad
                loadTime = Date().time
                Timber.i("open ads onAdLoaded")
            }

            /**
             * Called when an app open ad has failed to load.
             *
             * @param loadAdError the error.
             */
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                // Handle the error.
                Timber.e("open ads onAdFailedToLoad " + loadAdError.message)
            }
        }
        val request = adRequest
        AppOpenAd.load(
            myApplication!!, GoRouter.getInstance().getService(AdService::class.java)!!.getOpenAdUnitId(), request,
            AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT, loadCallback
        )
    }

    /** Shows the ad if one isn't already showing.  */
    fun showAdIfAvailable(onShowAdCompleteListener: OnOpenAdFinishListener? = null) {
        // Only show ad if there is not already an app open ad currently showing
        // and an ad is available.
        if (!isShowingAd && isAdAvailable && !GoRouter.getInstance().getService(PayService::class.java)!!.isAdBlocker()) {
            Timber.d("Will show open ads.")
            val fullScreenContentCallback: FullScreenContentCallback =
                object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        // Set the reference to null so isAdAvailable() returns false.
                        appOpenAd = null
                        isShowingAd = false
                        onShowAdCompleteListener?.onNext()
                        fetchAd()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Timber.e("open ads failed show " + adError.message)
                        onShowAdCompleteListener?.onNext()
                        fetchAd()
                    }

                    override fun onAdShowedFullScreenContent() {
                        isShowingAd = true
                    }
                }
            appOpenAd!!.fullScreenContentCallback = fullScreenContentCallback
            currentActivity?.run {
                //偶然测试timer的onFinish走了两次
                appOpenAd!!.show(this)
            }
        } else {
            Timber.d("Can not show open ads.")
            onShowAdCompleteListener?.onNext()
            fetchAd()
        }
    }

    fun fetchAd(adContainer: ViewGroup?, onOpenAdFinishListener: OnOpenAdFinishListener) {
        if (BasePreferenceUtil.firstInstallAndLaunch) {
            Timber.d("do not show when install first")
            BasePreferenceUtil.firstInstallAndLaunch = false
            onOpenAdFinishListener.onNext()
            return
        }
        if (GoRouter.getInstance().getService(PayService::class.java)!!.isAdBlocker()) {
            onOpenAdFinishListener.onNext()
        } else {
            // Create a timer so the SplashActivity will be displayed for a fixed amount of time.
            createTimer(COUNTER_TIME, onOpenAdFinishListener)
        }

    }

    /**
     * Create the countdown timer, which counts down to zero and show the app open ad.
     *
     * @param seconds the number of seconds that the timer counts down from
     */
    private fun createTimer(seconds: Long, onOpenAdFinishListener: OnOpenAdFinishListener) {
        val countDownTimer: CountDownTimer = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                Timber.d("onTick " + millisUntilFinished / 1000)
            }

            override fun onFinish() {
                Timber.d("onFinish")
                // Show the app open ad.
                showAdIfAvailable(
                    onOpenAdFinishListener
                )
            }
        }
        countDownTimer.start()
    }


    /** Creates and returns ad request.  */
    private val adRequest: AdRequest
        private get() = AdRequest.Builder().build()

    /**
     * Interface definition for a callback to be invoked when an app open ad is complete (i.e.
     * dismissed or fails to show).
     */
    interface OnShowAdCompleteListener {
        fun onShowAdComplete()
    }

    /** ActivityLifecycleCallback methods  */
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivity = activity
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityStopped(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        currentActivity = null
    }

    companion object {
        private var isShowingAd = false
    }
}