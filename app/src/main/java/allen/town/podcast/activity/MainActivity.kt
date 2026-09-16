package allen.town.podcast.activity

import android.annotation.SuppressLint
import allen.town.focus_common.extensions.notificationRequestCode
import allen.town.focus_common.extensions.requestNotificationPermission
import allen.town.focus_common.extensions.setLightNavigationBarAuto
import allen.town.focus_common.extensions.setLightStatusBarAuto
import allen.town.focus_common.extensions.setNavigationBarColor
import allen.town.focus_common.extensions.surfaceColor
import allen.town.focus_common.util.BasePreferenceUtil.libraryCategory
import allen.town.focus_common.util.BasePreferenceUtil.materialYou
import allen.town.focus_common.util.RetroUtil
import allen.town.focus_common.util.Timber
import allen.town.focus_common.util.TopSnackbarUtil.showSnack
import allen.town.focus_common.views.AccentMaterialDialog
import allen.town.podcast.BuildConfig
import allen.town.podcast.MyApp.Companion.instance
import allen.town.podcast.R
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.pref.Prefs.BackButtonBehavior
import allen.town.podcast.core.receiver.MediaButtonReceiver
import allen.town.podcast.core.service.playback.PlaybackService
import allen.town.podcast.core.storage.DBTasks
import allen.town.podcast.core.util.StorageUtils
import allen.town.podcast.core.util.download.AutoUpdateManager
import allen.town.podcast.event.MessageEvent
import allen.town.podcast.fragment.AudioPlayerFragment
import allen.town.podcast.fragment.DiscoverFragment
import allen.town.podcast.fragment.DownloadPagerFragment
import allen.town.podcast.fragment.EpisodesFragment
import allen.town.podcast.fragment.FavoriteEpisodesFragment
import allen.town.podcast.fragment.FeedItemlistFragment
import allen.town.podcast.fragment.LocalSearchFragment
import allen.town.podcast.fragment.NavigationDrawerFragment
import allen.town.podcast.fragment.NavigationDrawerFragment.Companion.getLastNavFragment
import allen.town.podcast.fragment.NavigationDrawerFragment.Companion.saveLastNavFragment
import allen.town.podcast.fragment.PlaybackHistoryFragment
import allen.town.podcast.fragment.PlaylistFragment
import allen.town.podcast.fragment.SubFeedsFragment
import allen.town.podcast.fragment.TransitionEffect
import allen.town.podcast.model.feed.Feed
import allen.town.podcast.playback.LibraryViewModel
import allen.town.podcast.playback.getSelectedAudioPlayerFragment
import allen.town.podcast.playback.onPaletteColorChanged
import allen.town.podcast.pref.PreferenceUpgrader
import allen.town.podcast.ui.startintent.MainActivityStarter
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool
import code.name.monkey.appthemehelper.ThemeStore.Companion.accentColor
import code.name.monkey.appthemehelper.constants.ThemeConstants
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.Validate
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


/**
 * The activity that is shown when the user launches the app.
 */
class MainActivity : SimpleToolbarActivity(), OnSharedPreferenceChangeListener {
    private var drawerLayout: DrawerLayout? = null

    /** Kept so the delayed nav-drawer attach can be cancelled when the activity goes away. */
    private var attachNavDrawer: Runnable? = null
    private var drawerToggle: ActionBarDrawerToggle? = null
    private lateinit var navDrawer: View
    var bottomSheet: BottomSheetBehavior<View>? = null
        private set
    private var lastBackButtonPressTime: Long = 0
    val recycledViewPool = RecycledViewPool()
    private val lastTheme = 0

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
        drawerLayout = findViewById(R.id.drawer_layout)
        navDrawer = findViewById(R.id.navDrawerFragment)
        setNavDrawerSize()
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
                    loadFeedFragmentById(lastFragment!!.toInt().toLong(), null)
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


        //load this fragment with a delay so the UI shows first; too short a delay makes the subscription screen animation skip or stutter
        attachNavDrawer = Runnable {
            Timber.v("post nav inti on ui thread")
            fm.beginTransaction().replace(
                R.id.navDrawerFragment,
                NavigationDrawerFragment(),
                NavigationDrawerFragment.TAG
            ).commitAllowingStateLoss()
            Timber.v("nav inti finished")
        }
        navDrawer.postDelayed(attachNavDrawer, 1000)



