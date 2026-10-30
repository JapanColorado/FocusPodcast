package dev.russell.yagi.fragment

import dev.russell.yagi.R
import dev.russell.yagi.core.feed.EpisodeFilterOptions
import dev.russell.yagi.core.storage.DBReader
import dev.russell.yagi.dialog.filter.EpisodeFilterSheet
import dev.russell.yagi.model.feed.FeedItem
import dev.russell.yagi.model.feed.FeedItemFilter
import android.content.Context
import android.os.Bundle
import android.view.*

/**
 * Like 'EpisodesFragment' except that it only shows new episodes and
 * supports swiping to mark as read.
 */
class EpisodesFragment : EpisodesListFragment(), EpisodeFilterSheet.Host {
    private var feedItemFilter = FeedItemFilter.unfiltered()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // a filter saved before the filter sheet may hold keys it no longer shows
        feedItemFilter = EpisodeFilterOptions.sanitize(FeedItemFilter(prefs.getString(PREF_FILTER, "")))
    }

    override val swipeTag
        get() = TAG
    override val prefName
        get() = PREF_NAME

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        emptyView.setIcon(R.drawable.ic_star)
        toolbar.setTitle(R.string.episodes_label)
        return root
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (!super.onOptionsItemSelected(item)) {
            if (item.itemId == R.id.filter_items) {
                showFilterDialog()
                return true
            }
            false
        } else {
            true
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.filter_items).isVisible = true
        menu.findItem(R.id.mark_all_read_item).isVisible = true
    }

    override fun onFragmentLoaded(episodes: List<FeedItem>) {
        super.onFragmentLoaded(episodes)
        val filterItem = toolbar.menu.findItem(R.id.filter_items)
        if (feedItemFilter.values.size > 0) {
            filterItem.setTitle(R.string.filtered_label)
            filterItem.setIcon(R.drawable.ic_filter_disable)
        } else {
            filterItem.setTitle(R.string.filter)
            filterItem.setIcon(R.drawable.ic_filter)
        }
    }

    private fun showFilterDialog() {
        if (childFragmentManager.findFragmentByTag(EpisodeFilterSheet.TAG) == null) {
            EpisodeFilterSheet.newAllEpisodesInstance(feedItemFilter)
                .show(childFragmentManager, EpisodeFilterSheet.TAG)
        }
    }

    override fun onEpisodeFilterChanged(filter: FeedItemFilter) {
        feedItemFilter = filter
        requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(PREF_FILTER, filter.values.joinToString(","))
            .apply()
        loadItems()
    }

    override fun shouldUpdatedItemRemainInList(item: FeedItem): Boolean {
        return !(feedItemFilter.isShowDownloaded && (!item.hasMedia() || item.media?.isDownloaded != true))
    }

    override fun loadData(): MutableList<FeedItem> {
        return DBReader.getRecentlyPublishedEpisodes(0, page * EPISODES_PER_PAGE, feedItemFilter)
    }

    override fun loadMoreData(): MutableList<FeedItem> {
        return DBReader.getRecentlyPublishedEpisodes(
            (page - 1) * EPISODES_PER_PAGE,
            EPISODES_PER_PAGE,
            feedItemFilter
        )
    }

    companion object {
        const val PREF_NAME = "pref_all_episodes_fragment"
        const val TAG = "EpisodesFragment"
        private const val PREF_FILTER = "filter"
    }
}