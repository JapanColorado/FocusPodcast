package allen.town.podcast.activity.main

import allen.town.podcast.R
import allen.town.podcast.activity.MainActivity
import allen.town.podcast.fragment.DiscoverFragment
import allen.town.podcast.fragment.DownloadPagerFragment
import allen.town.podcast.fragment.EpisodesFragment
import allen.town.podcast.fragment.FavoriteEpisodesFragment
import allen.town.podcast.fragment.FeedItemlistFragment
import allen.town.podcast.fragment.NavigationDrawerFragment.Companion.saveLastNavFragment
import allen.town.podcast.fragment.PlaybackHistoryFragment
import allen.town.podcast.fragment.PlaylistFragment
import allen.town.podcast.fragment.SubFeedsFragment
import allen.town.podcast.fragment.TransitionEffect
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import org.apache.commons.lang3.Validate

/**
 * Owns MainActivity's top-level fragment navigation: turning a nav-drawer tag or a feed id into a
 * fragment, replacing the single "main" fragment (clearing the back stack on the way), and pushing
 * child fragments with their transition animations. Because a replace made while the drawer is open
 * looks broken, a requested fragment is parked in [openPendingFragment]'s slot and the drawer is
 * asked to close; the drawer owner calls [openPendingFragment] once it finished closing. It never
 * touches the drawer directly - it only asks through the two callbacks given at construction.
 */
internal class MainFragmentNavigator(
    private val activity: AppCompatActivity,
    private val isDrawerOpen: () -> Boolean,
    private val closeDrawer: () -> Unit
) {
    private var needtoOpenFragmentLater: Fragment? = null

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
        saveLastNavFragment(activity, tag)
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
        saveLastNavFragment(activity, feedId.toString())
        loadFragment(fragment)
    }

    private fun loadFragment(fragment: Fragment) {
        if (isDrawerOpen()) {
            needtoOpenFragmentLater = fragment
            closeDrawer()
        } else {
            loadFragmentInner(fragment)
        }
    }

    /**
     * Called by the drawer once it finished closing.
     * @return true when a parked fragment was waiting and has now been committed.
     */
    fun openPendingFragment(): Boolean {
        val pendingFragment = needtoOpenFragmentLater ?: return false
        loadFragmentInner(pendingFragment)
        return true
    }

    private fun loadFragmentInner(fragment: Fragment) {
        val fragmentManager = activity.supportFragmentManager
        // clear back stack
        repeat(fragmentManager.backStackEntryCount) {
            fragmentManager.popBackStack()
        }
        val t = fragmentManager.beginTransaction()
        //without this animation, tapping a feed flickers
        t.setCustomAnimations(
            R.anim.retro_fragment_open_enter,
            R.anim.retro_fragment_open_exit,
            R.anim.retro_fragment_close_enter,
            R.anim.retro_fragment_close_exit
        ).replace(R.id.main_view, fragment, MainActivity.MAIN_FRAGMENT_TAG)
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
    fun loadChildFragment(fragment: Fragment, transition: TransitionEffect?) {
        Validate.notNull(fragment)
        val fragmentManager = activity.supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
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
        fragmentManager.findFragmentByTag(MainActivity.MAIN_FRAGMENT_TAG)
            ?.let { transaction.hide(it) }
        transaction
            .add(R.id.main_view, fragment, MainActivity.MAIN_FRAGMENT_TAG)
            .addToBackStack(null)
            .commit()

        /*if (drawerLayout != null) { // Tablet layout does not have a drawer
            drawerLayout.closeDrawer(navDrawer);
        }*/
    }

    private companion object {
        private const val TAG = "MainActivity"
    }
}
