package allen.town.podcast.fragment.feeditemlist

import allen.town.focus_common.util.TopSnackbarUtil.showSnack
import allen.town.podcast.R
import allen.town.podcast.activity.MainActivity
import allen.town.podcast.core.service.download.DownloadService
import allen.town.podcast.core.util.menuhandler.MenuItemUtils
import allen.town.podcast.core.util.menuhandler.MenuItemUtils.UpdateRefreshMenuItemChecker
import allen.town.podcast.dialog.RenameItemDialog
import allen.town.podcast.fragment.FeedSettingsFragment
import allen.town.podcast.fragment.LocalSearchFragment
import allen.town.podcast.menuprocess.FeedMenuProcess
import allen.town.podcast.model.feed.Feed
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment

/**
 * Owns the toolbar of [allen.town.podcast.fragment.FeedItemlistFragment]: which menu items are
 * visible for the current feed, whether the refresh item shows a spinner (tracked in
 * [isUpdatingFeed], which the fragment compares against incoming download events), the menu item
 * clicks it can handle itself (rename, feed settings, and everything
 * [FeedMenuProcess] handles), and the three header buttons that open the filter, sort and search
 * UIs. It reads the feed through the `currentFeed` provider and navigates through the hosting
 * [MainActivity]; it holds no state beyond the refresh flag.
 */
internal class FeedItemListMenu(
    private val fragment: Fragment,
    private val toolbar: Toolbar,
    private val currentFeed: () -> Feed?
) {
    private val updateRefreshMenuItemChecker = UpdateRefreshMenuItemChecker {
        val feed = currentFeed()
        feed != null && DownloadService.isRunning()
                && DownloadService.isDownloadingFile(feed.getDownload_url())
    }

    var isUpdatingFeed = false
        private set

    /** True while the checker still sees a running feed download. */
    val isRefreshing: Boolean
        get() = updateRefreshMenuItemChecker.isRefreshing

    fun refreshToolbarState() {
        val feed = currentFeed() ?: return
        toolbar.menu.findItem(R.id.share_link_item).isVisible = feed.link != null
        toolbar.menu.findItem(R.id.visit_website_item).isVisible = feed.link != null
        toolbar.menu.findItem(R.id.feed_setting).isVisible = feed.isSubscribed
        toolbar.menu.findItem(R.id.rename_item).isVisible = feed.isSubscribed
        isUpdatingFeed = MenuItemUtils.updateRefreshMenuItem(
            toolbar.menu,
            R.id.refresh_item, updateRefreshMenuItemChecker
        )
        FeedMenuProcess.onPrepareOptionsMenu(toolbar.menu, feed)
    }

    fun onMenuItemClick(item: MenuItem): Boolean {
        val feed = currentFeed()
        if (feed == null) {
            showSnack(fragment.activity, R.string.please_wait_for_data, Toast.LENGTH_LONG)
            return true
        }
        val feedMenuHandled = FeedMenuProcess.onOptionsItemClicked(fragment.activity, item, feed)
        if (feedMenuHandled) {
            return true
        }
        val itemId = item.itemId
        if (itemId == R.id.rename_item) {
            RenameItemDialog(fragment.requireActivity(), feed).show()
            return true
        } else if (itemId == R.id.feed_setting) {
            val settingsFragment: FeedSettingsFragment = FeedSettingsFragment.newInstance(feed)
            (fragment.requireActivity() as MainActivity).loadChildFragment(settingsFragment)
            return true
        }
        return false
    }

    fun filterFeedItems() {
        if (currentFeed() == null) {
            showSnack(fragment.activity, R.string.please_wait_for_data, Toast.LENGTH_LONG)
            return
        }
        FeedMenuProcess.showFilterDialog(fragment.context, currentFeed())
    }

    fun sortFeedItems() {
        if (currentFeed() == null) {
            showSnack(fragment.activity, R.string.please_wait_for_data, Toast.LENGTH_LONG)
            return
        }
        FeedMenuProcess.showSortDialog(fragment.context, currentFeed())
    }

    fun searchFeedItems() {
        val feed = currentFeed()
        if (feed == null) {
            showSnack(fragment.activity, R.string.please_wait_for_data, Toast.LENGTH_LONG)
            return
        }
        (fragment.requireActivity() as MainActivity).loadChildFragment(
            LocalSearchFragment.newInstance(
                feed.id, feed.title
            )
        )
    }
}
