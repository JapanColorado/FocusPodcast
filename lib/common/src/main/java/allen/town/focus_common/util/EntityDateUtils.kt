package allen.town.focus_common.util

import allen.town.focus_common.R
import android.content.Context
import android.text.format.DateUtils
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

object EntityDateUtils {
    @JvmStatic
    fun isCurrentYear(time: Long): Boolean {
        val now = Calendar.getInstance()
        val currentYear = now[Calendar.YEAR]
        now.timeInMillis = time
        val year = now[Calendar.YEAR]
        return currentYear == year
    }

    @JvmStatic
    fun getTimeFromIso8601(time: String): Long {
        var currentTimeMillis = System.currentTimeMillis()
        try {
            var simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'");
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT+0:00"));
            currentTimeMillis = simpleDateFormat.parse(time).getTime();
        } catch (e: Exception) {
            Timber.e(e, "getTimeFromIso8601")
        }
        return currentTimeMillis
    }

    @JvmStatic
    fun get8601Time(time: Long): String {
        var timeStr = ""
        try {
            var simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT+0:00"))
            timeStr = simpleDateFormat.format(Date(time))
        } catch (e: Exception) {
            Timber.e(e, "get8601Time")
        }
        return timeStr
    }

    /**
     * 日期字符串转换Date实体
     */
    @JvmStatic
    fun parseServerTime(serverTime: String?, format: String?): Date? {
        var format = format
        if (format == null || format.isEmpty()) {
            format = "yyyy-MM-dd HH:mm:ss"
        }
        val sdf = SimpleDateFormat(format, Locale.CHINESE)
        sdf.timeZone = TimeZone.getTimeZone("GMT+8:00")
        var date: Date? = null
        try {
            date = sdf.parse(serverTime)
        } catch (e: java.lang.Exception) {
            Timber.e(e, "")
        }
        return date
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

    /**
     * 在from的基础上加上days
     */
    @JvmStatic
    fun addDays(from: String?, days: Int): String? {
        val f = SimpleDateFormat("yyyy-MM-dd")
        try {
            val d = Date(f.parse(from).getTime() + 24 * 3600 * 1000 * days)
            f.format(d)
        } catch (ex: Exception) {
            Timber.e("addOneday", ex)
        }
        return ""
    }

    /**
     * 在from的基础上加上days
     * 这里有个坑如果不声明为Long,计算默认当做int类型处理的
     */
    @JvmStatic
    fun addDays(date: Date?, days: Long): Long {
        return date?.run {
            try {
                Timber.i("purchase time= ${time}")
                val addedTime: Long = 24 * 3600 * 1000 * days
                Timber.i("added time= ${addedTime}")
                return time + addedTime
            } catch (ex: Exception) {
                Timber.e("addDays", ex)
            }

            0L
        } ?: 0L

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
    fun getEntityDateStr(date: Date, context: Context): String {
        val calendar = Calendar.getInstance()
        calendar.time = Date(System.currentTimeMillis())
        var dateInstance: DateFormat
        //不是同一年显示包含年份完整的日期
        if (!isCurrentYear(date)) {
            dateInstance = SimpleDateFormat.getDateInstance(2)
            return dateInstance.format(date)
        } else if (DateUtils.isToday(date.time)) {
            //今天
            return context.getString(R.string.today)

        } else if (isYesterday(date.time)) {
            //昨天
            return context.getString(R.string.yesterday)
        } else {
            val df = SimpleDateFormat("MMM dd")
            return df.format(date)

        }
    }

    @JvmStatic
    fun getToday(): String {
        val df = SimpleDateFormat("dd")
        return df.format(Date())
    }

    @JvmStatic
    fun getDayBefore(days: Int, actionStr: String = ""): String {
        val instance = Calendar.getInstance()
        instance.add(Calendar.DAY_OF_MONTH, -days)
        Timber.d("${actionStr} %s", instance.time)
        return instance.timeInMillis.toString()
    }

    /**
     * 获取今天开始时间
     * 不以当天0点开始计算而是从现在往前24小时
     */
    @JvmStatic
    fun getStartTime(): String? {
/*        val todayStart = Calendar.getInstance()
        todayStart.time = Date()
        todayStart[Calendar.HOUR_OF_DAY] = 0
        todayStart[Calendar.MINUTE] = 0
        todayStart[Calendar.SECOND] = 0
        todayStart[Calendar.MILLISECOND] = 0
        return todayStart.time.time.toString()*/
        return getDayBefore(1)
    }

    @JvmStatic
    fun inTime(str: String, str2: String?): Boolean {
        val format = SimpleDateFormat("HH:mm").format(Date())
        return if (str.compareTo(str2!!) >= 0) {
            format.compareTo(str) >= 0 || format.compareTo(str2) <= 0
        } else !(format.compareTo(str) < 0 || format.compareTo(str2) > 0)
    }




}