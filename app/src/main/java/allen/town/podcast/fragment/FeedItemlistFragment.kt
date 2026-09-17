package allen.town.podcast.fragment

import allen.town.podcast.common.util.DoubleClickBackToContentTopListener
import allen.town.podcast.common.util.MenuIconUtil.showToolbarMenuIcon
import allen.town.podcast.common.util.StatusBarUtils.setPaddingStatusBarTop
import allen.town.podcast.common.util.Timber
import allen.town.podcast.R
import allen.town.podcast.activity.MainActivity
import allen.town.podcast.activity.MainActivity.Companion.onPanelCollapsed
import allen.town.podcast.adapter.EpisodeItemListAdapter
import allen.town.podcast.adapter.MultiSelectAdapter.OnSelectModeListener
import allen.town.podcast.core.event.DownloadEvent
import allen.town.podcast.core.feed.FeedEvent
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.storage.DBTasks
import allen.town.podcast.core.util.FeedItemUtil
import allen.town.podcast.core.util.menuhandler.MenuItemUtils
import allen.town.podcast.databinding.FeedItemListFragmentBinding
import allen.town.podcast.event.*
import allen.town.podcast.event.playback.PlaybackPositionEvent
import allen.town.podcast.fragment.feeditemlist.FeedItemListHeader
import allen.town.podcast.fragment.feeditemlist.FeedItemListLoader
import allen.town.podcast.fragment.feeditemlist.FeedItemListMenu
import allen.town.podcast.fragment.feeditemlist.FeedItemListMultiSelect
import allen.town.podcast.fragment.feeditemlist.FeedItemListPager
import allen.town.podcast.fragment.swipeactions.SwipeActions
import allen.town.podcast.menuprocess.FeedItemMenuProcess
import allen.town.podcast.model.feed.Feed
import allen.town.podcast.util.SkeletonRecyclerDelay
import allen.town.podcast.view.FeedItemListToolbarIconTintHelper
import allen.town.podcast.view.StorePositionRecyclerView
import allen.town.podcast.viewholder.EpisodeItemViewHolder
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import allen.town.podcast.theme.util.scroll.ThemedFastScroller.create
import com.faltenreich.skeletonlayout.Skeleton
import com.faltenreich.skeletonlayout.applySkeleton
import com.google.android.material.appbar.AppBarLayout
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Displays a list of FeedItems.
 *
 * The screen is assembled from five collaborators in `fragment.feeditemlist`:
 * [FeedItemListLoader] owns the feed and how it is fetched, [FeedItemListHeader] the collapsing
 * header and series-info panel, [FeedItemListMenu] the toolbar plus the filter/sort/search
 * buttons, [FeedItemListPager] the "next page" footer and [FeedItemListMultiSelect] the batch
 * action mode. The fragment keeps the lifecycle, the RecyclerView/adapter and all EventBus
 * subscribers, which must stay here because this is the object registered with EventBus.
 */
