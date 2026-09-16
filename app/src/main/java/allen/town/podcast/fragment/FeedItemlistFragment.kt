package allen.town.podcast.fragment

import android.annotation.SuppressLint
import allen.town.focus_common.util.DoubleClickBackToContentTopListener
import allen.town.focus_common.util.ImageUtils.getColoredDrawable
import allen.town.focus_common.util.MenuIconUtil.showToolbarMenuIcon
import allen.town.focus_common.util.StatusBarUtils.setPaddingStatusBarTop
import allen.town.focus_common.util.Timber
import allen.town.focus_common.util.TopSnackbarUtil.showSnack
import allen.town.focus_common.util.Util.dp2Px
import allen.town.podcast.MyApp.Companion.runOnUiThread
import allen.town.podcast.R
import allen.town.podcast.activity.MainActivity
import allen.town.podcast.activity.MainActivity.Companion.onPanelCollapsed
import allen.town.podcast.activity.RssSearchActivity.Companion.feedInFeedlist
import allen.town.podcast.activity.RssSearchActivity.Companion.getFeedId
import allen.town.podcast.adapter.EpisodeItemListAdapter
import allen.town.podcast.adapter.MultiSelectAdapter
import allen.town.podcast.adapter.MultiSelectAdapter.OnPrepareActionModeListener
import allen.town.podcast.adapter.MultiSelectAdapter.OnSelectModeListener
import allen.town.podcast.core.event.DownloadEvent
import allen.town.podcast.core.feed.FeedEvent
import allen.town.podcast.core.feed.FeedUrlNotFoundException
import allen.town.podcast.core.glide.ApGlideSettings
import allen.town.podcast.core.glide.FastBlurTransformation
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.service.download.DownloadRequestCreator
import allen.town.podcast.core.service.download.DownloadService
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.storage.DBTasks
import allen.town.podcast.core.storage.DBWriter
import allen.town.podcast.core.util.FeedItemPermutors
import allen.town.podcast.core.util.FeedItemUtil
import allen.town.podcast.core.util.menuhandler.MenuItemUtils
import allen.town.podcast.core.util.menuhandler.MenuItemUtils.UpdateRefreshMenuItemChecker
import allen.town.podcast.core.util.ui.ListFooterUtil
import allen.town.podcast.databinding.FeedItemListFragmentBinding
import allen.town.podcast.dialog.RemoveFeedDialog.OnFeedRemovedListener
import allen.town.podcast.dialog.RemoveFeedDialog.show
import allen.town.podcast.dialog.RenameItemDialog
import allen.town.podcast.discovery.PodcastSearcherRegistry
import allen.town.podcast.discovery.RetrieveFeedUtil.tryToRetrieveFeedUrlBySearch
import allen.town.podcast.event.*
import allen.town.podcast.event.playback.PlaybackPositionEvent
import allen.town.podcast.fragment.actions.EpisodeMultiSelectActionHandler
import allen.town.podcast.fragment.swipeactions.SwipeActions
import allen.town.podcast.menuprocess.FeedItemMenuProcess
import allen.town.podcast.menuprocess.FeedMenuProcess
import allen.town.podcast.model.feed.Feed
import allen.town.podcast.util.SkeletonRecyclerDelay
import allen.town.podcast.view.FeedItemListToolbarIconTintHelper
import allen.town.podcast.view.SeriesDetailInfoView
import allen.town.podcast.view.StorePositionRecyclerView
import allen.town.podcast.view.SubscribeButton
import allen.town.podcast.viewholder.EpisodeItemViewHolder
import android.animation.Animator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.res.Configuration
import android.graphics.LightingColorFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import code.name.monkey.appthemehelper.ThemeStore.Companion.accentColor
import code.name.monkey.appthemehelper.util.ATHUtil.resolveColor
import code.name.monkey.appthemehelper.util.scroll.ThemedFastScroller.create
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.faltenreich.skeletonlayout.Skeleton
import com.faltenreich.skeletonlayout.applySkeleton
import com.google.android.material.appbar.AppBarLayout
import com.joanzapata.iconify.Iconify
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Displays a list of FeedItems.
 */
