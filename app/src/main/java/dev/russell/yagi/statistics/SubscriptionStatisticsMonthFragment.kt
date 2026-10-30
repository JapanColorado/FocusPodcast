package dev.russell.yagi.statistics

import dev.russell.yagi.common.util.BaseDateUtils
import dev.russell.yagi.R

class SubscriptionStatisticsMonthFragment : SubscriptionStatisticsBaseFragment() {
    override val timeFrom: Long
        get() = BaseDateUtils.getDayBefore(31)
    override val timeTo: Long
        get() = BaseDateUtils.getDayBefore(1)
    override val headStrRes: Int
        get() = R.string.statistics_filter_past_month
}