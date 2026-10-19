package dev.russell.focuspodcast

import dev.russell.focuspodcast.common.BaseApplication
import dev.russell.focuspodcast.common.util.BasePreferenceUtil
import dev.russell.focuspodcast.common.util.PodcastSearchPreferenceUtil
import dev.russell.focuspodcast.activity.SplashActivity
import dev.russell.focuspodcast.appshortcuts.ShortcutsDefaultList
import dev.russell.focuspodcast.config.CategoriesDefaultList
import dev.russell.focuspodcast.config.DownloadServiceCallbacksImpl
import dev.russell.focuspodcast.config.PodcastSearchDefaultList
import dev.russell.focuspodcast.core.ApCoreEventBusIndex
import dev.russell.focuspodcast.core.ClientConfig
import dev.russell.focuspodcast.error.RxJavaErrorHandlerSetup
import dev.russell.focuspodcast.pref.AppLanguage
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import dev.russell.focuspodcast.theme.ThemeStore
import dev.russell.focuspodcast.theme.util.VersionUtils
import dev.russell.focuspodcast.theme.shortcut.DynamicShortcutManager
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
        fun runOnUiThread(runnable: Runnable) {
            uiThreadHandler.post(runnable)
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
        AppLanguage.migrateLegacyPreference(this)
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
