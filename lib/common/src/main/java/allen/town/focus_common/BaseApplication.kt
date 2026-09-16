package allen.town.focus_common

import allen.town.focus_common.ad.AppOpenAdManager
import allen.town.focus_common.crash.CustomCrashHandler
import allen.town.focus_common.error.RxJavaErrorHandlerSetup
import allen.town.focus_common.util.BasePreferenceUtil
import allen.town.focus_common.util.Timber
import allen.town.focus_common.util.WallpaperAccentManager
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import androidx.multidex.MultiDexApplication
import com.wyjson.router.GoRouter

const val PRODUCT_CHINA = "free"
const val PRODUCT_DROID = "fdroid"
open class BaseApplication: MultiDexApplication() {
    open val wallpaperAccentManager = WallpaperAccentManager(this)
    var isAlipay = false
    var isDroid = false
    open var needOpenPurchaseWhenAppOpen = false

    //打开开关后调试支付宝支付,上线前一定要置为false
    private val debugAlipay = false
    private fun setAlipay() {
        if (PRODUCT_CHINA.equals(BuildConfig.FLAVOR, ignoreCase = true) || debugAlipay) {
            isAlipay = true
        }
    }

    private fun setDroid() {
        if (PRODUCT_DROID.equals(BuildConfig.FLAVOR, ignoreCase = true)) {
            isDroid = true
        }
    }


    private fun setArouter() {
        if (BuildConfig.DEBUG) {           // 这两行必须写在init之前，否则这些配置在init过程中将无效
            GoRouter.openDebug()   // 开启调试模式(如果在InstantRun模式下运行，必须开启调试模式！线上版本需要关闭,否则有安全风险)
        }
        GoRouter.autoLoadRouteModule(this) // 尽可能早，推荐在Application中初始化
    }

    private fun setLog() {
        Timber.plant(object : Timber.DebugTree() {
            override fun log(priority: Int, tag: String?, message: String?, t: Throwable?) {
                //release版本输出d以及以上的日志
                if (BuildConfig.DEBUG || priority >= 3) {
                    super.log(priority, tag, message, t)
                }
            }
        })
    }

    lateinit var openAdManager: AppOpenAdManager
    fun isAdBlockUser(): Boolean {
        return checkAdSupporter() || isRemoveAdToady()
    }


    //---------------------订阅----------------------

    //是否是订阅用户，不能直接读
    protected var isSupporter = false

    /**
     * 设置是否是订阅用户
     */
    fun setSubSupporter(flag: Boolean) {
        isSupporter = flag
    }



    /**
     * 因为看了广告的临时会员，有效期不超过1小时
     */
    fun temporarySupporter(): Boolean {
        return BasePreferenceUtil.isRewardAdProValid()
    }

    //--------------------去广告----------------
    //购买了去广告
    protected var isAdRemover = false

    /**
     * 检查是否去去广告用户（包括内购和购买了去广告）
     */
    private fun checkAdSupporter(): Boolean {
        return isSupporter || isAdRemover || isDroid
    }

    fun setAdSupporter(flag: Boolean) {
        isAdRemover = flag
    }




    override fun onTerminate() {
        super.onTerminate()
        wallpaperAccentManager.release()
    }

    /**
     * 看了激励广告，10分钟内不再显示广告
     */
    private fun isRemoveAdToady(): Boolean {
        return BasePreferenceUtil.isRewardAdProValid()
    }

    var activityCounter = 0
    var onFront = false

    //判断App是否在后台运行
    fun isAppRunningBackground(): Boolean {
        var flag = false
        if (activityCounter == 0) {
            flag = true
        }
        return flag
    }

    /**
     * 判断App是否是从后台到前台
     */
    fun isAppOnFront(): Boolean {
        return onFront
    }

    /**
     *
     * 判断某activity是否处于栈顶
     * @return true在栈顶 false不在栈顶
     */
    private fun isActivityTop(cls: Class<*>, context: Context): Boolean {
        val manager: ActivityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val name: String? = manager.getRunningTasks(1).get(0).topActivity?.className
        return name == cls.name
    }

    private inner class ActivityLifecycleCallbacksImpl : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        }

        override fun onActivityStarted(activity: Activity) {
            activityCounter++
            //数值从0 变到 1 说明是从后台切到前台
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
        setAlipay()
        setDroid()
        setArouter()
        needOpenPurchaseWhenAppOpen()
        //--------------------------------------------------

        setLog()
        RxJavaErrorHandlerSetup.setupRxJavaErrorHandler()
        if(needInitDefaultWallpaperAccent()){
            //系统壁纸监听只会执行一次，可能交给实现类去注册回调
            wallpaperAccentManager.init()
        }

        registerActivityLifecycleCallbacks(ActivityLifecycleCallbacksImpl())
        openAdManager = AppOpenAdManager(this)

    }

    private fun needOpenPurchaseWhenAppOpen() {
        //when app opened secondly, open purchase page
        val count = BasePreferenceUtil.appOpenCount + 1
        if (count < 3) {
            BasePreferenceUtil.appOpenCount = count
        }
        needOpenPurchaseWhenAppOpen = count == 2
    }

    open fun needInitDefaultWallpaperAccent(): Boolean {
        return true
    }
}