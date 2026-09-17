package allen.town.podcast.activity

import android.annotation.SuppressLint
import allen.town.focus_common.extensions.notificationRequestCode
import allen.town.focus_common.extensions.requestNotificationPermission
import allen.town.focus_common.extensions.setLightNavigationBarAuto
import allen.town.focus_common.extensions.setLightStatusBarAuto
import allen.town.focus_common.extensions.setNavigationBarColor
import allen.town.focus_common.extensions.surfaceColor
import allen.town.focus_common.util.BasePreferenceUtil.libraryCategory
import allen.town.focus_common.util.RetroUtil
import allen.town.focus_common.util.Timber
import allen.town.focus_common.util.TopSnackbarUtil.showSnack
import allen.town.focus_common.views.AccentMaterialDialog
import allen.town.podcast.BuildConfig
import allen.town.podcast.R
import allen.town.podcast.activity.main.MainFragmentNavigator
import allen.town.podcast.activity.main.MainHardwareKeys
import allen.town.podcast.activity.main.MainIntentHandler
import allen.town.podcast.activity.main.MainNavDrawer
import allen.town.podcast.activity.main.MainPlayerSheet
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.pref.Prefs.BackButtonBehavior
import allen.town.podcast.core.storage.DBTasks
import allen.town.podcast.core.util.StorageUtils
import allen.town.podcast.core.util.download.AutoUpdateManager
import allen.town.podcast.event.MessageEvent
import allen.town.podcast.fragment.AudioPlayerFragment
import allen.town.podcast.fragment.DiscoverFragment
import allen.town.podcast.fragment.FeedItemlistFragment
import allen.town.podcast.fragment.NavigationDrawerFragment
import allen.town.podcast.fragment.NavigationDrawerFragment.Companion.getLastNavFragment
import allen.town.podcast.fragment.PlaylistFragment
import allen.town.podcast.fragment.TransitionEffect
import allen.town.podcast.model.feed.Feed
import allen.town.podcast.playback.getSelectedAudioPlayerFragment
import allen.town.podcast.pref.PreferenceUpgrader
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool
import code.name.monkey.appthemehelper.constants.ThemeConstants
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers
import org.apache.commons.lang3.ArrayUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


/**
 * The activity that is shown when the user launches the app.
 *
 * The heavy lifting lives in four collaborators created in [onCreate]: [MainNavDrawer] (drawer +
 * per-fragment toolbar toggle), [MainFragmentNavigator] (top-level and child fragment
 * transactions), [MainPlayerSheet] (mini/full player bottom sheet, palette colour, anchored
 * snackbars) and [MainIntentHandler] (launch intents and deep links). The activity keeps the
 * lifecycle callbacks, the EventBus subscriber and the hardware-key handling, and forwards its
 * public API to the collaborators.
 */
class MainActivity : SimpleToolbarActivity(), OnSharedPreferenceChangeListener {
    private lateinit var navDrawerController: MainNavDrawer
    private lateinit var fragmentNavigator: MainFragmentNavigator
    private lateinit var playerSheet: MainPlayerSheet
    private lateinit var intentHandler: MainIntentHandler
    private val hardwareKeys = MainHardwareKeys(this)

    val bottomSheet: BottomSheetBehavior<View>
        get() = playerSheet.bottomSheet

    private var lastBackButtonPressTime: Long = 0
    val recycledViewPool = RecycledViewPool()

