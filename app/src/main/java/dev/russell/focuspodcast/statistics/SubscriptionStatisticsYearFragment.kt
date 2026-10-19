package dev.russell.focuspodcast.statistics

import dev.russell.focuspodcast.common.util.BaseDateUtils
import dev.russell.focuspodcast.R

class SubscriptionStatisticsYearFragment : SubscriptionStatisticsBaseFragment() {
    override val timeFrom: Long
        get() = BaseDateUtils.getDayBefore(366)
    override val timeTo: Long
        get() = BaseDateUtils.getDayBefore(1)
    override val headStrRes: Int
        get() = R.string.statistics_filter_past_year
}