package allen.town.podcast.fragment

import allen.town.podcast.R
import allen.town.podcast.activity.MainActivity
import allen.town.podcast.activity.SettingsActivity
import allen.town.podcast.adapter.NavigationListAdapter
import allen.town.podcast.adapter.NavigationListAdapter.ItemAccess
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.storage.FeedKind
import allen.town.podcast.core.storage.NavDrawerData
import allen.town.podcast.core.util.LottieHelper.getRandomLottieFileName
import allen.town.podcast.event.FeedListUpdateEvent
import allen.town.podcast.event.QueueEvent
import allen.town.podcast.event.UnreadItemsUpdateEvent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class NavigationDrawerFragment : Fragment(), OnSharedPreferenceChangeListener {
    private var navDrawerData: NavDrawerData? = null
    private lateinit var navAdapter: NavigationListAdapter
    private var disposable: Disposable? = null
    private lateinit var progressBar: ProgressBar
    private lateinit var lottieAnimationView: LottieAnimationView


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val root = inflater.inflate(R.layout.nav_list, container, false)
        val preferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
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
        override fun isSelected(position: Int): Boolean =
            navAdapter.getFragmentTags()[position] == getLastNavFragment(requireContext())

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
            val mainActivity = requireActivity() as MainActivity
            mainActivity.loadFragment(navAdapter.getFragmentTags()[position], null)
            mainActivity.bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
        }
    }

    private fun loadData() {
        disposable = Observable.fromCallable { DBReader.getNavDrawerData(false, FeedKind.PODCASTS) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: NavDrawerData ->
                    navDrawerData = result
                    navAdapter.notifyDataSetChanged()
                    progressBar.visibility =
                        View.GONE // Stays hidden once there is something in the list
                }) { error: Throwable? ->
                Log.e(TAG, Log.getStackTraceString(error))
                progressBar.visibility = View.GONE
            }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (PREF_LAST_FRAGMENT_TAG == key) {
            navAdapter.notifyDataSetChanged() // Update selection
        }
    }

    companion object {
        @VisibleForTesting
        val PREF_LAST_FRAGMENT_TAG = "pref_last_fragment_tag"

        @VisibleForTesting
        val PREF_NAME = "pref_navigation_drawer"
        const val TAG = "DrawerFragment"

        @JvmField
        val NAV_DRAWER_TAGS = arrayOf(
            PlaylistFragment.TAG,
            EpisodesFragment.TAG,
            SubFeedsFragment.TAG,
            FavoriteEpisodesFragment.TAG,
            DownloadPagerFragment.TAG
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