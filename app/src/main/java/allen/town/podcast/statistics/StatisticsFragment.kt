package allen.town.podcast.statistics

import allen.town.podcast.core.view.TopAppBarLayout
import allen.town.podcast.R
import allen.town.podcast.core.dialog.ConfirmationDialog
import allen.town.podcast.core.storage.DBWriter
import allen.town.podcast.ui.common.PagedToolbarFragment
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.tabs.TabLayoutMediator.TabConfigurationStrategy
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.Future

/**
 * Displays the 'statistics' screen
 */
class StatisticsFragment : PagedToolbarFragment() {
    enum class PREF_FILTER_TYPE_ENUM {
        PREF_FILTER_TYPE_PASS_MONTH, PREF_FILTER_TYPE_PASS_YEAR, PREF_FILTER_TYPE_ALL_TIME
    }

    private lateinit var tabLayout: TabLayout
    private var resetDisposable: Disposable? = null
    private var viewPager: ViewPager2? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        setHasOptionsMenu(true)
        val rootView = inflater.inflate(R.layout.collapsing_pager_fragment, container, false)
        viewPager = rootView.findViewById(R.id.viewpager)
        toolbar = rootView.findViewById<TopAppBarLayout>(R.id.appBarLayout).toolbar
        toolbar.setTitle(getString(R.string.statistics_label))
        toolbar.setNavigationOnClickListener(View.OnClickListener { v: View? -> parentFragmentManager.popBackStack() })
        toolbar.inflateMenu(R.menu.statistics)
        viewPager.setAdapter(StatisticsPagerAdapter(this))
        // Give the TabLayout the ViewPager
        tabLayout = rootView.findViewById(R.id.sliding_tabs)
        super.setupPagedToolbar(toolbar, viewPager)
        TabLayoutMediator(
            tabLayout,
            viewPager,
            TabConfigurationStrategy { tab: TabLayout.Tab, position: Int ->
                when (position) {
                    POS_SUBSCRIPTIONS_FULL_TIME -> tab.setText(R.string.statistics_filter_all_time)
                    POS_SUBSCRIPTIONS_MONTH -> tab.setText(R.string.statistics_filter_past_month)
                    POS_SUBSCRIPTIONS_YEAR -> tab.setText(R.string.statistics_filter_past_year)
                    else -> {}
                }
            }).attach()
        return rootView
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.statistics_reset_item) {
            confirmResetAll()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun confirmResetAll() {
        val dialog = object : ConfirmationDialog(
            requireContext(),
            R.string.statistics_reset_label,
            R.string.statistics_reset_msg
        ) {
            override fun onConfirmButtonPressed(dialog: DialogInterface) {
                dialog.dismiss()
                resetAndReload(DBWriter.resetStatistics())
            }
        }
        dialog.setPositiveText(R.string.delete_label)
        dialog.createNewDialog().show()
    }

    /** Waits for a statistics write to finish, then reloads every tab. */
    fun resetAndReload(write: Future<*>) {
        resetDisposable?.dispose()
        resetDisposable = Completable.fromAction { write.get() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ reloadChildren() }) { error -> Log.e(TAG, Log.getStackTraceString(error)) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        resetDisposable?.dispose()
    }

    /** Reloads every statistics tab after the stored durations changed. */
    private fun reloadChildren() {
        for (child in childFragmentManager.fragments) {
            (child as? SubscriptionStatisticsBaseFragment)?.refreshStatistics()
        }
    }

    class StatisticsPagerAdapter internal constructor(fragment: Fragment) :
        FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                POS_SUBSCRIPTIONS_FULL_TIME -> SubscriptionStatisticsFulltimeFragment()
                POS_SUBSCRIPTIONS_MONTH -> SubscriptionStatisticsMonthFragment()
                POS_SUBSCRIPTIONS_YEAR -> SubscriptionStatisticsYearFragment()
                else -> SubscriptionStatisticsFulltimeFragment()
            }
        }

        override fun getItemCount(): Int {
            return TOTAL_COUNT
        }
    }

    companion object {
        const val TAG = "StatisticsFragment"
        const val PREF_NAME = "StatisticsActivityPrefs"
        private const val POS_SUBSCRIPTIONS_FULL_TIME = 0
        private const val POS_SUBSCRIPTIONS_MONTH = 1
        private const val POS_SUBSCRIPTIONS_YEAR = 2
        private const val TOTAL_COUNT = 3
    }
}