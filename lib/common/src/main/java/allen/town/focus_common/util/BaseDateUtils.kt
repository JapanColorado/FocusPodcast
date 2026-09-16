package allen.town.focus_common.util

import java.text.SimpleDateFormat
import java.util.*

object BaseDateUtils {
    @JvmStatic
    fun isCurrentYear(time: Long): Boolean {
        val now = Calendar.getInstance()
        val currentYear = now[Calendar.YEAR]
        now.timeInMillis = time
        val year = now[Calendar.YEAR]
        return currentYear == year
    }


    /**
     * 获取前后几个月的日期
     * @param beginDate
     * @param distanceMonth
     * @param format
     * @return
     */
    fun getOldDateByMonth(beginDate: Date?, distanceMonth: Int, format: String?): String? {
        var format = format
        if (format == null || format.isEmpty()) {
            format = "yyyy-MM-dd"
        }
        val dft = SimpleDateFormat(format)
        val date = Calendar.getInstance()
        date.time = beginDate
        date[Calendar.MONTH] = date[Calendar.MONTH] + distanceMonth
        var endDate: Date? = null
        try {
            endDate = dft.parse(dft.format(date.time))
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return dft.format(endDate)
    }

    @JvmStatic
    fun getDayBefore(days: Int): Long {
        val instance = Calendar.getInstance()
        instance.add(Calendar.DAY_OF_MONTH, -days)
        return instance.timeInMillis
    }

    /**
     * 获取某个日期前后N天的日期
     *
     * @param beginDate
     * @param distanceDay 前后几天 如获取前7天日期则传-7即可；如果后7天则传7
     * @param format      日期格式，默认"yyyy-MM-dd"
     * @return
     */
    fun getOldDateByDay(beginDate: Date?, distanceDay: Int, format: String?): String? {
        var format = format
        if (format == null || format.isEmpty()) {
            format = "yyyy-MM-dd"
        }
        val dft = SimpleDateFormat(format)
        val date = Calendar.getInstance()
        date.time = beginDate
        date[Calendar.DATE] = date[Calendar.DATE] + distanceDay
        var endDate: Date? = null
        try {
            endDate = dft.parse(dft.format(date.time))
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return dft.format(endDate)
    }

    /**
     * Date对象获取时间字符串
     */
    @JvmStatic
    fun date2TimeStamp(date: String?, format: String?): String? {
        try {
            val sdf = SimpleDateFormat(format)
            return (sdf.parse(date).time / 1000).toString()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return ""
    }

    /**
     * 时间戳转换日期格式字符串
     */
    @JvmStatic
    fun timeStamp2Date(time: Long, format: String? = "yyyy-MM-dd"): String? {
        val sdf = SimpleDateFormat(format)
        return sdf.format(Date(time))
    }


    @JvmStatic
    fun isCurrentYear(date: Date): Boolean {
        val now = Calendar.getInstance()
        val currentYear = now[Calendar.YEAR]
        now.time = date
        val year = now[Calendar.YEAR]
        return currentYear == year
    }

    @JvmStatic
    fun isSameDay(date1: Date, date2: Date): Boolean {
        val calendar = Calendar.getInstance()
        calendar.time = date1

        val otherCalendar = Calendar.getInstance()
        otherCalendar.time = date2

        return calendar.get(Calendar.YEAR) == otherCalendar.get(Calendar.YEAR)
                && calendar.get(Calendar.DAY_OF_YEAR) == otherCalendar.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * 是否为昨天
     */
    @JvmStatic
    fun isYesterday(timeStamp: Long?): Boolean {
        val todayCalendar = Calendar.getInstance()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeStamp!!
        if (calendar[Calendar.YEAR] == todayCalendar[Calendar.YEAR]) {
            val diffDay = todayCalendar[Calendar.DAY_OF_YEAR] - calendar[Calendar.DAY_OF_YEAR]
            return diffDay == 1
        }
        return false
    }


    @JvmStatic
    fun inTime(str: String, str2: String?): Boolean {
        val format = SimpleDateFormat("HH:mm").format(Date())
        return if (str.compareTo(str2!!) >= 0) {
            format.compareTo(str) >= 0 || format.compareTo(str2) <= 0
        } else !(format.compareTo(str) < 0 || format.compareTo(str2) > 0)
    }

    /**
     * 获取今天零点的时间
     * @return
     */
    @JvmStatic
    fun getToadyTime(): Long {
        val calendar = Calendar.getInstance()
        calendar[Calendar.SECOND] = 0
        calendar[Calendar.MINUTE] = 0
        calendar[Calendar.HOUR_OF_DAY] = 0
        calendar[Calendar.MILLISECOND] = 0
        return calendar.timeInMillis
    }
}