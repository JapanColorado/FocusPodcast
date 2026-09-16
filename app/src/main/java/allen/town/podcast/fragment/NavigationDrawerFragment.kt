package allen.town.podcast.fragment

import allen.town.focus_common.util.BasePreferenceUtil
import allen.town.focus_common.util.MenuIconUtil.showContextMenuIcon
import allen.town.focus_common.util.Timber
import allen.town.focus_common.util.TopSnackbarUtil
import allen.town.focus_common.views.AccentMaterialDialog
import allen.town.podcast.MyApp
import allen.town.podcast.MyApp.Companion.instance
import allen.town.podcast.R
import allen.town.podcast.activity.MainActivity
import allen.town.podcast.activity.SettingsActivity
import allen.town.podcast.adapter.NavigationListAdapter
import allen.town.podcast.adapter.NavigationListAdapter.ItemAccess
import allen.town.podcast.appshortcuts.SubscriptionActivityStarter
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.storage.NavDrawerData
import allen.town.podcast.core.storage.NavDrawerData.*
import allen.town.podcast.core.util.LottieHelper.getRandomLottieFileName
import allen.town.podcast.core.util.menuhandler.MenuItemUtils
import allen.town.podcast.dialog.RemoveFeedDialog
import allen.town.podcast.dialog.RenameItemDialog
import allen.town.podcast.dialog.SubsFilterDialog
import allen.town.podcast.dialog.TagEditDialog
import allen.town.podcast.dialog.TagEditDialog.Companion.newInstance
import allen.town.podcast.event.FeedListUpdateEvent
import allen.town.podcast.event.QueueEvent
import allen.town.podcast.event.UnreadItemsUpdateEvent
import allen.town.podcast.model.feed.Feed
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import code.name.monkey.appthemehelper.ThemeStore.Companion.accentColor
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.SimpleLottieValueCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class NavigationDrawerFragment : Fragment(), OnSharedPreferenceChangeListener {
    private var navDrawerData: NavDrawerData? = null
    private var flatItemList: List<DrawerItem> = emptyList()
    private var contextPressedItem: DrawerItem? = null
    private lateinit var navAdapter: NavigationListAdapter
    private var disposable: Disposable? = null
    private lateinit var progressBar: ProgressBar
    private var openFolders: MutableSet<String> = HashSet()
    private lateinit var lottieAnimationView: LottieAnimationView


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val root = inflater.inflate(R.layout.nav_list, container, false)
        val preferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        openFolders =
            HashSet(preferences.getStringSet(PREF_OPEN_FOLDERS, HashSet())) // Must not modify
        progressBar = root.findViewById(R.id.progressBar)
        val navList = root.findViewById<RecyclerView>(R.id.nav_list)
        navAdapter = NavigationListAdapter(
            itemAccess,
            requireActivity()
        )
        navAdapter.setHasStableIds(true)
        navList.adapter = navAdapter
        navList.layoutManager = LinearLayoutManager(context)
        root.findViewById<View>(R.id.nav_settings).setOnClickListener { v: View? ->
            val mainActivity = requireActivity() as MainActivity
            mainActivity.closeDrawer {
                startActivity(Intent(mainActivity, SettingsActivity::class.java))
                mainActivity.overridePendingTransition(
                    R.anim.retro_fragment_open_enter,
                    R.anim.anim_activity_stay
                )
            }
        }
        lottieAnimationView = root.findViewById(R.id.lottie_play_item)
        lottieAnimationView.setAnimation(getRandomLottieFileName())
        preferences.registerOnSharedPreferenceChangeListener(this)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
        disposable?.dispose()
        requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val pressedItem = contextPressedItem ?: return
        val inflater = requireActivity().menuInflater
        menu.setHeaderTitle(pressedItem.title)
        if (pressedItem.type == DrawerItem.Type.FEED) {
            inflater.inflate(R.menu.nav_feed_context, menu)
            // episodes are not loaded, so we cannot check if the podcast has new or unplayed ones!
        } else {
            inflater.inflate(R.menu.nav_folder_context, menu)
        }
        showContextMenuIcon(menu)
        MenuItemUtils.setOnClickListeners(menu) { item: MenuItem -> onContextItemSelected(item) }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val pressedItem = contextPressedItem
        contextPressedItem = null
        if (pressedItem == null) {
            return false
        }
        return if (pressedItem.type == DrawerItem.Type.FEED) {
            onFeedContextMenuClicked((pressedItem as FeedDrawerItem).feed, item)
        } else {
            onTagContextMenuClicked(pressedItem, item)
        }
    }

    private fun onFeedContextMenuClicked(feed: Feed, item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.shortcut_item) {
            SubscriptionActivityStarter.createShortcut(context, feed)
            return true
        } else if (itemId == R.id.edit_tags) {
            newInstance(listOf(feed.preferences))
                .show(childFragmentManager, TagEditDialog.TAG)
            return true
        } else if (itemId == R.id.rename_item) {
            RenameItemDialog(requireActivity(), feed).show()
            return true
        } else if (itemId == R.id.remove_feed) {
            (requireActivity() as MainActivity).loadFragment(EpisodesFragment.TAG, null)
            RemoveFeedDialog.show(requireContext(), feed)
            return true
        }
        return super.onContextItemSelected(item)
    }

    private fun onTagContextMenuClicked(drawerItem: DrawerItem, item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.rename_folder_item) {
            RenameItemDialog(requireActivity(), drawerItem).show()
            return true
        }
        return super.onContextItemSelected(item)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: UnreadItemsUpdateEvent?) {
        loadData()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent?) {
        loadData()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onQueueChanged(event: QueueEvent) {
        // we are only interested in the number of queue items, not download status or position
        if (event.action == QueueEvent.Action.DELETED_MEDIA || event.action == QueueEvent.Action.SORTED || event.action == QueueEvent.Action.MOVED) {
            return
        }
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private val itemAccess: ItemAccess = object : ItemAccess {
        override val count: Int
            get() = flatItemList.size

        override fun getItem(position: Int): DrawerItem? {
            return if (0 <= position && position < flatItemList.size) {
                flatItemList[position]
            } else {
                null
            }
        }

        override fun isSelected(position: Int): Boolean {
            val lastNavFragment = getLastNavFragment(requireContext())
            if (position < navAdapter.subscriptionOffset) {
                return navAdapter.getFragmentTags()[position] == lastNavFragment
            } else if (lastNavFragment != null && StringUtils.isNumeric(lastNavFragment)) {
                // last fragment was not a list, but a feed
                val feedId = lastNavFragment.toLong()
                if (navDrawerData != null) {
                    val itemToCheck = flatItemList[position - navAdapter.subscriptionOffset]
                    if (itemToCheck.type == DrawerItem.Type.FEED) {
                        // When the same feed is displayed multiple times, it should be highlighted multiple times.
                        return (itemToCheck as FeedDrawerItem).feed.id == feedId
                    }
                }
            }
            return false
        }

        override val queueSize: Int
            get() = navDrawerData?.queueSize ?: 0
        override val numberOfNewItems: Int
            get() = navDrawerData?.numNewItems ?: 0
        override val numberOfDownloadedItems: Int
            get() = navDrawerData?.numDownloadedItems ?: 0
        override val reclaimableItems: Int
            get() = navDrawerData?.reclaimableSpace ?: 0
        override val feedCounterSum: Int
            get() {
                val navDrawerData = navDrawerData ?: return 0
                var sum = 0
                for (counter in navDrawerData.feedCounters.values()) {
                    sum += counter
                }
                return sum
            }


        override fun onItemClick(position: Int) {
            val viewType = navAdapter.getItemViewType(position)
            if (viewType != NavigationListAdapter.VIEW_TYPE_SECTION_DIVIDER) {
                val mainActivity = requireActivity() as MainActivity
                if (position < navAdapter.subscriptionOffset) {
                    val tag = navAdapter.getFragmentTags()[position]
                    mainActivity.loadFragment(tag, null)
                    mainActivity.bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
                } else {
                    val pos = position - navAdapter.subscriptionOffset
                    val clickedItem = flatItemList[pos]
                    if (clickedItem.type == DrawerItem.Type.FEED) {
                        val feedId = (clickedItem as FeedDrawerItem).feed.id
                        mainActivity.loadFeedFragmentById(feedId, null)
                        mainActivity.bottomSheet
                            .setState(BottomSheetBehavior.STATE_COLLAPSED)
                    } else {
                        val folder = clickedItem as TagDrawerItem
                        if (openFolders.contains(folder.name)) {
                            openFolders.remove(folder.name)
                        } else {
                            openFolders.add(folder.name)
                        }
                        requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putStringSet(PREF_OPEN_FOLDERS, openFolders)
                            .apply()
                        val drawerData = navDrawerData ?: return
                        disposable = Observable.fromCallable {
                            makeFlatDrawerData(
                                drawerData.items, 0
                            )
                        }
                            .subscribeOn(Schedulers.computation())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                { result: List<DrawerItem> ->
                                    flatItemList = result
                                    navAdapter.notifyDataSetChanged()
                                }) { error: Throwable? ->
                                Log.e(
                                    TAG,
                                    Log.getStackTraceString(error)
                                )
                            }
                    }
                }
            } else if (Prefs.subscriptionsFilter.isEnabled
                && navAdapter.showSubscriptionList
            ) {
                SubsFilterDialog.showDialog(requireContext())
            }
        }

        override fun onItemLongClick(position: Int): Boolean {
            return if (position < navAdapter.getFragmentTags().size) {
                true
            } else {
                contextPressedItem = flatItemList[position - navAdapter.subscriptionOffset]
                false
            }
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
            this@NavigationDrawerFragment.onCreateContextMenu(menu, v, menuInfo)
        }
    }

    private fun loadData() {
        disposable = Observable.fromCallable {
            val data = DBReader.getNavDrawerData(false)
            Pair(data, makeFlatDrawerData(data.items, 0))
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: Pair<NavDrawerData, List<DrawerItem>> ->
                    navDrawerData = result.first
                    flatItemList = result.second
                    navAdapter.notifyDataSetChanged()
                    progressBar.visibility =
                        View.GONE // Stays hidden once there is something in the list
                }) { error: Throwable? ->
                Log.e(TAG, Log.getStackTraceString(error))
                progressBar.visibility = View.GONE
            }
    }

    private fun makeFlatDrawerData(items: List<DrawerItem>, layer: Int): List<DrawerItem> {
        val flatItems: MutableList<DrawerItem> = ArrayList()
        for (item in items) {
            item.layer = layer
            flatItems.add(item)
            if (item.type == DrawerItem.Type.TAG) {
                val folder = item as TagDrawerItem
                folder.isOpen = openFolders.contains(folder.name)
                if (folder.isOpen) {
                    flatItems.addAll(makeFlatDrawerData(item.children, layer + 1))
                }
            }
        }
        return flatItems
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (PREF_LAST_FRAGMENT_TAG == key) {
            navAdapter.notifyDataSetChanged() // Update selection
        }
    }

    companion object {
        @VisibleForTesting
        val PREF_LAST_FRAGMENT_TAG = "pref_last_fragment_tag"
        private const val PREF_OPEN_FOLDERS = "pref_opened_folders"

        @VisibleForTesting
        val PREF_NAME = "pref_navigation_drawer"
        const val TAG = "DrawerFragment"

        @JvmField
        val NAV_DRAWER_TAGS = arrayOf(
            PlaylistFragment.TAG,
            EpisodesFragment.TAG,
            SubFeedsFragment.TAG,
            FavoriteEpisodesFragment.TAG,
            DownloadPagerFragment.TAG,
            PlaybackHistoryFragment.TAG,
            DiscoverFragment.TAG,
            NavigationListAdapter.SUBSCRIPTION_LIST_TAG
        )

        /**
         * Saves the most recently opened fragment
         *
         * @param context
         * @param tag
         */
        @JvmStatic
        fun saveLastNavFragment(context: Context, tag: String?) {
            Log.d(TAG, "set last nav fragment -> $tag")
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val edit = prefs.edit()
            if (tag != null) {
                edit.putString(PREF_LAST_FRAGMENT_TAG, tag)
            } else {
                edit.remove(PREF_LAST_FRAGMENT_TAG)
            }
            edit.apply()
        }

        @JvmStatic
        fun getLastNavFragment(context: Context): String? {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lastFragment = prefs.getString(PREF_LAST_FRAGMENT_TAG, PlaylistFragment.TAG)
            Log.v(TAG, "get last nav fragment() -> $lastFragment")
            return lastFragment
        }
    }
}