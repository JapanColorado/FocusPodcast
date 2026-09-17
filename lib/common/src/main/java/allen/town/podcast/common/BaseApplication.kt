package allen.town.podcast.common

import allen.town.podcast.common.crash.CustomCrashHandler
import allen.town.podcast.common.error.RxJavaErrorHandlerSetup
import allen.town.podcast.common.util.BasePreferenceUtil
import allen.town.podcast.common.util.Timber
import allen.town.podcast.common.util.WallpaperAccentManager
import android.app.Activity
import android.os.Bundle
import androidx.multidex.MultiDexApplication

open class BaseApplication: MultiDexApplication() {
    open val wallpaperAccentManager = WallpaperAccentManager(this)
    private fun setLog() {
        Timber.plant(object : Timber.DebugTree() {
            override fun log(priority: Int, tag: String?, message: String?, t: Throwable?) {
                // Release builds log debug level and above
                if (BuildConfig.DEBUG || priority >= 3) {
                    super.log(priority, tag, message, t)
                }
            }
        })
    }

    override fun onTerminate() {
        super.onTerminate()
        wallpaperAccentManager.release()
    }

    var activityCounter = 0
    var onFront = false

    // Whether the app is running in the background
    fun isAppRunningBackground(): Boolean {
        var flag = false
        if (activityCounter == 0) {
            flag = true
        }
        return flag
    }

    /**
     * Whether the app just came from the background to the foreground.
     */
    fun isAppOnFront(): Boolean {
        return onFront
    }

    private inner class ActivityLifecycleCallbacksImpl : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        }

        override fun onActivityStarted(activity: Activity) {
            activityCounter++
            // Going from 0 to 1 means we came from the background to the foreground
            onFront = activityCounter == 1
        }

        override fun onActivityResumed(activity: Activity) {
        }

        override fun onActivityPaused(activity: Activity) {
        }

        override fun onActivityStopped(activity: Activity) {
            activityCounter--
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        }

        override fun onActivityDestroyed(activity: Activity) {
        }
    }

    override fun onCreate() {
        super.onCreate()
        BasePreferenceUtil.instance(this)
        if (!BuildConfig.DEBUG) {
            CustomCrashHandler.getInstance().setCustomCrashHandler()
        }
        setLog()
        RxJavaErrorHandlerSetup.setupRxJavaErrorHandler()
        if(needInitDefaultWallpaperAccent()){
            // The system wallpaper listener is registered only once; subclasses may register the callback themselves
            wallpaperAccentManager.init()
        }

        registerActivityLifecycleCallbacks(ActivityLifecycleCallbacksImpl())
    }

    open fun needInitDefaultWallpaperAccent(): Boolean {
        return true
    }
}