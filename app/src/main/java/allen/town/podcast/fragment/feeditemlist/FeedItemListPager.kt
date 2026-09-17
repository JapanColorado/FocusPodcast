package allen.town.podcast.fragment.feeditemlist

import allen.town.podcast.core.service.download.DownloadService
import allen.town.podcast.core.util.ui.ListFooterUtil
import allen.town.podcast.model.feed.Feed
import allen.town.podcast.view.StorePositionRecyclerView
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Owns the "load the next page" footer of [allen.town.podcast.fragment.FeedItemlistFragment]:
 * the [ListFooterUtil] over the `more_content_list_footer` layout, the scroll listener that reveals
 * the footer (and reserves bottom padding for it) once the list is scrolled to the bottom of a
 * paged feed, and the footer's loading spinner, which mirrors whether any feed download is running.
 * The actual page load is delegated to the `onLoadNextPage` callback.
 */
internal class FeedItemListPager(
    footerRoot: View,
    private val recyclerView: StorePositionRecyclerView,
    private val currentFeed: () -> Feed?,
    private val onLoadNextPage: (Feed) -> Unit
) {
    private val nextPageLoader = ListFooterUtil(footerRoot)

    init {
        nextPageLoader.setClickListener {
            val feed = currentFeed()
            if (feed != null) {
                onLoadNextPage(feed)
            }
        }
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, deltaX: Int, deltaY: Int) {
                super.onScrolled(view, deltaX, deltaY)
                val feed = currentFeed()
                val hasMorePages =
                    (feed != null) && feed.isPaged && (feed.nextPageLink != null)
                val pageLoaderVisible = recyclerView.isScrolledToBottom && hasMorePages
                nextPageLoader.root.visibility =
                    if (pageLoaderVisible) View.VISIBLE else View.GONE
                recyclerView.setPadding(
                    recyclerView.getPaddingLeft(), 0, recyclerView.getPaddingRight(),
                    if (pageLoaderVisible) nextPageLoader.root.measuredHeight else 0
                )
            }
        })
    }

    /** Mirrors the global feed-download state onto the footer. */
    fun updateLoadingIndicator() {
        if (!DownloadService.isDownloadingFeeds()) {
            nextPageLoader.root.visibility = View.GONE
        }
        nextPageLoader.setLoadingState(DownloadService.isDownloadingFeeds())
    }
}