class FeedItemlistFragment() : Fragment(), OnItemClickListener, Toolbar.OnMenuItemClickListener,
    OnSelectModeListener , DoubleClickBackToContentTopListener.IBackToContentTopView {
    private lateinit var binding: FeedItemListFragmentBinding
    private var adapter: FeedItemListAdapter? = null
    private var swipeActions: SwipeActions? = null
    private lateinit var nextPageLoader: ListFooterUtil
    private lateinit var recyclerView: StorePositionRecyclerView
    private lateinit var txtvTitle: TextView
    private lateinit var txtvFailure: TextView
    private lateinit var imgvBackground: ImageView
    private lateinit var imgvCover: ImageView
    private lateinit var txtvInformation: TextView
    private lateinit var txtvAuthor: TextView
    private lateinit var txtvUpdatesDisabled: TextView
    private lateinit var header: View
    private lateinit var toolbar: Toolbar
    private var displayUpArrow = false
    private var feedID: Long = 0
    private var feed: Feed? = null
    private var headerCreated = false
    private var isUpdatingFeed = false
    private var disposable: Disposable? = null
    private lateinit var detailInfoView: SeriesDetailInfoView
    private lateinit var skeleton: Skeleton
    private lateinit var mInfoViewToggleButton: View
    private lateinit var detailInfoViewContainer: FrameLayout
    private lateinit var subscribe_button: SubscribeButton
    private var isDownloadingFeed = false
    private var updateDownloadStatus: Disposable? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var iconTintManager: FeedItemListToolbarIconTintHelper? = null
    private lateinit var appBar: AppBarLayout
    private lateinit var skeletonRecyclerDelay: SkeletonRecyclerDelay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //keep this line: otherwise items disappear when switching to dark mode on this screen
//        setRetainInstance(true);
        val args = requireArguments()
        feedID = args.getLong(ARGUMENT_FEED_ID)
        feed = args.getParcelable(ARGUMENT_FEED)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FeedItemListFragmentBinding.inflate(inflater, container, false)
        val root = binding.root
        binding.filterItems.setOnClickListener { filterFeedItems() }
        binding.sortItems.setOnClickListener { sortFeedItems() }
        binding.actionSearch.setOnClickListener { searchFeedItems() }
        toolbar = binding.toolbar
        toolbar.inflateMenu(R.menu.feedlist)
        showToolbarMenuIcon(toolbar)
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setOnClickListener(DoubleClickBackToContentTopListener(this))
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        }
        (requireActivity() as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)
        refreshToolbarState()
        recyclerView = binding.recyclerView
        recyclerView.setRecycledViewPool((requireActivity() as MainActivity).recycledViewPool)
        create(recyclerView)
        skeleton = recyclerView.applySkeleton(R.layout.item_small_recyclerview_skeleton, 15)
        skeletonRecyclerDelay = SkeletonRecyclerDelay(skeleton, recyclerView)
        skeletonRecyclerDelay.showSkeleton()
        txtvTitle = root.findViewById(R.id.txtvTitle)
        txtvAuthor = root.findViewById(R.id.txtvAuthor)
        imgvBackground = root.findViewById(R.id.imgvBackground)
        imgvCover = root.findViewById(R.id.imgvCover)
        txtvInformation = root.findViewById(R.id.txtvInformation)
        txtvFailure = root.findViewById(R.id.txtvFailure)
        txtvUpdatesDisabled = root.findViewById(R.id.txtvUpdatesDisabled)
        header = root.findViewById(R.id.headerContainer)
        appBar = binding.appBar
        val collapsingToolbar = binding.collapsingToolbar
        detailInfoView = root.findViewById(R.id.detailInfoView)
        mInfoViewToggleButton = root.findViewById(R.id.info_view_toggle_button)
        imgvBackground.setOnClickListener(View.OnClickListener { v: View? -> openAbout() })
        detailInfoViewContainer = root.findViewById(R.id.series_info_view_container)
        subscribe_button = root.findViewById(R.id.subscribe_button)
        subscribe_button.setTickSize(dp2Px(requireContext(), 18.0f))
        subscribe_button.setOnClickListener(View.OnClickListener { v: View? ->
            val currentFeed = feed ?: return@OnClickListener
            if (subscribe_button.isSubscribed()) {
                show(requireContext(), currentFeed, object : OnFeedRemovedListener {
                    override fun onFeedRemoved() {
                        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                            getParentFragmentManager().popBackStack()
                        } else {
                            (requireActivity() as MainActivity).loadFragment(
                                SubFeedsFragment.TAG,
                                null
                            )
                        }
                    }
                })
            } else {
                DBWriter.subscribeFeed(currentFeed, requireContext())
            }
        })

        //top padding is required
        setPaddingStatusBarTop(requireActivity(), toolbar)
        setPaddingStatusBarTop(requireActivity(), header)
        val tintManager =
            FeedItemListToolbarIconTintHelper(requireContext(), toolbar, collapsingToolbar)
        iconTintManager = tintManager
        tintManager.updateTint()
        appBar.addOnOffsetChangedListener(tintManager)
        val pageLoader = ListFooterUtil(root.findViewById(R.id.more_content_list_footer))
        nextPageLoader = pageLoader
        pageLoader.setClickListener({
            val currentFeed = feed
            if (currentFeed != null) {
                DBTasks.loadNextPageOfFeed(getActivity(), currentFeed, false)
            }
        })
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, deltaX: Int, deltaY: Int) {
                super.onScrolled(view, deltaX, deltaY)
                val currentFeed = feed
                val hasMorePages =
                    (currentFeed != null) && currentFeed.isPaged && (currentFeed.nextPageLink != null)
                val pageLoaderVisible = recyclerView.isScrolledToBottom && hasMorePages
                pageLoader.root.visibility =
                    if (pageLoaderVisible) View.VISIBLE else View.GONE
                recyclerView.setPadding(
                    recyclerView.getPaddingLeft(), 0, recyclerView.getPaddingRight(),
                    if (pageLoaderVisible) pageLoader.root.measuredHeight else 0
                )
            }
        })
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
        disposable?.dispose()
        adapter?.endSelectMode()
        // displayList() only attaches the adapter when it is null; a surviving fragment instance
        // with a new RecyclerView would otherwise show an empty list forever
        adapter = null
        updateDownloadStatus?.dispose()
