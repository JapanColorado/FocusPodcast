package allen.town.focus_common.ad;

import static androidx.lifecycle.Lifecycle.Event.ON_PAUSE;
import static androidx.lifecycle.Lifecycle.Event.ON_RESUME;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.wyjson.router.GoRouter;
import com.zh.pocket.ads.splash.SplashAD;
import com.zh.pocket.ads.splash.SplashADListener;
import com.zh.pocket.error.ADError;
import com.zh.pocket.utils.ActivityFrontBackProcessor;

import java.util.Date;

import allen.town.core.service.AdService;
import allen.town.core.service.PayService;
import allen.town.focus_common.ads.OnOpenAdFinishListener;
import allen.town.focus_common.util.Timber;

public class AppOpenAdManager implements LifecycleObserver, Application.ActivityLifecycleCallbacks {
    private Activity currentActivity;
    private static boolean isShowingAd = false;
    private long loadTime = 0;

    private Application myApplication;
    public boolean mDisrupt = false;

    /**
     * Constructor
     */
    public AppOpenAdManager(Application myApplication) {
        this.myApplication = myApplication;
        this.myApplication.registerActivityLifecycleCallbacks(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    private AppOpenAdManager() {
    }

    /**
     * Utility method to check if ad was loaded more than n hours ago.
     */
    private boolean wasLoadTimeLessThanNHoursAgo(long numHours) {
        long dateDifference = (new Date()).getTime() - this.loadTime;
        long numMilliSecondsPerHour = 3600000;
        return (dateDifference < (numMilliSecondsPerHour * numHours));
    }

    /**
     * Utility method that checks if ad exists and can be shown.
     */
    public boolean isAdAvailable() {
        return /*appOpenAd != null &&*/ wasLoadTimeLessThanNHoursAgo(4);
    }

    /**
     * LifecycleObserver methods
     */
    @OnLifecycleEvent(ON_RESUME)
    public void onResume() {
        Timber.d("onResume");
        if (mDisrupt) {
            next();
        }
        mDisrupt = true;
    }

    @OnLifecycleEvent(ON_PAUSE)
    public void onPause() {
        Timber.d("onPause");
        mDisrupt = false;
    }

    private OnOpenAdFinishListener mOnOpenAdFinishListener;

    /**
     * Request an ad
     */
    public void fetchAd(ViewGroup adContainer, OnOpenAdFinishListener onOpenAdFinishListener) {
        this.mOnOpenAdFinishListener = onOpenAdFinishListener;
        if (GoRouter.getInstance().getService(PayService.class).isAdBlocker()) {
            //目前腾讯不支持android13，口袋sdk启动广告失败没有回调，会卡在启动页
            //区别是从后台到前台这种情况（广告sdk拉起的）还是冷启动
            if (!ActivityFrontBackProcessor.toFront(currentActivity.getIntent())) {
                Timber.i("splash_ad back_2_front for pro user");
                mOnOpenAdFinishListener.onNext();
            }
            currentActivity.finish();
            return;

        }
        SplashAD splashAD = new SplashAD(currentActivity, GoRouter.getInstance().getService(AdService.class).getOpenAdUnitId());
        splashAD.setSplashADListener(new SplashADListener() {
            @Override
            public void onFailed(ADError adError) {
                Timber.e("open ads failed " + adError.toString());
                next();
            }

            @Override
            public void onADExposure() {

            }

            @Override
            public void onADClicked() {

            }

            @Override
            public void onADDismissed() {
                next();
            }

            @Override
            public void onADTick(long l) {

            }
        });
        splashAD.show(adContainer);

    }

    private void next() {
        if (mDisrupt) {
            Timber.i("splash_ads mDisrupt == true");
            if (currentActivity == null || !ActivityFrontBackProcessor.toFront(currentActivity.getIntent())) {
                Timber.i("splash_ads not back_2_front");
                if (mOnOpenAdFinishListener != null) {
                    Timber.i("splash_ads go to next");
                    mOnOpenAdFinishListener.onNext();
                    if(currentActivity == null){
                        FirebaseCrashlytics.getInstance().log("splash_ads activity is null so goto next");
                    }
                }
            }
            if (currentActivity != null) {
                Timber.i("splash_ads close activity");
                currentActivity.finish();
            } else {
                FirebaseCrashlytics.getInstance().log("splash_ads activity is null");
            }
        } else {
            mDisrupt = true;
        }
    }

    /**
     * Shows the ad if one isn't already showing.
     */
    public void showAdIfAvailable() {
        if (GoRouter.getInstance().getService(PayService.class).isAdBlocker()) {
            //去广告会员不显示广告
            return;
        }
        // Only show ad if there is not already an app open ad currently showing
        // and an ad is available.
        if (!isShowingAd && isAdAvailable()) {
            Timber.d("Will show ad.");


        } else {
            Timber.d("Can not show ad.");
        }
    }


    /**
     * ActivityLifecycleCallback methods
     */
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        currentActivity = activity;
    }

    @Override
    public void onActivityStarted(Activity activity) {
        currentActivity = activity;
    }

    @Override
    public void onActivityResumed(Activity activity) {
        currentActivity = activity;
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        currentActivity = null;
    }
}
