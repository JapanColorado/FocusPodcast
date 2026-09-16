package allen.town.podcast

import allen.town.focus_common.BaseApplication
import allen.town.focus_common.util.BasePreferenceUtil
import allen.town.focus_common.util.PodcastSearchPreferenceUtil
import allen.town.podcast.activity.SplashActivity
import allen.town.podcast.appshortcuts.ShortcutsDefaultList
import allen.town.podcast.config.CategoriesDefaultList
import allen.town.podcast.config.DownloadServiceCallbacksImpl
import allen.town.podcast.config.PodcastSearchDefaultList
import allen.town.podcast.core.ApCoreEventBusIndex
import allen.town.podcast.core.ClientConfig
import allen.town.podcast.error.RxJavaErrorHandlerSetup
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import code.name.monkey.appthemehelper.ThemeStore
import code.name.monkey.appthemehelper.util.VersionUtils
import code.name.monkey.retromusic.appshortcuts.DynamicShortcutManager
import com.joanzapata.iconify.Iconify
import com.joanzapata.iconify.fonts.FontAwesomeModule
import com.joanzapata.iconify.fonts.MaterialModule
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
    }

    override fun onCreate() {
        super.onCreate()
        RxJavaErrorHandlerSetup.setupRxJavaErrorHandler()
        instance = this
        //--------------------------------------------------

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

        ClientConfig.initialize(this, DownloadServiceCallbacksImpl())
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

    }
}
