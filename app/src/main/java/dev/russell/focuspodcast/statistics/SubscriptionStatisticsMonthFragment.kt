package dev.russell.focuspodcast.statistics

import dev.russell.focuspodcast.common.util.BaseDateUtils
import dev.russell.focuspodcast.R

class SubscriptionStatisticsMonthFragment : SubscriptionStatisticsBaseFragment() {
    override val timeFrom: Long
        get() = BaseDateUtils.getDayBefore(31)
    override val timeTo: Long
        get() = BaseDateUtils.getDayBefore(1)
    override val headStrRes: Int
        get() = R.string.statistics_filter_past_month
}