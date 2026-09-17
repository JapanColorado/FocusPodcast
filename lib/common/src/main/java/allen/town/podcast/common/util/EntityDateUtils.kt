package allen.town.podcast.common.util

import allen.town.podcast.common.R
import android.content.Context
import android.text.format.DateUtils
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

// detekt: these are date parsing/formatting helpers over untrusted strings. Every one of
// them documents a fallback value for unparseable input, so a broad catch is the contract.
@Suppress("TooGenericExceptionCaught")
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
            // ISO 8601 is a machine format: Locale.ROOT keeps the digits and field order fixed.
            val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ROOT)
            simpleDateFormat.timeZone = TimeZone.getTimeZone("UTC")
            currentTimeMillis = simpleDateFormat.parse(time).time
        } catch (e: Exception) {
            // Unparseable input: fall back to "now", which is what the initial value holds.
            Timber.e(e, "getTimeFromIso8601")
        }
        return currentTimeMillis
    }

    @JvmStatic
    fun get8601Time(time: Long): String {
        var timeStr = ""
        try {
            // ISO 8601 is a machine format: Locale.ROOT keeps the digits and field order fixed.
            val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ROOT)
            simpleDateFormat.timeZone = TimeZone.getTimeZone("UTC")
            timeStr = simpleDateFormat.format(Date(time))
        } catch (e: Exception) {
            // "" is the documented "no timestamp" answer for callers.
            Timber.e(e, "get8601Time")
        }
        return timeStr
    }

    /**
     * Get the date a number of months before or after the given one.
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
        val dft = SimpleDateFormat(format, Locale.getDefault())
        val date = Calendar.getInstance()
        date.time = beginDate
        date[Calendar.MONTH] = date[Calendar.MONTH] + distanceMonth
        val endDate: Date = try {
            dft.parse(dft.format(date.time))
        } catch (e: java.lang.Exception) {
            // The caller-supplied pattern cannot round-trip its own output. Answer null rather
            // than falling through to format(null), which used to throw a NullPointerException.
            Timber.e(e, "could not apply the %s month offset", distanceMonth)
            return null
        }
        return dft.format(endDate)
    }

    /**
     * Get the date N days before or after the given one.
     *
     * @param beginDate
     * @param distanceDay days offset, e.g. -7 for seven days earlier, 7 for seven days later
     * @param format      date format, defaults to "yyyy-MM-dd"
     * @return
     */
    fun getOldDateByDay(beginDate: Date?, distanceDay: Int, format: String?): String? {
        var format = format
        if (format == null || format.isEmpty()) {
            format = "yyyy-MM-dd"
        }
        val dft = SimpleDateFormat(format, Locale.getDefault())
        val date = Calendar.getInstance()
        date.time = beginDate
        date[Calendar.DATE] = date[Calendar.DATE] + distanceDay
        val endDate: Date = try {
            dft.parse(dft.format(date.time))
        } catch (e: java.lang.Exception) {
            // Same as getOldDateByMonth: answer null instead of formatting a null Date.
            Timber.e(e, "could not apply the %s day offset", distanceDay)
            return null
        }
        return dft.format(endDate)
    }

    /**
     * Convert a date string into a unix timestamp string.
     */
    @JvmStatic
    fun date2TimeStamp(date: String?, format: String?): String? {
        try {
            // Parses a machine-formatted date, so the pattern must not be localised.
            val sdf = SimpleDateFormat(format, Locale.ROOT)
            return (sdf.parse(date).time / 1000).toString()
        } catch (e: java.lang.Exception) {
            // Unparseable input; "" is the documented "no timestamp" answer for callers.
            Timber.e(e, "could not parse %s as %s", date, format)
        }
        return ""
    }

    /**
     * Format a timestamp as a date string.
     */
    @JvmStatic
    fun timeStamp2Date(time: Long, format: String? = "yyyy-MM-dd"): String? {
        val sdf = SimpleDateFormat(format, Locale.getDefault())
        return sdf.format(Date(time))
    }

    /**
     * Add days to from.
     */
    @JvmStatic
    fun addDays(from: String?, days: Int): String? {
        // "yyyy-MM-dd" is a machine format, so it must not be localised.
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        try {
            val d = Date(f.parse(from).time + 24L * 3600 * 1000 * days)
            return f.format(d)
        } catch (ex: Exception) {
            // Unparseable input: "" is the documented "no date" answer for callers.
            Timber.e(ex, "addDays")
        }
        return ""
    }

    /**
     * Add days to from.
     * Careful: days must be a Long, otherwise the arithmetic is done in int and overflows.
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
                // 0 is the documented "no date" answer, matching the null-date branch below.
                Timber.e(ex, "addDays")
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
     * Whether the timestamp falls on yesterday.
     */
    @JvmStatic
    fun isYesterday(timeStamp: Long?): Boolean {
        val todayCalendar = Calendar.getInstance()
        // No timestamp cannot be yesterday.
        val millis = timeStamp ?: return false
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
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
        // Different year: show the full date including the year
        if (!isCurrentYear(date)) {
            dateInstance = SimpleDateFormat.getDateInstance(2)
            return dateInstance.format(date)
        } else if (DateUtils.isToday(date.time)) {
            // Today
            return context.getString(R.string.today)

        } else if (isYesterday(date.time)) {
            // Yesterday
            return context.getString(R.string.yesterday)
        } else {
            val df = SimpleDateFormat("MMM dd", Locale.getDefault())
            return df.format(date)

        }
    }

    @JvmStatic
    fun getToday(): String {
        val df = SimpleDateFormat("dd", Locale.getDefault())
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
     * Get the start of "today".
     * Counts back 24 hours from now rather than starting at midnight.
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
        // Compared lexically against caller-supplied "HH:mm" strings, so the digits must not
        // be localised.
        // Without an end of the range there is no window to be inside of.
        val end = str2 ?: return false
        val format = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date())
        return if (str.compareTo(end) >= 0) {
            format.compareTo(str) >= 0 || format.compareTo(end) <= 0
        } else !(format.compareTo(str) < 0 || format.compareTo(end) > 0)
    }




}