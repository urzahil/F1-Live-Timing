package rocks.urzahil.f1livetiming.ui.screens

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal val ColorxFFFFD700 = Color(0xFFFFD700)

internal fun formatUtcToLocal(dateStr: String?, timeStr: String?): Pair<String, String> {
    if (dateStr.isNullOrBlank()) return Pair("--", "")
    val time = if (!timeStr.isNullOrBlank()) {
        val clean = timeStr.replace("Z", "").trim()
        if (clean.length == 5) "$clean:00" else clean
    } else {
        "12:00:00"
    }

    return try {
        val utcIso = "${dateStr.trim()}T$time"
        val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsedDate = utcFormat.parse(utcIso) ?: return Pair(dateStr, timeStr.orEmpty())

        val localDateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        val localTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }

        val formattedDate = localDateFormat.format(parsedDate)
        val formattedTime = if (!timeStr.isNullOrBlank()) {
            localTimeFormat.format(parsedDate)
        } else {
            ""
        }

        Pair(formattedDate, formattedTime)
    } catch (_: Exception) {
        Pair(dateStr, timeStr.orEmpty())
    }
}

/** True once the session's UTC start time has passed; unparseable or missing dates count as not past. */
internal fun isSessionInPast(dateStr: String?, timeStr: String?): Boolean {
    if (dateStr.isNullOrBlank()) return false
    val time = timeStr?.replace("Z", "")?.trim().takeUnless { it.isNullOrBlank() }
        ?.let { if (it.length == 5) "$it:00" else it } ?: "23:59:59"
    return try {
        val start = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse("${dateStr.trim()}T$time") ?: return false
        start.time < System.currentTimeMillis()
    } catch (_: Exception) {
        false
    }
}

internal fun formatUtcToLocalShort(dateStr: String?, timeStr: String?): Pair<String, String> {
    if (dateStr.isNullOrBlank()) return Pair("--", "")
    val time = if (!timeStr.isNullOrBlank()) {
        val clean = timeStr.replace("Z", "").trim()
        if (clean.length == 5) "$clean:00" else clean
    } else {
        "12:00:00"
    }

    return try {
        val utcIso = "${dateStr.trim()}T$time"
        val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsedDate = utcFormat.parse(utcIso) ?: return Pair(dateStr, timeStr.orEmpty())

        val localDateFormat = SimpleDateFormat("EEE", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        val localTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }

        Pair(localDateFormat.format(parsedDate), localTimeFormat.format(parsedDate))
    } catch (_: Exception) {
        Pair(dateStr, timeStr.orEmpty())
    }
}

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