class FeedItemlistFragment() : Fragment(), OnItemClickListener, Toolbar.OnMenuItemClickListener,
    OnSelectModeListener , DoubleClickBackToContentTopListener.IBackToContentTopView {
    private lateinit var binding: FeedItemListFragmentBinding
    private var adapter: FeedItemListAdapter? = null
    private var swipeActions: SwipeActions? = null
    private lateinit var recyclerView: StorePositionRecyclerView
    private lateinit var toolbar: Toolbar
    private var displayUpArrow = false
    private lateinit var skeleton: Skeleton
    private val uiHandler = Handler(Looper.getMainLooper())
    private var iconTintManager: FeedItemListToolbarIconTintHelper? = null
    private lateinit var appBar: AppBarLayout
    private lateinit var skeletonRecyclerDelay: SkeletonRecyclerDelay

    private lateinit var loader: FeedItemListLoader
    private lateinit var header: FeedItemListHeader
    private lateinit var menuController: FeedItemListMenu
    private lateinit var pager: FeedItemListPager

    private val feed: Feed?
        get() = loader.feed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //keep this line: otherwise items disappear when switching to dark mode on this screen
//        setRetainInstance(true);
        loader = FeedItemListLoader(
            { context },
            { header.initDetailView() },
            { header.showFeedUrlFailure() }
        )
        val args = requireArguments()
        loader.feedID = args.getLong(ARGUMENT_FEED_ID)
        loader.feed = args.getParcelable(ARGUMENT_FEED)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FeedItemListFragmentBinding.inflate(inflater, container, false)
        val root = binding.root
        binding.filterItems.setOnClickListener { menuController.filterFeedItems() }
        binding.sortItems.setOnClickListener { menuController.sortFeedItems() }
        binding.actionSearch.setOnClickListener { menuController.searchFeedItems() }
        toolbar = binding.toolbar
        toolbar.inflateMenu(R.menu.feedlist)
        showToolbarMenuIcon(toolbar)
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setOnClickListener(DoubleClickBackToContentTopListener(this))
        menuController = FeedItemListMenu(this, toolbar) { loader.feed }
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        }
        (requireActivity() as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)
        menuController.refreshToolbarState()
        recyclerView = binding.recyclerView
        recyclerView.setRecycledViewPool((requireActivity() as MainActivity).recycledViewPool)
        create(recyclerView)
        skeleton = recyclerView.applySkeleton(R.layout.item_small_recyclerview_skeleton, 15)
        skeletonRecyclerDelay = SkeletonRecyclerDelay(skeleton, recyclerView)
        skeletonRecyclerDelay.showSkeleton()
        header = FeedItemListHeader(this, root, binding.filterItems, { loader.feed }) {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack()
            } else {
                (requireActivity() as MainActivity).loadFragment(
                    SubFeedsFragment.TAG,
                    null
                )
            }
        }
        appBar = binding.appBar
        val collapsingToolbar = binding.collapsingToolbar

        //top padding is required
        setPaddingStatusBarTop(requireActivity(), toolbar)
        setPaddingStatusBarTop(requireActivity(), header.headerView)
        val tintManager =
            FeedItemListToolbarIconTintHelper(requireContext(), toolbar, collapsingToolbar)
        iconTintManager = tintManager
        tintManager.updateTint()
        appBar.addOnOffsetChangedListener(tintManager)
        pager = FeedItemListPager(
            root.findViewById(R.id.more_content_list_footer),
            recyclerView,
            { loader.feed }
        ) { feed -> DBTasks.loadNextPageOfFeed(getActivity(), feed, false) }
        EventBus.getDefault().register(this)
        val swipeRefreshLayout = binding.swipeRefresh
        swipeRefreshLayout.setDistanceToTriggerSync(resources.getInteger(R.integer.swipe_refresh_distance))
        swipeRefreshLayout.setOnRefreshListener {
            if (feed != null) {
                DBTasks.forceRefreshFeed(requireContext(), feed, true)
            } else {
                Timber.e("not going to refresh feed becasue is null")
            }

            uiHandler.postDelayed(
                Runnable { swipeRefreshLayout.setRefreshing(false) },
                getResources().getInteger(R.integer.swipe_to_refresh_duration_in_ms).toLong()
            )
        }
        loadItems()
