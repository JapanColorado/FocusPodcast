package dev.russell.focuspodcast.fragment

import dev.russell.focuspodcast.common.util.DoubleClickBackToContentTopListener
import dev.russell.focuspodcast.common.util.MenuIconUtil.showToolbarMenuIcon
import dev.russell.focuspodcast.common.util.Timber
import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.activity.MainActivity
import dev.russell.focuspodcast.activity.MainActivity.Companion.onPanelCollapsed
import dev.russell.focuspodcast.adapter.EpisodeItemListAdapter
import dev.russell.focuspodcast.adapter.MultiSelectAdapter.OnSelectModeListener
import dev.russell.focuspodcast.core.event.DownloadEvent
import dev.russell.focuspodcast.core.feed.FeedEvent
import dev.russell.focuspodcast.core.pref.Prefs
import dev.russell.focuspodcast.core.storage.DBTasks
import dev.russell.focuspodcast.core.util.FeedItemUtil
import dev.russell.focuspodcast.core.util.menuhandler.MenuItemUtils
import dev.russell.focuspodcast.databinding.FeedItemListFragmentBinding
import dev.russell.focuspodcast.event.*
import dev.russell.focuspodcast.event.playback.PlaybackPositionEvent
import dev.russell.focuspodcast.fragment.feeditemlist.FeedItemListHeader
import dev.russell.focuspodcast.fragment.feeditemlist.FeedItemListLoader
import dev.russell.focuspodcast.fragment.feeditemlist.FeedItemListMenu
import dev.russell.focuspodcast.fragment.feeditemlist.FeedItemListMultiSelect
import dev.russell.focuspodcast.fragment.feeditemlist.FeedItemListPager
import dev.russell.focuspodcast.fragment.swipeactions.SwipeActions
import dev.russell.focuspodcast.menuprocess.FeedItemMenuProcess
import dev.russell.focuspodcast.menuprocess.FeedMenuProcess
import dev.russell.focuspodcast.model.feed.Feed
import dev.russell.focuspodcast.util.SkeletonRecyclerDelay
import dev.russell.focuspodcast.view.EmptyViewHandler
import dev.russell.focuspodcast.view.FeedItemListToolbarIconTintHelper
import dev.russell.focuspodcast.view.StorePositionRecyclerView
import dev.russell.focuspodcast.viewholder.EpisodeItemViewHolder
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import android.view.ContextMenu.ContextMenuInfo
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import dev.russell.focuspodcast.theme.util.scroll.ThemedFastScroller.create
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
    private lateinit var emptyView: EmptyViewHandler

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
        emptyView = EmptyViewHandler(requireContext())
        emptyView.attachTo(binding.emptyViewContainer)
        header = FeedItemListHeader(
            this, root, binding.filterItems, { loader.feed }
        ) {
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

        applyStatusBarPadding(root, toolbar, header)
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

    /**
     * Offsets the pinned toolbar and the header by the real status bar inset. The toolbar is padded;
     * the header (a fixed-height block) is padded and grown by the same amount so its content keeps its
     * full height and stays below the toolbar instead of sliding up underneath it. The insets are read
     * on the fragment root because CollapsingToolbarLayout consumes them before they reach its
     * children. When the window is not edge-to-edge the inset is zero and nothing moves.
     */
    private fun applyStatusBarPadding(root: View, toolbar: View, header: FeedItemListHeader) {
        val toolbarPadding = toolbar.paddingTop
        val headerPadding = header.headerView.paddingTop
        val headerHeight = header.headerView.layoutParams.height
        val backgroundHeight = header.backgroundView.layoutParams.height
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            toolbar.updatePadding(top = toolbarPadding + top)
            header.headerView.updatePadding(top = headerPadding + top)
            header.headerView.updateLayoutParams { height = headerHeight + top }
            header.backgroundView.updateLayoutParams { height = backgroundHeight + top }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        uiHandler.removeCallbacksAndMessages(null)
        //restore when the screen is closed
        onPanelCollapsed(requireActivity() as AppCompatActivity)
        EventBus.getDefault().unregister(this)
        loader.dispose()
        header.release()
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
        updateEmptyState(currentFeed)
        menuController.refreshToolbarState()
        updateSyncProgressBarVisibility()
    }

    /**
     * A stored feed without visible episodes says why: it has none yet, or the episode filter hides
     * all of them (then with a way out). Feeds still being fetched show no empty state.
     */
    private fun updateEmptyState(currentFeed: Feed?) {
        val items = currentFeed?.items
        if (currentFeed == null || currentFeed.id <= 0 || items == null || items.isNotEmpty()) {
            binding.emptyViewContainer.visibility = View.GONE
            return
        }
        emptyView.clearActions()
        val hidden = loader.hiddenItemCount
        if (hidden > 0) {
            emptyView.setIcon(R.drawable.ic_filter)
            emptyView.setTitle(
                resources.getQuantityString(R.plurals.feed_filter_hides_all_episodes, hidden, hidden)
            )
            emptyView.addAction(R.string.clear_filter_label, R.drawable.ic_filter_disable) {
                FeedMenuProcess.clearFilter(currentFeed)
            }
        } else {
            emptyView.setIcon(R.drawable.ic_episodes)
            emptyView.setTitle(R.string.no_all_episodes_head_label)
        }
        binding.emptyViewContainer.visibility = View.VISIBLE
    }

    private fun loadItems() {
        loader.loadItems(
            {
                header.refresh()
                if (!loader.isDownloadingFeed) {
                    //while a download is in progress this UI logic is not needed
                    displayList()
                    header.setEpisodesLoaded(true)
                    header.loadHighlight()
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
        private val ARGUMENT_FEED_ID = "argument.dev.russell.focuspodcast.feed_id"
        private val ARGUMENT_FEED = "argument.dev.russell.focuspodcast.feed"
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
