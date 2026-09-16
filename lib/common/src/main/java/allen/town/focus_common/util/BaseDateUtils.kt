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

    @JvmStatic
    fun getDayBefore(days: Int): Long {
        val instance = Calendar.getInstance()
        instance.add(Calendar.DAY_OF_MONTH, -days)
        return instance.timeInMillis
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

    /**
     * Get today's midnight in millis.
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