//        loadAd(requireActivity(), true)
        return root
    }

    fun updateTint() {
        iconTintManager?.updateTint()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        //isHidden is only false once the new fragment is added
        Log.d(
            TAG,
            "==>onHiddenChanged,isHidden=" + hidden + ",getUserVisibleHint=" + userVisibleHint
        )
        if (hidden) {
//            if (isPageResume && getUserVisibleHint()) {
//                onPagePause();
//            }
            //restore the status bar color when leaving the screen
            onPanelCollapsed(requireActivity() as AppCompatActivity)
        } else {
//            if (!isPageResume && getUserVisibleHint()) {
//                onPageResume();
//            }
            //reset the status bar color when entering the screen
            iconTintManager?.updateTint()
        }
        super.onHiddenChanged(hidden)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        uiHandler.removeCallbacksAndMessages(null)
        //restore when the screen is closed
        onPanelCollapsed(requireActivity() as AppCompatActivity)
        EventBus.getDefault().unregister(this)
        loader.dispose()
        adapter?.endSelectMode()
        // displayList() only attaches the adapter when it is null; a surviving fragment instance
        // with a new RecyclerView would otherwise show an empty list forever
        adapter = null
//        loadAd(requireActivity(), true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val horizontalSpacing =
            resources.getDimension(R.dimen.additional_horizontal_spacing).toInt()
        header.setHorizontalSpacing(horizontalSpacing)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return menuController.onMenuItemClick(item)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val adapter = this.adapter ?: return super.onContextItemSelected(item)
        val selectedItem = adapter.longPressedItem
        if (selectedItem == null) {
            Log.i(TAG, "Selected item at current position was null, ignoring selection")
            return super.onContextItemSelected(item)
        }
        return if (adapter.onContextItemSelected(item)) {
            true
        } else FeedItemMenuProcess.onMenuItemClicked(this, item.itemId, selectedItem)
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        val feed = this.feed
        if (adapter == null || feed == null) {
            return
        }
        val ids = FeedItemUtil.getIds(feed.items)
        (requireActivity() as MainActivity).loadChildFragment(
            FeedItemsViewPagerFragment.Companion.newInstance(
                ids,
                position
            )
        )
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: FeedEvent) {
        if (event.feedId == loader.feedID) {
            loadItems()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        val feed = this.feed
        if (feed == null || feed.items == null) {
            return
        }
        val adapter = this.adapter
        if (adapter == null) {
            loadItems()
            return
        }
        var i = 0
        val size = event.items.size
        while (i < size) {
            val item = event.items[i]
            val pos = FeedItemUtil.indexOfItemWithId(feed.items, item.id)
            if (pos >= 0) {
                feed.items.removeAt(pos)
                feed.items.add(pos, item)
                adapter.notifyItemChangedCompat(pos)
            }
            i++
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: DownloadEvent) {
        val update = event.update
        if (event.hasChangedFeedUpdateStatus(menuController.isUpdatingFeed)) {
            updateSyncProgressBarVisibility()
        }
        val currentFeed = feed
        val adapter = this.adapter
        if ((adapter != null) && (update.mediaIds.size > 0) && (currentFeed != null)) {
            for (mediaId: Long in update.mediaIds) {
                val pos = FeedItemUtil.indexOfItemWithMediaId(currentFeed.items, mediaId)
                if (pos >= 0) {
                    adapter.notifyItemChangedCompat(pos)
                }
            }
        }
        loader.refreshDownloadingFeedState { updateUi() }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        val adapter = this.adapter ?: return
        for (i in 0 until adapter.itemCount) {
            val holder =
                recyclerView.findViewHolderForAdapterPosition(i) as? EpisodeItemViewHolder
            if (holder != null && holder.isCurrentlyPlayingItem) {
                holder.notifyPlaybackPositionUpdated(event)
                break
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun favoritesChanged(event: FavoritesEvent?) {
        updateUi()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onQueueChanged(event: QueueEvent?) {
        updateUi()
    }

    override fun onStartSelectMode() {
        swipeActions?.detach()
        toolbar.visibility = View.GONE
        menuController.refreshToolbarState()
    }

    override fun onEndSelectMode() {
        swipeActions?.attachTo(recyclerView)
        toolbar.visibility = View.VISIBLE
    }

    private fun updateUi() {
        loadItems()
        updateSyncProgressBarVisibility()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent?) {
        updateUi()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: UnreadItemsUpdateEvent?) {
        updateUi()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent) {
        if (feed != null && event.contains(feed)) {
            updateUi()
        }
    }

    private fun updateSyncProgressBarVisibility() {
        if (menuController.isUpdatingFeed != menuController.isRefreshing) {
            menuController.refreshToolbarState()
        }
        pager.updateLoadingIndicator()
    }

    /**
     * Display the list
     */
    private fun displayList() {
        if (view == null) {
            Log.e(TAG, "Required root view is not yet created. Stop binding data to UI.")
            return
        }
        var listAdapter = adapter
        if (listAdapter == null) {
            recyclerView.adapter = null
            listAdapter = FeedItemListAdapter(requireActivity() as MainActivity)
            adapter = listAdapter
            val boundAdapter = listAdapter
            boundAdapter.setOnSelectModeListener(this)
            recyclerView.adapter = boundAdapter
            swipeActions = SwipeActions(this, TAG).attachTo(recyclerView)
            FeedItemListMultiSelect(requireActivity() as MainActivity) { loader.feed }
                .attachTo(boundAdapter)
        }
        if (skeleton.isSkeleton()) {
            skeletonRecyclerDelay.showOriginal()
        }
        val currentFeed = feed
        if (currentFeed != null && currentFeed.items != null) {
            listAdapter.updateItems(currentFeed.items)
            swipeActions?.setFilter(currentFeed.itemFilter)
        }
        menuController.refreshToolbarState()
        updateSyncProgressBarVisibility()
    }

    private fun loadItems() {
        loader.loadItems(
            {
                header.refresh()
                if (!loader.isDownloadingFeed) {
                    //while a download is in progress this UI logic is not needed
                    displayList()
                    header.setEpisodesLoaded(true)
                    val loadedFeed = feed
                    if (loadedFeed != null && loadedFeed.getId() > 0) {
                        header.showSubscribeButton()
                    }
                }
            },
            { error: Throwable? ->
                loader.feed = null
                header.refresh()
                displayList()
                Log.e(TAG, Log.getStackTraceString(error))
            }
        )
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onKeyUp(event: KeyEvent) {
        if (!isAdded || !isVisible || !isMenuVisible) {
            return
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_T -> recyclerView.smoothScrollToPosition(0)
            KeyEvent.KEYCODE_B -> adapter?.let {
                recyclerView.smoothScrollToPosition(it.itemCount - 1)
            }
            else -> {}
        }
    }

    private inner class FeedItemListAdapter(mainActivity: MainActivity) : EpisodeItemListAdapter(
        mainActivity, R.menu.episodes_multi_menu
    ) {
        override fun beforeBindViewHolder(holder: EpisodeItemViewHolder?, pos: Int) {
            holder?.coverHolder?.visibility =
                if (Prefs.showEpisodeCoverInFeed) View.VISIBLE else View.GONE
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
            super.onCreateContextMenu(menu, v, menuInfo)
            if (!inActionMode()) {
                menu.findItem(R.id.multi_select).isVisible = true
            }
            MenuItemUtils.setOnClickListeners(
                menu,
                { item: MenuItem -> this@FeedItemlistFragment.onContextItemSelected(item) })
        }
    }

    fun openAbout() {
        header.openAbout()
    }

    fun filterFeedItems() {
        menuController.filterFeedItems()
    }

    fun sortFeedItems() {
        menuController.sortFeedItems()
    }

    fun searchFeedItems() {
        menuController.searchFeedItems()
    }

    companion object {
        @JvmField
        val TAG = "ItemlistFragment"
        private val ARGUMENT_FEED_ID = "argument.allen.town.podcast.feed_id"
        private val ARGUMENT_FEED = "argument.allen.town.podcast.feed"
        private val KEY_UP_ARROW = "up_arrow"

        /**
         * Creates new ItemlistFragment which shows the Feeditems of a specific
         * feed. Sets 'showFeedtitle' to false
         *
         * @param feedId The id of the feed to show
         * @return the newly created instance of an ItemlistFragment
         */
        @JvmStatic
        fun newInstance(feedId: Long): FeedItemlistFragment {
            val i = FeedItemlistFragment()
            val b = Bundle()
            b.putLong(ARGUMENT_FEED_ID, feedId)
            i.arguments = b
            return i
        }

        @JvmStatic
        fun newInstance(feed: Feed?): FeedItemlistFragment {
            val i = FeedItemlistFragment()
            val b = Bundle()
            b.putParcelable(ARGUMENT_FEED, feed)
            i.arguments = b
            return i
        }
    }

    override fun backToContentTop() {
        recyclerView.scrollToPosition(5)
        recyclerView.post { recyclerView.smoothScrollToPosition(0) }
        appBar.setExpanded(true)
    }
}
