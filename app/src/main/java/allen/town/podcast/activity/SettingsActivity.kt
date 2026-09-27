package allen.town.podcast.activity

import allen.town.podcast.common.views.AccentMaterialDialog
import allen.town.podcast.R
import allen.town.podcast.databinding.SettingsActivityBinding
import allen.town.podcast.fragment.pref.*
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.preference.PreferenceFragmentCompat
import allen.town.podcast.common.extensions.applyToolbar
import allen.town.podcast.searchpreference.SearchConfiguration
import allen.town.podcast.searchpreference.SearchPreferenceResult
import allen.town.podcast.searchpreference.SearchPreferenceResultListener

/**
 * Hosts the settings screens. Which fragment shows which preference XML, the page titles and the
 * search breadcrumbs all come from [SettingsScreen].
 */
open class SettingsActivity : SimpleToolbarActivity(), SearchPreferenceResultListener {
    lateinit var binding: SettingsActivityBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyToolbar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { v -> onOptionsItemSelected() }
        if (supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer,
                    MainPrefFragment(), FRAGMENT_TAG)
                .commit()
        }
        // Only on a fresh start: after a recreate (theme, language) the back stack is restored.
        if (savedInstanceState == null && intent.getBooleanExtra(OPEN_AUTO_DOWNLOAD_SETTINGS, false)) {
            openScreen(R.xml.pref_auto_download)
        }
    }

    override fun setTitle(titleId: Int) {
        binding.collapsingToolbarLayout.title = getString(titleId)
    }

    open fun setSubtitle(titleId: Int) {
        binding.subTitle.text = getString(titleId)
    }

    open fun setSubtitle(title: String) {
        binding.subTitle.text = title
    }

    fun openScreen(screen: Int): PreferenceFragmentCompat? {
        val fragment = SettingsScreen.fromXml(screen)?.factory?.invoke()
        if (fragment != null) {
            supportFragmentManager.beginTransaction().setCustomAnimations(
                    R.anim.retro_fragment_open_enter,
            R.anim.retro_fragment_open_exit,
            R.anim.retro_fragment_close_enter,
            R.anim.retro_fragment_close_exit).replace(R.id.settingsContainer, fragment)
                .addToBackStack(getString(getTitleOfPage(screen))).commit()
        }
        return fragment
    }

    fun onOptionsItemSelected(): Boolean {
        if (supportFragmentManager.backStackEntryCount == 0) {
            finish()
        } else {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            var view = currentFocus
            //If no view currently has focus, create a new one, just so we can grab a window token from it
            if (view == null) {
                view = View(this)
            }
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            supportFragmentManager.popBackStack()
        }
        return true
    }

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        val screen = result.resourceFile
        if (SettingsScreen.fromXml(screen)?.factory == null) {
            // Per-podcast settings are indexed so users can find them, but live on each podcast.
            val builder = AccentMaterialDialog(
                this,
                R.style.MaterialAlertDialogTheme
            )
            builder.setTitle(R.string.feed_settings_label)
            builder.setMessage(R.string.pref_feed_settings_dialog_msg)
            builder.setPositiveButton(android.R.string.ok, null)
            builder.show()
        } else {
            val fragment = openScreen(result.resourceFile)
            result.highlight(fragment)
        }
    }

    companion object {
        private const val FRAGMENT_TAG = "tag_preferences"
        const val OPEN_AUTO_DOWNLOAD_SETTINGS = "OpenAutoDownloadSettings"

        @JvmStatic
        fun getTitleOfPage(preferences: Int): Int =
            SettingsScreen.fromXml(preferences)?.titleRes ?: R.string.settings_label
    }
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.anim_activity_stay, R.anim.retro_fragment_close_exit)
    }
}

/**
 * Every preference XML the settings search indexes. Each one is shown by exactly one fragment
 * ([factory]), and no key may appear in two of them, because a search hit opens the fragment for
 * its XML and highlights the key there. [parent] is the screen it is reached from, which gives
 * the search breadcrumbs. A null [factory] marks XMLs that are indexed but shown elsewhere (the
 * per-podcast settings); order matters only in that a parent must be declared before its children.
 */
enum class SettingsScreen(
    @XmlRes val xml: Int,
    @StringRes val titleRes: Int,
    val parent: SettingsScreen?,
    val factory: (() -> PreferenceFragmentCompat)?,
) {
    APPEARANCE(R.xml.pref_appearance, R.string.appearance, null, { AppearancePrefFragment() }),
    PLAYBACK(R.xml.pref_playback, R.string.playback_pref, null, { PlaybackPrefFragment() }),
    AD_SKIP(R.xml.pref_ad_skip, R.string.pref_ad_skip_category, null, { AdSkipPrefFragment() }),
    SUBSCRIPTIONS(R.xml.pref_subscriptions, R.string.pref_subscriptions_updates_title, null, { SubscriptionsPrefFragment() }),
    NETWORK(R.xml.pref_network, R.string.network_pref, null, { NetworkPrefFragment() }),
    AUTO_DOWNLOAD(R.xml.pref_auto_download, R.string.pref_automatic_download_title, NETWORK, { AutoDownloadPrefFragment() }),
    STORAGE(R.xml.pref_storage, R.string.storage_pref, null, { StoragePrefFragment() }),
    IMPORT_EXPORT(R.xml.pref_import_export, R.string.import_export_pref, STORAGE, { ImportExportPreferencesFragment() }),
    SYNC(R.xml.pref_sync, R.string.synchronization_pref, null, { SyncPrefFragment() }),
    NOTIFICATIONS(R.xml.pref_notifications, R.string.notification_pref_fragment, null, { NotificationPrefFragment() }),
    OTHERS(R.xml.pref_others, R.string.others, null, { OthersFragment() }),
    FEED_SETTINGS(R.xml.feed_settings, R.string.feed_settings_label, null, null),
    AUDIO_EFFECT(R.xml.pref_audio_effect, R.string.audio_effects, FEED_SETTINGS, null);

    /** Page titles from the top-level entry down to this screen. */
    val breadcrumbs: List<Int>
        get() = (parent?.breadcrumbs ?: emptyList()) + titleRes

    companion object {
        @JvmStatic
        fun fromXml(@XmlRes xml: Int): SettingsScreen? = values().firstOrNull { it.xml == xml }

        /** Adds every screen to the settings search, with its breadcrumb trail. */
        @JvmStatic
        fun indexAll(config: SearchConfiguration) {
            for (screen in values()) {
                val item = config.index(screen.xml)
                screen.breadcrumbs.forEach { item.addBreadcrumb(it) }
            }
        }
    }
}