    @SuppressLint("CheckResult") // fire-and-forget: app-scoped DB work with its own onError; nothing to dispose
    public override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            ensureGeneratedViewIdGreaterThan(savedInstanceState.getInt(KEY_GENERATED_VIEW_ID, 0))
        }
        Timber.d("onCreate")
        super.onCreate(savedInstanceState)
        addEntranceActivityName(this.javaClass.simpleName)
        StorageUtils.checkStorageAvailability(this)
        // Once per process: un-flag episodes whose downloaded file no longer exists
        // (deleted externally, restored from a backup, storage folder changed, ...).
        Completable.fromAction { DBTasks.checkMissingMediaFiles(applicationContext, false) }
            .subscribeOn(Schedulers.io())
            .subscribe({ }, { Timber.e(it, "missing media file check failed") })
        if (RetroUtil.isLandscape(this) && Prefs.shouldShowColumnInLandscape()) {
            setContentView(R.layout.main_land)
        } else {
            setContentView(R.layout.main)
        }
        EventBus.getDefault().register(this)
        recycledViewPool.setMaxRecycledViews(R.id.view_type_episode_item, 25)
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)
        navDrawerController = MainNavDrawer(this) { fragmentNavigator.openPendingFragment() }
        fragmentNavigator = MainFragmentNavigator(
            this,
            { navDrawerController.isDrawerOpen },
            { navDrawerController.closeDrawerNow() }
        )
        Timber.d("app version %s , %s", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        Timber.d("Android: " + Build.VERSION.RELEASE + " " + Build.MANUFACTURER + " " + Build.MODEL)
        val fm = supportFragmentManager
        if (fm.findFragmentByTag(MAIN_FRAGMENT_TAG) == null) {
            //first look up the tag saved last time
            var lastFragment = getLastNavFragment(this)
            if (!Prefs.shouldShowLastPageOfHome()) {
                //if configured to open the first item, iterate to find it
                val categoryInfoList = libraryCategory
                for ((tag, visible) in categoryInfoList) {
                    if (visible) {
                        lastFragment = tag
                        break
                    }
                }
            }
            if (ArrayUtils.contains(NavigationDrawerFragment.NAV_DRAWER_TAGS, lastFragment)) {
                //draw tag
                loadFragment(lastFragment, null)
            } else {
                try {
                    //feed
                    loadFeedFragmentById(lastFragment.orEmpty().toInt().toLong(), null)
                } catch (e: NumberFormatException) {
                    // it's not a number, this happens if we removed
                    // a label from the NAV_DRAWER_TAGS
                    // give them a nice default...
                    loadFragment(PlaylistFragment.TAG, null)
                }
            }
        }

        //loading this lazily breaks the player on orientation change (the mini fragment shows instead); in debug builds the subscription grid animation is missing on cold start, release builds are fine
        val transaction = fm.beginTransaction()
        transaction.replace(
            R.id.audioplayerFragment,
            this.getSelectedAudioPlayerFragment(),
            AudioPlayerFragment.TAG
        )
        transaction.commit()

        navDrawerController.scheduleAttach()

        checkFirstLaunch()
        PreferenceUpgrader.checkUpgrades(this)
        playerSheet = MainPlayerSheet(this, navDrawerController.navDrawerView)
        intentHandler = MainIntentHandler(this, fragmentNavigator, playerSheet)
        playerSheet.observePaletteColor()

        if (Prefs.shouldSyncOnStart()) {
            AutoUpdateManager.runImmediate(this)
        }

        requestNotificationPermission()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == notificationRequestCode()){
            //written this way because requesting both permissions together only asks for the notification one the first time; the phone one is not asked until the app is opened again (reason unknown)
//            requestPhonePermission()
        }
    }

    /**
     * View.generateViewId stores the current ID in a static variable.
     * When the process is killed, the variable gets reset.
     * This makes sure that we do not get ID collisions
     * and therefore errors when trying to restore state from another view.
     */
    private fun ensureGeneratedViewIdGreaterThan(minimum: Int) {
        while (View.generateViewId() <= minimum) {
            // Generate new IDs
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_GENERATED_VIEW_ID, View.generateViewId())
    }

    /**
     * Every fragment has its own toolbar; this wraps the setup
     * @param toolbar
     * @param displayUpArrow
     */
    fun setupToolbarToggle(toolbar: Toolbar, displayUpArrow: Boolean) {
        navDrawerController.setupToolbarToggle(toolbar, displayUpArrow)
    }

    private fun checkFirstLaunch() {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        //on first launch, open the add-subscription drawer
        if (prefs.getBoolean(PREF_IS_FIRST_LAUNCH, true)) {
            loadFragment(DiscoverFragment.TAG, null)

            // for backward compatibility, we only change defaults for fresh installs
            Prefs.updateInterval = 12
            AutoUpdateManager.restartUpdateAlarm(this)
            val edit = prefs.edit()
            edit.putBoolean(PREF_IS_FIRST_LAUNCH, false)
            edit.apply()
        }
    }

    val isDrawerOpen: Boolean
        get() = navDrawerController.isDrawerOpen

    fun setPlayerVisible(visible: Boolean) {
        playerSheet.setPlayerVisible(visible)
    }

    /**
     * Load a fragment by tag. This replaces, so the stack only ever holds one fragment.
     * @param tag
     * @param args
     */
    fun loadFragment(tag: String?, args: Bundle?) {
        fragmentNavigator.loadFragment(tag, args)
    }

    /**
     * Load a subscription item fragment by feedId. This replaces, so the stack only ever holds one fragment.
     * @param feedId
     * @param args
     */
    fun loadFeedFragmentById(feedId: Long, args: Bundle?) {
        fragmentNavigator.loadFeedFragmentById(feedId, args)
    }

    /**
     * The only difference found so far is that this adds to the back stack, so it can be popped.
     * @param fragment
     * @param transition
     */
    @JvmOverloads
    fun loadChildFragment(
        fragment: Fragment,
        transition: TransitionEffect? = TransitionEffect.FADE_AND_SCALE
    ) {
        fragmentNavigator.loadChildFragment(fragment, transition)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        navDrawerController.onPostCreate()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        navDrawerController.onConfigurationChanged(newConfig)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        playerSheet.onRestoreInstanceState()
    }

    override fun onResume() {
        super.onResume()
        StorageUtils.checkStorageAvailability(this)
        intentHandler.handleNavIntent()
    }

    override fun onStop() {
        Timber.d("onStop")
        super.onStop()
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        super.onDestroy()
        navDrawerController.onDestroy()
        EventBus.getDefault().unregister(this)
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        if (isFinishing) {
            //this branch means the activity was destroyed by pressing back, not recreated like on a theme change; only do this in the former case
            Timber.i("isFinishing")
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Glide.get(this).clearMemory()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (navDrawerController.onOptionsItemSelected(item)) { // Tablet layout does not have a drawer
            true
        } else if (item.itemId == android.R.id.home) {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            }
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    var drawerCloseCallback: (() -> Unit)?
        get() = navDrawerController.drawerCloseCallback
        set(value) {
            navDrawerController.drawerCloseCallback = value
        }

    fun closeDrawer(callback: (() -> Unit)?) {
        navDrawerController.closeDrawer(callback)
    }

    override fun onBackPressed() {
        if (isDrawerOpen) {
            navDrawerController.closeDrawerNow()
        } else if (bottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
        } else if (supportFragmentManager.backStackEntryCount != 0) {
            super.onBackPressed()
        } else {
            when (Prefs.backButtonBehavior) {
                // Tablet layout does not have drawer
                BackButtonBehavior.OPEN_DRAWER -> navDrawerController.openDrawer()
                BackButtonBehavior.SHOW_PROMPT -> AccentMaterialDialog(
                    this,
                    R.style.MaterialAlertDialogTheme
                )
                    .setMessage(R.string.close_prompt)
                    .setPositiveButton(R.string.yes) { dialogInterface: DialogInterface?, i: Int -> super@MainActivity.onBackPressed() }
                    .setNegativeButton(R.string.no, null)
                    .setCancelable(false)
                    .show()
                BackButtonBehavior.DOUBLE_TAP -> if (lastBackButtonPressTime < System.currentTimeMillis() - 2000) {
                    showSnack(this, R.string.double_tap_toast, Toast.LENGTH_SHORT)
                    lastBackButtonPressTime = System.currentTimeMillis()
                } else {
                    super.onBackPressed()
                }
                else -> super.onBackPressed()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: MessageEvent) {
        Log.d(TAG, "onEvent -> $event")
        val snackbar = showSnackbarAbovePlayer(event.message, Snackbar.LENGTH_LONG)
        val action = event.action
        if (action != null) {
            snackbar.setAction(getString(R.string.undo)) { v: View? -> action.run() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentHandler.handleNavIntent()
    }

    /**
     * Show a snackbar above the player
     * @param text
     * @param duration
     * @return
     */
    fun showSnackbarAbovePlayer(text: CharSequence?, duration: Int): Snackbar {
        return playerSheet.showSnackbarAbovePlayer(text, duration)
    }

    fun showSnackbarAbovePlayer(text: Int, duration: Int): Snackbar {
        return showSnackbarAbovePlayer(resources.getText(text), duration)
    }

    /**
     * Hardware keyboard support: custom key events are forwarded to PlaybackService
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return hardwareKeys.onKeyUp(keyCode, event) ?: super.onKeyUp(keyCode, event)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == Prefs.NOW_PLAYING_SCREEN_ID || key == Prefs.PREF_TOGGLE_ADD_CONTROLS
            || key == Prefs.PREF_ADAPTIVE_COLOR_APP
            || key == Prefs.APPBAR_MODE
            || key == Prefs.PREF_COLUMN_IN_LANDSCAPE /*|| Objects.equals(key, UserPreferences.PREF_SNOWFALL)*/) {
            postRecreate()
        } else if(key == ThemeConstants.TOGGLE_FULL_SCREEN){
            clearAllAppcompactActivities(true)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val MAIN_FRAGMENT_TAG = "main"
        const val PREF_NAME = "MainActivityPrefs"
        const val PREF_IS_FIRST_LAUNCH = "prefMainActivityIsFirstLaunch"
        const val EXTRA_FRAGMENT_TAG = "fragment_tag"
        const val EXTRA_FRAGMENT_ARGS = "fragment_args"
        const val EXTRA_FEED_ID = "fragment_feed_id"
        const val EXTRA_FEED = "fragment_feed"

        //when true, it is opened as a child fragment so back navigation works
        const val EXTRA_STARTED_FROM_SEARCH = "started_from_search"
        const val KEY_GENERATED_VIEW_ID = "generated_view_id"

        @JvmStatic
        fun getIntentToOpenFeedWithId(context: Context, feedId: Long): Intent {
            val intent = Intent(context.applicationContext, MainActivity::class.java)
            intent.putExtra(EXTRA_FEED_ID, feedId)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }

        @JvmStatic
        fun getIntentToOpenFeed(context: Context, feed: Feed?): Intent {
            val intent = Intent(context.applicationContext, MainActivity::class.java)
            intent.putExtra(EXTRA_FEED, feed)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }

        @JvmStatic
        fun onPanelCollapsed(activity: AppCompatActivity) {
            activity.setNavigationBarColor(activity.surfaceColor())
            val currentFragment = activity.supportFragmentManager.findFragmentByTag(
                MAIN_FRAGMENT_TAG
            )
            if (currentFragment is FeedItemlistFragment) {
                currentFragment.updateTint()
            } else {
                activity.setLightStatusBarAuto()
            }
            activity.setLightNavigationBarAuto()
        }
    }
}