        checkFirstLaunch()
        PreferenceUpgrader.checkUpgrades(this)
        this.bottomSheet = BottomSheetBehavior.from(findViewById<View>(R.id.audioplayerFragment))
        bottomSheet!!.setPeekHeight(resources.getDimension(R.dimen.external_player_height).toInt())
        bottomSheet!!.setHideable(false)
        bottomSheet!!.setBottomSheetCallback(bottomSheetCallback)
        libraryViewModel = ViewModelProvider(this).get(
            LibraryViewModel::class.java
        )
        updateColor()

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

    private fun requestPhonePermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                PERMISSIONS_REQUEST_PHONE
            )
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

    private var libraryViewModel: LibraryViewModel? = null

    /**
     * Album cover color changed
     */
    private fun onPaletteColorChanged() {
        if (bottomSheet!!.state == BottomSheetBehavior.STATE_EXPANDED) {
            this.onPaletteColorChanged(paletteColor)
        }
    }

    private var paletteColor = Color.WHITE
    private fun updateColor() {
        libraryViewModel!!.paletteColor.observe(this) { color: Int ->
            paletteColor = color
            onPaletteColorChanged()
        }
    }

    private val bottomSheetCallback: BottomSheetCallback = object : BottomSheetCallback() {
        /**
         * Called when the state changes
         * @param view
         * @param state
         */
        override fun onStateChanged(view: View, state: Int) {
            if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                onSlide(view, 0.0f)
                onPanelCollapsed(this@MainActivity)
            } else if (state == BottomSheetBehavior.STATE_EXPANDED) {
                onSlide(view, 1.0f)
                onPaletteColorChanged()
            }
        }

        /**
         * Called while sliding
         * @param view
         * @param slideOffset
         */
        override fun onSlide(view: View, slideOffset: Float) {
            val audioPlayer = supportFragmentManager
                .findFragmentByTag(AudioPlayerFragment.TAG) as AudioPlayerFragment? ?: return
            if (slideOffset == 0.0f) { //STATE_COLLAPSED
                audioPlayer.scrollToPage(AudioPlayerFragment.POS_COVER)
            }
            val condensedSlideOffset = Math.max(0.0f, Math.min(0.2f, slideOffset - 0.2f)) / 0.2f
            audioPlayer.externalPlayerHolder.alpha = 1 - condensedSlideOffset
            audioPlayer.externalPlayerHolder.visibility =
                if (condensedSlideOffset > 0.99f) View.GONE else View.VISIBLE
        }
    }

    /**
     * Every fragment has its own toolbar; this wraps the setup
     * @param toolbar
     * @param displayUpArrow
     */
    fun setupToolbarToggle(toolbar: Toolbar, displayUpArrow: Boolean) {
        if (drawerLayout != null) { // Tablet layout does not have a drawer
            if (drawerToggle != null) {
                drawerLayout!!.removeDrawerListener(drawerToggle!!)
            }
            drawerToggle = DrawerCloseToggle(
                this, drawerLayout, toolbar,
                R.string.drawer_open, R.string.drawer_close
            )
            drawerLayout!!.addDrawerListener(drawerToggle!!)
            drawerToggle!!.syncState()
            //in the original logic, true meant the system handled the menu event
//            drawerToggle!!.isDrawerIndicatorEnabled = !displayUpArrow
            drawerToggle!!.isDrawerIndicatorEnabled = false
            toolbar.setNavigationIcon(if (displayUpArrow) R.drawable.ic_keyboard_backspace_black else R.drawable.ic_homepage)
            drawerToggle!!.toolbarNavigationClickListener =
                View.OnClickListener { v: View? ->
                    if (displayUpArrow) supportFragmentManager.popBackStack() else drawerLayout!!.openDrawer(
                        navDrawer
                    )
                }
        } else if (!displayUpArrow) {
            toolbar.navigationIcon = null
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_keyboard_backspace_black)
            toolbar.setNavigationOnClickListener { v: View? -> supportFragmentManager.popBackStack() }
        }
    }

    internal inner class DrawerCloseToggle : ActionBarDrawerToggle {
        constructor(
            activity: Activity?,
            drawerLayout: DrawerLayout?,
            openDrawerContentDescRes: Int,
            closeDrawerContentDescRes: Int
        ) : super(activity, drawerLayout, openDrawerContentDescRes, closeDrawerContentDescRes) {
        }

        constructor(
            activity: Activity?,
            drawerLayout: DrawerLayout?,
            toolbar: Toolbar?,
            openDrawerContentDescRes: Int,
            closeDrawerContentDescRes: Int
        ) : super(
            activity,
            drawerLayout,
            toolbar,
            openDrawerContentDescRes,
            closeDrawerContentDescRes
        ) {
        }

        override fun onDrawerClosed(drawerView: View) {
            super.onDrawerClosed(drawerView)
            if (needtoOpenFragmentLater != null) {
                loadFragmentInner(needtoOpenFragmentLater!!)
            } else if (drawerCloseCallback != null) {
                drawerCloseCallback!!();
                drawerCloseCallback = null;
            }
        }
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
        get() = drawerLayout != null && navDrawer != null && drawerLayout!!.isDrawerOpen(
            navDrawer!!
        )


    fun setPlayerVisible(visible: Boolean) {
        if (visible) {
            //the first argument is unused but must not be null, so anything is passed
            bottomSheetCallback.onStateChanged(navDrawer, bottomSheet!!.state) // Update toolbar visibility
        } else {
            bottomSheet!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
        }

        val mainView = findViewById<FragmentContainerView>(R.id.main_view)
        val params = mainView.layoutParams as MarginLayoutParams
        params.setMargins(
            0,
            0,
            0,
            if (visible) resources.getDimension(R.dimen.external_player_height).toInt() else 0
        )
        mainView.layoutParams = params
        findViewById<View>(R.id.audioplayerFragment).visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Load a fragment by tag. This replaces, so the stack only ever holds one fragment.
     * @param tag
     * @param args
     */
    fun loadFragment(tag: String?, args: Bundle?) {
        var tag = tag
        var args = args
        Log.d(TAG, "loadFragment -> tag: $tag args: $args")
        val fragment: Fragment
        when (tag) {
            PlaylistFragment.TAG -> fragment = PlaylistFragment()
            EpisodesFragment.TAG -> fragment = EpisodesFragment()
            DownloadPagerFragment.TAG -> fragment = DownloadPagerFragment()
            PlaybackHistoryFragment.TAG -> fragment = PlaybackHistoryFragment()
            DiscoverFragment.TAG -> fragment = DiscoverFragment()
            SubFeedsFragment.TAG -> fragment =
                SubFeedsFragment()
            FavoriteEpisodesFragment.TAG -> fragment = FavoriteEpisodesFragment()
            else -> {
                // default to the queue
                fragment = PlaylistFragment()
                tag = PlaylistFragment.TAG
                args = null
            }
        }
        if (args != null) {
            fragment.arguments = args
        }
        saveLastNavFragment(this, tag)
        loadFragment(fragment)
    }

    /**
     * Load a subscription item fragment by feedId. This replaces, so the stack only ever holds one fragment.
     * @param feedId
     * @param args
     */
    fun loadFeedFragmentById(feedId: Long, args: Bundle?) {
        val fragment: Fragment = FeedItemlistFragment.newInstance(feedId)
        if (args != null) {
            fragment.arguments = args
        }
        saveLastNavFragment(this, feedId.toString())
        loadFragment(fragment)
    }

    private var needtoOpenFragmentLater: Fragment? = null
    private fun loadFragment(fragment: Fragment) {
        if (isDrawerOpen) {
            needtoOpenFragmentLater = fragment
            drawerLayout!!.closeDrawer(navDrawer!!)
        } else {
            loadFragmentInner(fragment)
        }
    }

    private fun loadFragmentInner(fragment: Fragment) {
        val fragmentManager = supportFragmentManager
        // clear back stack
        for (i in 0 until fragmentManager.backStackEntryCount) {
            fragmentManager.popBackStack()
        }
        val t = fragmentManager.beginTransaction()
        //without this animation, tapping a feed flickers
        t.setCustomAnimations(
            R.anim.retro_fragment_open_enter,
            R.anim.retro_fragment_open_exit,
            R.anim.retro_fragment_close_enter,
            R.anim.retro_fragment_close_exit
        ).replace(R.id.main_view, fragment, MAIN_FRAGMENT_TAG)
        fragmentManager.popBackStack()
        // TODO: we have to allow state loss here
        // since this function can get called from an AsyncTask which
        // could be finishing after our app has already committed state
        // and is about to get shutdown.  What we *should* do is
        // not commit anything in an AsyncTask, but that's a bigger
        // change than we want now.
        t.commitAllowingStateLoss()
        needtoOpenFragmentLater = null
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
        Validate.notNull(fragment)
        val transaction = supportFragmentManager.beginTransaction()
        when (transition) {
            TransitionEffect.FADE -> transaction.setCustomAnimations(
                R.anim.fade_in,
                R.anim.fade_out
            )
            TransitionEffect.SLIDE -> transaction.setCustomAnimations(
                R.anim.slide_right_in,
                R.anim.slide_left_out,
                R.anim.slide_left_in,
                R.anim.slide_right_out
            )
            TransitionEffect.FADE_AND_SCALE -> transaction.setCustomAnimations(
                R.anim.retro_fragment_open_enter,
                R.anim.retro_fragment_open_exit,
                R.anim.retro_fragment_close_enter,
                R.anim.retro_fragment_close_exit
            )
            else -> transaction.setCustomAnimations(
                R.anim.fade_in,
                R.anim.fade_out
            )
        }
        transaction
            .hide(supportFragmentManager.findFragmentByTag(MAIN_FRAGMENT_TAG)!!)
            .add(R.id.main_view, fragment, MAIN_FRAGMENT_TAG)
            .addToBackStack(null)
            .commit()

        /*if (drawerLayout != null) { // Tablet layout does not have a drawer
            drawerLayout.closeDrawer(navDrawer);
        }*/
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (drawerToggle != null) { // Tablet layout does not have a drawer
            drawerToggle!!.syncState()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (drawerToggle != null) { // Tablet layout does not have a drawer
            drawerToggle!!.onConfigurationChanged(newConfig)
        }
        setNavDrawerSize()
    }

    /**
     * Set the drawer width
     */
    private fun setNavDrawerSize() {
        val screenPercent = resources.getInteger(R.integer.nav_drawer_screen_size_percent) * 0.01f
        val width = (screenWidth * screenPercent).toInt()
        val maxWidth = resources.getDimension(R.dimen.nav_drawer_max_screen_size).toInt()
        navDrawer!!.layoutParams.width = Math.min(width, maxWidth)
    }

    private val screenWidth: Int
        private get() {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            return displayMetrics.widthPixels
        }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (bottomSheet!!.state == BottomSheetBehavior.STATE_EXPANDED) {
            //re-open
            bottomSheetCallback.onSlide(navDrawer, 1.0f)
        }
    }

    override fun onResume() {
        super.onResume()
        StorageUtils.checkStorageAvailability(this)
        handleNavIntent()
    }

    override fun onStop() {
        Timber.d("onStop")
        super.onStop()
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        super.onDestroy()
        attachNavDrawer?.let { navDrawer.removeCallbacks(it) }
        attachNavDrawer = null
        if (drawerLayout != null) {
            drawerLayout!!.removeDrawerListener(drawerToggle!!)
        }
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
        return if (drawerToggle != null && drawerToggle!!.onOptionsItemSelected(item)) { // Tablet layout does not have a drawer
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

    var drawerCloseCallback: (() -> Unit)? = null

    fun closeDrawer(callback: (() -> Unit)?) {
        if (isDrawerOpen) {
            callback?.run {
                drawerCloseCallback = this
            }
            drawerLayout!!.closeDrawer(navDrawer)
        } else {
            callback?.run {
                callback()
            }
        }
    }

    override fun onBackPressed() {
        if (isDrawerOpen) {
            drawerLayout!!.closeDrawer(navDrawer)
        } else if (bottomSheet!!.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheet!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
        } else if (supportFragmentManager.backStackEntryCount != 0) {
            super.onBackPressed()
        } else {
            when (Prefs.backButtonBehavior) {
                BackButtonBehavior.OPEN_DRAWER -> if (drawerLayout != null) { // Tablet layout does not have drawer
                    drawerLayout!!.openDrawer(navDrawer)
                }
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
        if (event.action != null) {
            snackbar.setAction(getString(R.string.undo)) { v: View? -> event.action!!.run() }
        }
    }

    private fun handleNavIntent() {
        val intent = intent
        if (intent.hasExtra(EXTRA_FEED_ID) || intent.hasExtra(EXTRA_FRAGMENT_TAG)
            || intent.hasExtra(EXTRA_FEED)
        ) {
            Log.d(TAG, "handle NavIntent()")
            val tag = intent.getStringExtra(EXTRA_FRAGMENT_TAG)
            val args = intent.getBundleExtra(EXTRA_FRAGMENT_ARGS)

            val feedId = intent.getLongExtra(EXTRA_FEED_ID, 0)
            val feed = intent.getParcelableExtra<Feed>(EXTRA_FEED)
            if (tag != null) {
                loadFragment(tag, args)
            } else if (feedId > 0) {
                if (intent.getBooleanExtra(EXTRA_STARTED_FROM_SEARCH, false)) {
                    loadChildFragment(FeedItemlistFragment.newInstance(feedId))
                } else {
                    loadFeedFragmentById(feedId, args)
                }
            } else if (feed != null) {
                loadChildFragment(FeedItemlistFragment.newInstance(feed))
            }
            bottomSheet!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
        } else if (intent.getBooleanExtra(MainActivityStarter.EXTRA_OPEN_PLAYER, false)) {
            bottomSheet!!.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheetCallback.onSlide(navDrawer, 1.0f)
        } else if (Intent.ACTION_VIEW == intent.action) {
            handleDeeplink(intent.data)
        }
        // to avoid handling the intent twice when the configuration changes
        setIntent(Intent(this@MainActivity, MainActivity::class.java))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavIntent()
    }

    /**
     * Show a snackbar above the player
     * @param text
     * @param duration
     * @return
     */
    fun showSnackbarAbovePlayer(text: CharSequence?, duration: Int): Snackbar {
        val s: Snackbar
        if (bottomSheet!!.state == BottomSheetBehavior.STATE_COLLAPSED) {
            s = Snackbar.make(findViewById(R.id.main_view), text!!, duration)
            if (findViewById<View>(R.id.audioplayerFragment).visibility == View.VISIBLE) {
                s.anchorView = findViewById(R.id.audioplayerFragment)
            }
        } else {
            s = Snackbar.make(findViewById(android.R.id.content), text!!, duration)
        }
        if (!materialYou) {
            s.setActionTextColor(accentColor(this))
        }
        s.show()
        return s
    }

    fun showSnackbarAbovePlayer(text: Int, duration: Int): Snackbar {
        return showSnackbarAbovePlayer(resources.getText(text), duration)
    }

    /**
     * Handles the deep link incoming via App Actions.
     * Performs an in-app search or opens the relevant feature of the app
     * depending on the query.
     *
     * @param uri incoming deep link
     */
    private fun handleDeeplink(uri: Uri?) {
        if (uri == null || uri.path == null) {
            return
        }
        Log.d(TAG, "handle deeplink -> $uri")
        when (uri.path) {
            "/deeplink/search" -> {
                val query = uri.getQueryParameter("query") ?: return
                loadChildFragment(LocalSearchFragment.newInstance(query))
            }
            "/deeplink/main" -> {
                val feature = uri.getQueryParameter("page") ?: return
                when (feature) {
                    "downloads" -> loadFragment(DownloadPagerFragment.TAG, null)
                    "history" -> loadFragment(PlaybackHistoryFragment.TAG, null)
                    "episodes" -> loadFragment(EpisodesFragment.TAG, null)
                    "playlist" -> loadFragment(PlaylistFragment.TAG, null)
                    "subscriptions" -> loadFragment(SubFeedsFragment.TAG, null)
                    "favorite" -> loadFragment(FavoriteEpisodesFragment.TAG, null)
                    else -> {
                        showSnack(
                            this, getString(R.string.app_action_not_found, feature),
                            Toast.LENGTH_LONG
                        )
                        return
                    }
                }
            }
            else -> {}
        }
    }

    /**
     * Hardware keyboard support: custom key events are forwarded to PlaybackService
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val currentFocus = currentFocus
        if (currentFocus is EditText) {
            return super.onKeyUp(keyCode, event)
        }
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        var customKeyCode: Int? = null
        EventBus.getDefault().post(event)
        when (keyCode) {
            KeyEvent.KEYCODE_P -> customKeyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_COMMA -> customKeyCode =
                KeyEvent.KEYCODE_MEDIA_REWIND
            KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_PERIOD -> customKeyCode =
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_W -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
                )
                return true
            }
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_S -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
                )
                return true
            }
            KeyEvent.KEYCODE_M -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI
                )
                return true
            }
        }
        if (customKeyCode != null) {
            val intent = Intent(this, PlaybackService::class.java)
            intent.putExtra(MediaButtonReceiver.EXTRA_KEYCODE, customKeyCode)
            ContextCompat.startForegroundService(this, intent)
            return true
        }
        return super.onKeyUp(keyCode, event)
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
        const val PERMISSIONS_REQUEST_PHONE = 1005

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