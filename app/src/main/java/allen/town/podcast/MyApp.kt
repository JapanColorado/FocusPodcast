package allen.town.podcast

import allen.town.focus_common.BaseApplication
import allen.town.focus_common.crash.Crashlytics
import allen.town.focus_common.http.LeanHttpClient
import allen.town.focus_common.http.bean.LeanAdmobBean
import allen.town.focus_common.http.bean.LeanAdmobContentBean
import allen.town.focus_common.util.BasePreferenceUtil
import allen.town.focus_common.util.JsonHelper
import allen.town.focus_common.util.PodcastSearchPreferenceUtil
import allen.town.focus_common.util.Timber
import allen.town.podcast.activity.SplashActivity
import allen.town.podcast.appshortcuts.ShortcutsDefaultList
import allen.town.podcast.config.CategoriesDefaultList
import allen.town.podcast.config.PodcastSearchDefaultList
import allen.town.podcast.core.ApCoreEventBusIndex
import allen.town.podcast.core.ClientConfig
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.error.RxJavaErrorHandlerSetup
import allen.town.podcast.util.NavigationUtil
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.util.Log
import code.name.monkey.appthemehelper.ThemeStore
import code.name.monkey.appthemehelper.util.VersionUtils
import code.name.monkey.retromusic.appshortcuts.DynamicShortcutManager
import com.google.gson.reflect.TypeToken
import com.joanzapata.iconify.Iconify
import com.joanzapata.iconify.fonts.FontAwesomeModule
import com.joanzapata.iconify.fonts.MaterialModule
import io.reactivex.android.schedulers.AndroidSchedulers
import org.greenrobot.eventbus.EventBus

/** Main application class.  */
class MyApp : BaseApplication() {



    companion object {
        @JvmStatic
        lateinit var instance: MyApp
            private set
        val uiThreadHandler = Handler(Looper.getMainLooper())

        @JvmStatic
        fun runOnUiThread(runnable: Runnable?) {
            uiThreadHandler.post(runnable!!)
        }


        @JvmStatic
        fun forceRestart() {
            val intent = Intent(instance, SplashActivity::class.java)
            val cn = intent.component
            val mainIntent = Intent.makeRestartActivityTask(cn)
            instance.startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }

        // make sure that ClientConfigurator executes its static code
        init {
            try {
                Class.forName("allen.town.podcast.config.ClientConfigurator")
            } catch (e: Exception) {
                throw RuntimeException("ClientConfigurator not found", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        RxJavaErrorHandlerSetup.setupRxJavaErrorHandler()
        instance = this
        //--------------------------------------------------

        ProductWrap.setBaiduStat(this)

        // default theme
        if (!ThemeStore.isConfigured(this, 1)) {
            ThemeStore.editTheme(this)
                .accentColorRes(R.color.deault_accent_color)
                .coloredNavigationBar(true)
                .commit()
        }

        if (BuildConfig.DEBUG) {
            val builder = VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .penaltyDropBox()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
            StrictMode.setVmPolicy(builder.build())
        }

        ClientConfig.initialize(this)
        Iconify.with(FontAwesomeModule())
        Iconify.with(MaterialModule())
        EventBus.builder()
            .addIndex(ApEventBusIndex())
            .addIndex(ApCoreEventBusIndex())
            .logNoSubscriberMessages(false)
            .sendNoSubscriberEvent(false)
            .installDefaultEventBus()


        if (VersionUtils.hasNougatMR()) {
            DynamicShortcutManager(
                this, ShortcutsDefaultList(this).defaultShortcuts
            ).initDynamicShortcuts()
        }
        BasePreferenceUtil.defaultCategories = CategoriesDefaultList.defaultList
        PodcastSearchPreferenceUtil.defaultSearchEngine = PodcastSearchDefaultList.defaultList

        Crashlytics.getInstance().setCrashlyticsCollectionEnabled(!Prefs.isDisableFirebase)

        getAdmobAdInfo()
    }



    private fun getAdmobAdInfo() {
        if (!isAlipay && !isDroid && BasePreferenceUtil.needCheckAdmobInfo()) {
            Timber.d("getAdmobAdInfo")
            LeanHttpClient.getAdmob("634e1a673c7cfa40a67829ca")
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { leanAdmobBean: LeanAdmobBean? ->
                        BasePreferenceUtil.lastAdmobCheckTime = System.currentTimeMillis()
                        leanAdmobBean?.run {
                            val content = leanAdmobBean.content
                            content?.run {
                                val leanAdmobContentBeanList: ArrayList<LeanAdmobContentBean>? =
                                    JsonHelper.parseObjectList(
                                        this,
                                        object : TypeToken<ArrayList<LeanAdmobContentBean>>() {
                                        }.type
                                    )
                                leanAdmobContentBeanList?.run {
                                    for (leanAdmobContentBean in this) {
                                        BasePreferenceUtil.setStringValue(
                                            leanAdmobContentBean.type,
                                            leanAdmobContentBean.id
                                        )
                                    }
                                }
                            }
                        }
                    },
                    { error: Throwable? ->
                        Timber.e(
                            "failed to getAdmobAdInfo ${Log.getStackTraceString(error)}"
                        )
                    }
                )
        }
    }


    /**
     * 不是订阅用户跳转付费界面
     *
     * @return true 代表订阅了
     */
    fun checkSupporter(context: Context?, gotoPro: Boolean = true): Boolean {
        if (!isSupporter && !temporarySupporter() && gotoPro) {
            context?.run {
                NavigationUtil.goToProVersion(context)
            }
        }
        return isSupporter || temporarySupporter() || isDroid
    }


}