package dev.russell.yagi.statistics

import dev.russell.yagi.R


class SubscriptionStatisticsFulltimeFragment : SubscriptionStatisticsBaseFragment() {
    override val timeFrom: Long
        get() = 0
    override val timeTo: Long
        get() = System.currentTimeMillis()
    override val headStrRes: Int
        get() = R.string.statistics_filter_all_time

}