//        loadAd(requireActivity(), true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    private val updateRefreshMenuItemChecker = UpdateRefreshMenuItemChecker {
        val currentFeed = feed
        currentFeed != null && DownloadService.isRunning()
                && DownloadService.isDownloadingFile(currentFeed.getDownload_url())
    }

    private fun refreshToolbarState() {
        val feed = this.feed ?: return
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val horizontalSpacing =
            resources.getDimension(R.dimen.additional_horizontal_spacing).toInt()
        header.setPadding(
            horizontalSpacing,
            header.paddingTop,
            horizontalSpacing,
            header.paddingBottom
        )
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val feed = this.feed
        if (feed == null) {
            showSnack(activity, R.string.please_wait_for_data, Toast.LENGTH_LONG)
            return true
        }
        val feedMenuHandled = FeedMenuProcess.onOptionsItemClicked(activity, item, feed)
        if (feedMenuHandled) {
            return true
        }
        val itemId = item.itemId
        if (itemId == R.id.rename_item) {
            RenameItemDialog(requireActivity(), feed).show()
            return true
        } else if (itemId == R.id.feed_setting) {
            val fragment: FeedSettingsFragment = FeedSettingsFragment.newInstance(feed)
            (requireActivity() as MainActivity).loadChildFragment(fragment)
            return true
        }
        return false
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
        if (event.feedId == feedID) {
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
        if (event.hasChangedFeedUpdateStatus(isUpdatingFeed)) {
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
        if ((currentFeed != null) && !DownloadService.isDownloadingFile(
                currentFeed.download_url
            ) && isDownloadingFeed
        ) {
            // DownloadEvents arrive on every progress tick; do not stack one query per event
            updateDownloadStatus?.dispose()
            updateDownloadStatus = Observable.fromCallable(
                { DBReader.getAllFeedList() })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { feeds: List<Feed>? ->
                        this@FeedItemlistFragment.feeds = feeds
                        if (feedInFeedlist(feeds, feed)) {
                            //download finished, the feed is now in the database
                            isDownloadingFeed = false
                            Log.i(TAG, "set isDownloadingFeed = false ")
                            //look up the feedId in the database (by download url)
                            feedID = getFeedId(feeds, feed)
                            updateUi()
                        }
                    }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) }
                )
        }
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
        refreshToolbarState()
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

    private var feeds: List<Feed>? = null
    private fun updateSyncProgressBarVisibility() {
        if (isUpdatingFeed != updateRefreshMenuItemChecker.isRefreshing) {
            refreshToolbarState()
        }
        if (!DownloadService.isDownloadingFeeds()) {
            nextPageLoader.root.visibility = View.GONE
        }
        nextPageLoader.setLoadingState(DownloadService.isDownloadingFeeds())
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
            boundAdapter.setOnMenuItemClickListener(object :
                MultiSelectAdapter.OnMenuItemClickListener {
                override fun onMenuItemClick(item: MenuItem?) {
                    if (item == null) {
                        return
                    }
                    EpisodeMultiSelectActionHandler(
                        requireActivity() as MainActivity,
                        boundAdapter.selectedItems
                    )
                        .handleAction(item.itemId)
                    boundAdapter.endSelectMode()
                }
            })
            boundAdapter.setonPrepareActionListener(object : OnPrepareActionModeListener {
                override fun onPrepareActionMode(mode: ActionMode?, item: Menu?) {
                    val currentFeed = feed ?: return
                    if (item != null && currentFeed.isLocalFeed) {
                        item.findItem(R.id.download_batch).isVisible = false
                        item.findItem(R.id.delete_batch).isVisible = false
                    }
                }
            })
        }
        if (skeleton.isSkeleton()) {
            skeletonRecyclerDelay.showOriginal()
        }
        val currentFeed = feed
        if (currentFeed != null && currentFeed.items != null) {
            listAdapter.updateItems(currentFeed.items)
            swipeActions?.setFilter(currentFeed.itemFilter)
        }
        refreshToolbarState()
        updateSyncProgressBarVisibility()
    }

    /**
     * Refresh the header: feed title, image, etc.
     */
    private fun refreshHeaderView() {
        setupHeaderView()
        val feed = this.feed
        if (feed == null) {
            Log.e(TAG, "Unable to refresh header view")
            return
        }
        loadFeedImage(feed)
        if (feed.hasLastUpdateFailed()) {
            txtvFailure.visibility = View.VISIBLE
        } else {
            txtvFailure.visibility = View.INVISIBLE
        }
        if (feed.preferences != null && !feed.preferences.keepUpdated) {
            txtvUpdatesDisabled.text =
                "{md-pause-circle-outline} " + this.getString(R.string.updates_disabled_label)
            Iconify.addIcons(txtvUpdatesDisabled)
            txtvUpdatesDisabled.visibility = View.VISIBLE
        } else {
            txtvUpdatesDisabled.visibility = View.GONE
        }
        txtvTitle.text = feed.title
        txtvAuthor.text = feed.author
        val itemFilter = feed.itemFilter
        if (itemFilter != null && itemFilter.values.isNotEmpty()) {
            binding.filterItems.setImageResource(R.drawable.ic_filter_disable)
        } else {
            binding.filterItems.setImageResource(R.drawable.ic_filter)
        }
    }

    private fun setupHeaderView() {
        if (feed == null || headerCreated) {
            return
        }

        // https://github.com/bumptech/glide/issues/529
        imgvBackground.colorFilter = LightingColorFilter(-0x99999a, 0x000000)
        imgvCover.setOnClickListener({ v: View? -> openAbout() })
        headerCreated = true
    }

    private fun loadFeedImage(feed: Feed) {
        Glide.with(this)
            .load(feed.imageUrl)
            .apply(
                RequestOptions()
                    .placeholder(R.color.image_readability_tint)
                    .error(R.color.image_readability_tint)
                    .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                    .transform(FastBlurTransformation())
                    .dontAnimate()
            )
            .into(imgvBackground)
        Glide.with(this)
            .load(feed.imageUrl)
            .apply(
                RequestOptions()
                    .placeholder(R.drawable.ic_podcast_background_round)
                    .error(R.drawable.ic_podcast_background_round)
                    .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                    .centerCrop()
                    .dontAnimate()
            )
            .into(imgvCover)
    }

    /**
     * Load the items
     */
    private fun loadItems() {
        disposable?.dispose()
        disposable = Observable.fromCallable({ loadData() })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: Feed? ->
                    refreshHeaderView()
                    if (!isDownloadingFeed) {
                        //while a download is in progress this UI logic is not needed
                        displayList()
                        detailInfoView.setEpisodesLoaded(true)
                        val loadedFeed = feed
                        if (loadedFeed != null && loadedFeed.getId() > 0) {
                            subscribe_button.setVisibility(View.VISIBLE)
                        }
                    }
                }, { error: Throwable? ->
                    feed = null
                    refreshHeaderView()
                    displayList()
                    Log.e(TAG, Log.getStackTraceString(error))
                })
    }

    private fun initDetailView() {
        runOnUiThread({
            val ctx = getContext()
            if (ctx == null) {
                //entered from a home screen shortcut: the item gets loaded twice (one recent, one current), which leaves this null and makes neither RxJava branch run afterwards (reason unknown)
                Timber.e(" loadItems break getContext() == null")
                return@runOnUiThread
            }
            val currentFeed = feed ?: return@runOnUiThread
            //must run on the main thread; called before downloading, since the feed may have to be fetched from the network if it is not in the local database
            if (!currentFeed.isLocalFeed()) {
                mInfoViewToggleButton.setVisibility(View.VISIBLE)
                mInfoViewToggleButton.setOnClickListener(View.OnClickListener { v: View? -> openAbout() })
                mInfoViewToggleButton.setBackground(
                    getColoredDrawable(
                        ctx, R.drawable.shape_circle, resolveColor(
                            ctx, android.R.attr.windowBackground
                        )
                    )
                )
                if (!currentFeed.isSubscribed()) {
                    detailInfoView.expandCollapseContent(false, false)
                } else {
                    detailInfoView.setVisibility(View.GONE)
                }
            } else {
                detailInfoView.setVisibility(View.GONE)
            }

            //subscribe button state
            subscribe_button.setCircleRingColor(
                resolveColor(
                    ctx,
                    android.R.attr.windowBackground
                )
            )
            subscribe_button.setSubscribedIconColor(accentColor(ctx))
            subscribe_button.setSubscribed(currentFeed.isSubscribed())
            var circleFillColor: Int = 0
            if (currentFeed.isSubscribed()) {
                circleFillColor = subscribe_button.getCircleRingColor()
            }
            subscribe_button.setCircleFillColor(circleFillColor)
            detailInfoView.setData(currentFeed, getActivity())
        })
    }

    private var finalGetFeedUrl = false
    @SuppressLint("CheckResult") // fire-and-forget: app-scoped DB work with its own onError; nothing to dispose
    private fun loadData(): Feed? {
        if (feedID > 0) {
            //a feedId means the feed exists in the database
            feed = DBReader.getFeed(feedID, true)
        }
        var currentFeed = feed ?: return null
        initDetailView()
        if (currentFeed.id == 0L) {
            val loadedFeed = currentFeed
            val feedFromDb: Feed? = if (!TextUtils.isEmpty(loadedFeed.itunesId)) {
                //comes from iTunes
                Timber.i("feedUrl from itunes ")
                DBReader.getFeedByItunesFeedId(loadedFeed.itunesId, true)
            } else {
                DBReader.getFeed(loadedFeed.download_url, true)
            }
            if (feedFromDb == null) {
                //only reached when the id is 0 and the feedUrl lookup also fails; otherwise duplicate rows would be created
                PodcastSearcherRegistry.lookupUrl(loadedFeed.download_url)
                    .subscribeOn(Schedulers.trampoline())
                    .observeOn(Schedulers.trampoline())
                    .subscribe(
                        { feedUrl: String ->
                            Timber.i("get feedUrl from itunes " + feedUrl)
                            loadedFeed.setDownload_url(feedUrl)
                            finalGetFeedUrl = true
                        },
                        { error: Throwable? ->
                            if (error is FeedUrlNotFoundException) {
                                finalGetFeedUrl = !TextUtils.isEmpty(
                                    tryToRetrieveFeedUrlBySearch(error)
                                )
                            } else {
                                loadedFeed.setLastUpdateFailed(true)
                                runOnUiThread {
                                    //run on the main thread
                                    txtvFailure.setText(R.string.null_value_podcast_error)
                                }
                                Log.e(TAG, Log.getStackTraceString(error))
                            }
                        })
                Log.i(TAG, "check isDownloadingFeed $isDownloadingFeed")
                if (finalGetFeedUrl) {
                    if (!isDownloadingFeed) {
                        DownloadService.download(
                            context, false, DownloadRequestCreator.create(loadedFeed).build()
                        )
                        isDownloadingFeed = true
                    } else {
                        //loadData also runs again after a failed download, and we must not start another download then (other events can reach this branch too, so verify nothing is actually downloading) or we end up in an infinite loop
                        if (!DownloadService.isDownloadingFile(
                                loadedFeed.download_url
                            )
                        ) {
                            Log.i(TAG, "not downloading setLastUpdateFailed ")
                            isDownloadingFeed = false
                            loadedFeed.setLastUpdateFailed(true)
                        }
                    }
                }
                return loadedFeed
            } else {
                //the feed was found in the database, so use the stored values
                feed = feedFromDb
                currentFeed = feedFromDb
                feedID = feedFromDb.id
            }
        }
        DBReader.loadAdditionalFeedItemListData(currentFeed.items)
        val sortOrder = currentFeed.sortOrder
        if (sortOrder != null) {
            val feedItems = currentFeed.items
            FeedItemPermutors.getPermutor(sortOrder).reorder(feedItems)
            currentFeed.items = feedItems
        }
        return currentFeed
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
        val feed = this.feed ?: return
        if (!feed.isLocalFeed && detailInfoView.isAllDataLoaded) {
            if (!feed.isSubscribed) {
                detailInfoView.toggleCollapseMode()
                return
            }
            if (detailInfoView.isCollapsed) {
                detailInfoView.expandCollapseContent(true, false)
            }
            animateSeriesInfoView("openAbout", null)
        }
    }

    /* synthetic */   fun `lambda$animateSeriesInfoView$2`(valueAnimator: ValueAnimator) {
        mInfoViewToggleButton.rotation = (valueAnimator.animatedValue as Float).toFloat()
    }

    private fun animateSeriesInfoView(str: String, animatorListener: Animator.AnimatorListener?) {
        val feed = this.feed ?: return
        if (!feed.isLocalFeed) {
            val z = detailInfoView.visibility == View.VISIBLE
            detailInfoView.clearDescriptionSelection()
            val fArr = FloatArray(2)
            var f = 180.0f
            fArr[0] = if (z) 180.0f else 0.0f
            if (z) {
                f = 0.0f
            }
            fArr[1] = f
            val ofFloat = ValueAnimator.ofFloat(*fArr)
            ofFloat.duration = 150L
            ofFloat.addUpdateListener(AnimatorUpdateListener { valueAnimator ->

                // from class: fm.player.ui.fragments.i
                // android.animation.ValueAnimator.AnimatorUpdateListener
                Timber.e("rote " + valueAnimator.animatedValue)
                `lambda$animateSeriesInfoView$2`(valueAnimator)
            })
            ofFloat.start()
            if (z) {
                val viewHeight = detailInfoView.getViewHeight(false)
                if (viewHeight > 0) {
                    detailInfoViewContainer.layoutParams.height = viewHeight
                    detailInfoViewContainer.pivotY = 0.0f
                    detailInfoViewContainer.pivotX = 0.0f
                    val ofInt = ValueAnimator.ofInt(viewHeight, 0)
                    ofInt.addUpdateListener(object : AnimatorUpdateListener {
                        // from class: fm.player.ui.fragments.FeedItemlistFragment.10
                        // android.animation.ValueAnimator.AnimatorUpdateListener
                        override fun onAnimationUpdate(valueAnimator: ValueAnimator) {
                            detailInfoViewContainer.scaleY =
                                (valueAnimator.animatedValue as Int).toFloat() / viewHeight
                            detailInfoView.layoutParams.height =
                                (valueAnimator.animatedValue as Int)
                            detailInfoView.requestLayout()
                        }
                    })
                    ofInt.addListener(object : Animator.AnimatorListener {
                        // from class: fm.player.ui.fragments.FeedItemlistFragment.11
                        // android.animation.Animator.AnimatorListener
                        override fun onAnimationCancel(animator: Animator) {
                            val animatorListener2 = animatorListener
                            animatorListener2?.onAnimationCancel(animator)
                        }

                        // android.animation.Animator.AnimatorListener
                        override fun onAnimationEnd(animator: Animator) {
                            detailInfoView.visibility = View.GONE
                            val animatorListener2 = animatorListener
                            animatorListener2?.onAnimationEnd(animator)
                        }

                        // android.animation.Animator.AnimatorListener
                        override fun onAnimationRepeat(animator: Animator) {
                            val animatorListener2 = animatorListener
                            animatorListener2?.onAnimationRepeat(animator)
                        }

                        // android.animation.Animator.AnimatorListener
                        override fun onAnimationStart(animator: Animator) {
                            val animatorListener2 = animatorListener
                            animatorListener2?.onAnimationStart(animator)
                        }
                    })
                    ofInt.duration = 250L
                    ofInt.interpolator = AccelerateDecelerateInterpolator()
                    ofInt.start()
                    detailInfoView.animateFadeOverlayExpandButton(false, 250)
                    return
                }
                return
            }
            detailInfoView.measure(0, 0)
            val viewHeight2 = detailInfoView.getViewHeight(true)
            if (viewHeight2 > 0) {
                detailInfoViewContainer.layoutParams.height = viewHeight2
                detailInfoViewContainer.pivotY = 0.0f
                detailInfoViewContainer.pivotX = 0.0f
                detailInfoView.visibility = View.VISIBLE
                val ofInt2 = ValueAnimator.ofInt(0, viewHeight2)
                ofInt2.addUpdateListener(object : AnimatorUpdateListener {
                    // from class: fm.player.ui.fragments.FeedItemlistFragment.12
                    // android.animation.ValueAnimator.AnimatorUpdateListener
                    override fun onAnimationUpdate(valueAnimator: ValueAnimator) {
                        detailInfoViewContainer.scaleY =
                            (valueAnimator.animatedValue as Int).toFloat() / viewHeight2
                        detailInfoView.layoutParams.height =
                            (valueAnimator.animatedValue as Int)
                        detailInfoView.requestLayout()
                    }
                })
                ofInt2.addListener(object : Animator.AnimatorListener {
                    // from class: fm.player.ui.fragments.FeedItemlistFragment.13
                    // android.animation.Animator.AnimatorListener
                    override fun onAnimationCancel(animator: Animator) {}

                    // android.animation.Animator.AnimatorListener
                    override fun onAnimationEnd(animator: Animator) {
                        detailInfoView.layoutParams.height =
                            if (detailInfoView.isCollapsed) detailInfoView.collapsedHeight else -2
                        detailInfoViewContainer.layoutParams.height = -2
                        detailInfoViewContainer.requestLayout()
                        detailInfoView.requestLayout()
                    }

                    // android.animation.Animator.AnimatorListener
                    override fun onAnimationRepeat(animator: Animator) {}

                    // android.animation.Animator.AnimatorListener
                    override fun onAnimationStart(animator: Animator) {}
                })
                ofInt2.duration = 250L
                ofInt2.interpolator = AccelerateDecelerateInterpolator()
                ofInt2.start()
                detailInfoView.animateFadeOverlayExpandButton(true, 250)
            }
        }
    }

    fun filterFeedItems() {
        if (feed == null) {
            showSnack(activity, R.string.please_wait_for_data, Toast.LENGTH_LONG)
            return
        }
        FeedMenuProcess.showFilterDialog(context, feed)
    }

    fun sortFeedItems() {
        if (feed == null) {
            showSnack(activity, R.string.please_wait_for_data, Toast.LENGTH_LONG)
            return
        }
        FeedMenuProcess.showSortDialog(context, feed)
    }

    fun searchFeedItems() {
        val feed = this.feed
        if (feed == null) {
            showSnack(activity, R.string.please_wait_for_data, Toast.LENGTH_LONG)
            return
        }
        (requireActivity() as MainActivity).loadChildFragment(
            LocalSearchFragment.Companion.newInstance(
                feed.id, feed.title
            )
        )
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