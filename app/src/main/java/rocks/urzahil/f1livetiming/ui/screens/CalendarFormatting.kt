package rocks.urzahil.f1livetiming.ui.screens

import rocks.urzahil.f1livetiming.data.model.scheduleUtcMillis
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Local date ("Sun, 8 Mar 2026") and time ("05:00") of a calendar entry; the time is blank when it has none. */
internal fun formatUtcToLocal(dateStr: String?, timeStr: String?): Pair<String, String> =
    formatLocal(dateStr, timeStr, datePattern = "EEE, d MMM yyyy", withTime = !timeStr.isNullOrBlank())

/** Local weekday ("Sun") and time ("05:00") of a calendar entry, for the session pills. */
internal fun formatUtcToLocalShort(dateStr: String?, timeStr: String?): Pair<String, String> =
    formatLocal(dateStr, timeStr, datePattern = "EEE", withTime = true)

private fun formatLocal(dateStr: String?, timeStr: String?, datePattern: String, withTime: Boolean): Pair<String, String> {
    if (dateStr.isNullOrBlank()) return Pair("--", "")
    // Without a time, noon UTC keeps the local date on the calendar day in nearly every time zone.
    val millis = scheduleUtcMillis(dateStr, timeStr, defaultTime = "12:00:00")
        ?: return Pair(dateStr, timeStr.orEmpty())
    val date = Date(millis)
    val localDate = SimpleDateFormat(datePattern, Locale.getDefault()).format(date)
    val localTime = if (withTime) SimpleDateFormat("HH:mm", Locale.getDefault()).format(date) else ""
    return Pair(localDate, localTime)
}

/** True once the session's UTC start time has passed; unparseable or missing dates count as not past. */
internal fun isSessionInPast(dateStr: String?, timeStr: String?): Boolean =
    scheduleUtcMillis(dateStr, timeStr, defaultTime = "23:59:59")?.let { it < System.currentTimeMillis() } ?: false

internal fun isDateInFutureOrToday(dateStr: String?): Boolean {
    if (dateStr.isNullOrBlank()) return false
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val d = sdf.parse(dateStr.trim().take(10)) ?: return false
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        !d.before(cal.time)
    } catch (_: Exception) {
        false
    }
}
