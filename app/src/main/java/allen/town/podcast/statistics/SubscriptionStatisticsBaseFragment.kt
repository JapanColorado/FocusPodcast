package allen.town.podcast.statistics

import allen.town.podcast.R
import allen.town.podcast.core.dialog.ConfirmationDialog
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.storage.DBWriter
import allen.town.podcast.core.storage.DBReader.StatisticsResult
import allen.town.podcast.core.storage.StatisticsItem
import allen.town.podcast.util.SkeletonRecyclerDelay
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import allen.town.podcast.theme.util.scroll.ThemedFastScroller
import com.faltenreich.skeletonlayout.Skeleton
import com.faltenreich.skeletonlayout.applySkeleton
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.*

/**
 * Displays the 'playback statistics' screen
 */
abstract class SubscriptionStatisticsBaseFragment : Fragment() {
    private var disposable: Disposable? = null
    private lateinit var feedStatisticsList: RecyclerView

    private var listAdapter: PlaybackStatisticsListAdapter? = null
    private var statisticsResult: StatisticsResult? = null
    private lateinit var skeleton: Skeleton
    private lateinit var skeletonRecyclerDelay: SkeletonRecyclerDelay
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.statistics_fragment, container, false)
        feedStatisticsList = root.findViewById(R.id.statistics_list)
        listAdapter = PlaybackStatisticsListAdapter(this) { item -> confirmResetFeed(item) }
        feedStatisticsList.setLayoutManager(LinearLayoutManager(context))
        feedStatisticsList.setAdapter(listAdapter)
        ThemedFastScroller.create(feedStatisticsList)
        skeleton = feedStatisticsList.applySkeleton(R.layout.list_item_recyclerview_skeleton, 15)
        skeletonRecyclerDelay = SkeletonRecyclerDelay(skeleton, feedStatisticsList)
        skeletonRecyclerDelay.showSkeleton()
        return root
    }

    override fun onStart() {
        super.onStart()
        refreshStatistics()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable?.dispose()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    fun refreshStatistics() {
        loadStatistics()
    }

    private fun confirmResetFeed(item: StatisticsItem) {
        val dialog = object : ConfirmationDialog(
            requireContext(),
            R.string.statistics_reset_label,
            getString(R.string.statistics_reset_feed_msg, item.feed.title)
        ) {
            override fun onConfirmButtonPressed(dialog: DialogInterface) {
                dialog.dismiss()
                val write = DBWriter.resetStatistics(item.feed.id)
                val parent = parentFragment as? StatisticsFragment
                if (parent != null) {
                    parent.resetAndReload(write)
                } else {
                    refreshStatistics()
                }
            }
        }
        dialog.setPositiveText(R.string.delete_label)
        dialog.createNewDialog().show()
    }

    abstract val timeFrom: Long
    abstract val timeTo: Long
    abstract val headStrRes: Int
    private fun loadStatistics() {
        disposable?.dispose()
        disposable = Observable.fromCallable {
            val statisticsData = DBReader.getStatistics(
                false, timeFrom, timeTo
            )
            Collections.sort(statisticsData.feedTime) { item1: StatisticsItem, item2: StatisticsItem ->
                java.lang.Long.compare(
                    item2.timePlayed,
                    item1.timePlayed
                )
            }
            statisticsData
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result: StatisticsResult ->
                statisticsResult = result
                // When "from" is "today", set it to today
                listAdapter?.setTimeFilter(headStrRes)
                listAdapter?.update(result.feedTime)
                if (skeleton.isSkeleton()) {
                    skeletonRecyclerDelay.showOriginal()
                }
            }) { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) }
    }

    companion object {
        private val TAG = SubscriptionStatisticsBaseFragment::class.java.simpleName
    }
}