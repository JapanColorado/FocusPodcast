package allen.town.podcast.activity.main

import allen.town.focus_common.util.Timber
import allen.town.podcast.R
import allen.town.podcast.fragment.NavigationDrawerFragment
import android.app.Activity
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout

/**
 * Owns everything about MainActivity's navigation drawer: the [DrawerLayout] itself (absent in the
 * tablet/landscape layout, hence the nullability), the [ActionBarDrawerToggle] that every fragment
 * re-wires through [setupToolbarToggle], the drawer width computation, and the delayed attach of
 * [NavigationDrawerFragment]. It also holds the "do this once the drawer has finished closing"
 * bookkeeping: a pending action is reported to the owner through the `onDrawerClosed` callback
 * given at construction (which returns true when it consumed the event), and any remaining
 * [drawerCloseCallback] runs afterwards. It performs no fragment transactions itself apart from
 * attaching the drawer fragment.
 */
internal class MainNavDrawer(
    private val activity: AppCompatActivity,
    private val onDrawerClosed: () -> Boolean
) {
    private val drawerLayout: DrawerLayout? = activity.findViewById(R.id.drawer_layout)

    /** The drawer container view. Also used as a throwaway "any view" argument by the player sheet. */
    val navDrawerView: View = activity.findViewById(R.id.navDrawerFragment)

    private var drawerToggle: ActionBarDrawerToggle? = null

    /** Kept so the delayed nav-drawer attach can be cancelled when the activity goes away. */
    private var attachNavDrawer: Runnable? = null

    var drawerCloseCallback: (() -> Unit)? = null

    init {
        setNavDrawerSize()
    }

    /**
     * Load the drawer fragment with a delay so the UI shows first; too short a delay makes the
     * subscription screen animation skip or stutter.
     */
    fun scheduleAttach() {
        val attach = Runnable {
            Timber.v("post nav inti on ui thread")
            activity.supportFragmentManager.beginTransaction().replace(
                R.id.navDrawerFragment,
                NavigationDrawerFragment(),
                NavigationDrawerFragment.TAG
            ).commitAllowingStateLoss()
            Timber.v("nav inti finished")
        }
        attachNavDrawer = attach
        navDrawerView.postDelayed(attach, 1000)
    }

    val isDrawerOpen: Boolean
        get() = drawerLayout?.isDrawerOpen(navDrawerView) == true

    fun openDrawer() {
        drawerLayout?.openDrawer(navDrawerView)
    }

    fun closeDrawerNow() {
        drawerLayout?.closeDrawer(navDrawerView)
    }

    fun closeDrawer(callback: (() -> Unit)?) {
        if (isDrawerOpen) {
            callback?.run {
                drawerCloseCallback = this
            }
            drawerLayout?.closeDrawer(navDrawerView)
        } else {
            callback?.run {
                callback()
            }
        }
    }

    /**
     * Every fragment has its own toolbar; this wraps the setup
     * @param toolbar
     * @param displayUpArrow
     */
    fun setupToolbarToggle(toolbar: Toolbar, displayUpArrow: Boolean) {
        val drawerLayout = this.drawerLayout
        if (drawerLayout != null) { // Tablet layout does not have a drawer
            drawerToggle?.let { drawerLayout.removeDrawerListener(it) }
            val toggle = DrawerCloseToggle(
                activity, drawerLayout, toolbar,
                R.string.drawer_open, R.string.drawer_close
            )
            drawerToggle = toggle
            drawerLayout.addDrawerListener(toggle)
            toggle.syncState()
            //in the original logic, true meant the system handled the menu event
//            toggle.isDrawerIndicatorEnabled = !displayUpArrow
            toggle.isDrawerIndicatorEnabled = false
            toolbar.setNavigationIcon(if (displayUpArrow) R.drawable.ic_keyboard_backspace_black else R.drawable.ic_homepage)
            toggle.toolbarNavigationClickListener =
                View.OnClickListener { v: View? ->
                    if (displayUpArrow) activity.supportFragmentManager.popBackStack() else drawerLayout.openDrawer(
                        navDrawerView
                    )
                }
        } else if (!displayUpArrow) {
            toolbar.navigationIcon = null
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_keyboard_backspace_black)
            toolbar.setNavigationOnClickListener { v: View? -> activity.supportFragmentManager.popBackStack() }
        }
    }

    fun onPostCreate() {
        // Tablet layout does not have a drawer
        drawerToggle?.syncState()
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        // Tablet layout does not have a drawer
        drawerToggle?.onConfigurationChanged(newConfig)
        setNavDrawerSize()
    }

    fun onDestroy() {
        attachNavDrawer?.let { navDrawerView.removeCallbacks(it) }
        attachNavDrawer = null
        drawerToggle?.let { drawerLayout?.removeDrawerListener(it) }
    }

    fun onOptionsItemSelected(item: MenuItem): Boolean =
        drawerToggle?.onOptionsItemSelected(item) == true

    /**
     * Set the drawer width
     */
    private fun setNavDrawerSize() {
        val screenPercent =
            activity.resources.getInteger(R.integer.nav_drawer_screen_size_percent) * 0.01f
        val width = (screenWidth * screenPercent).toInt()
        val maxWidth = activity.resources.getDimension(R.dimen.nav_drawer_max_screen_size).toInt()
        navDrawerView.layoutParams.width = Math.min(width, maxWidth)
    }

    private val screenWidth: Int
        get() {
            val displayMetrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            return displayMetrics.widthPixels
        }

    internal inner class DrawerCloseToggle : ActionBarDrawerToggle {
        constructor(
            activity: Activity?,
            drawerLayout: DrawerLayout?,
            openDrawerContentDescRes: Int,
            closeDrawerContentDescRes: Int
        ) : super(activity, drawerLayout, openDrawerContentDescRes, closeDrawerContentDescRes)

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
        )

        override fun onDrawerClosed(drawerView: View) {
            super.onDrawerClosed(drawerView)
            val consumed = onDrawerClosed()
            val closeCallback = drawerCloseCallback
            if (!consumed && closeCallback != null) {
                closeCallback()
                drawerCloseCallback = null
            }
        }
    }
}
