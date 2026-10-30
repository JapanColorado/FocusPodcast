package dev.russell.yagi

import dev.russell.yagi.common.BaseApplication
import dev.russell.yagi.common.util.BasePreferenceUtil
import dev.russell.yagi.common.util.PodcastSearchPreferenceUtil
import dev.russell.yagi.activity.SplashActivity
import dev.russell.yagi.appshortcuts.ShortcutsDefaultList
import dev.russell.yagi.config.CategoriesDefaultList
import dev.russell.yagi.config.DownloadServiceCallbacksImpl
import dev.russell.yagi.config.PodcastSearchDefaultList
import dev.russell.yagi.core.ApCoreEventBusIndex
import dev.russell.yagi.core.ClientConfig
import dev.russell.yagi.core.storage.PreferencesBackup
import dev.russell.yagi.error.RxJavaErrorHandlerSetup
import dev.russell.yagi.pref.AppLanguage
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import dev.russell.yagi.theme.ThemeStore
import dev.russell.yagi.theme.util.VersionUtils
import dev.russell.yagi.theme.shortcut.DynamicShortcutManager
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
        // Settings from a database import; must land before anything reads or watches them
        PreferencesBackup.applyPendingRestore(this)
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
