package rocks.urzahil.f1livetiming.ui.viewmodel

import rocks.urzahil.f1livetiming.data.model.SeasonRaceItem
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Picks the races of the season that can have FIA documents, for the "FIA documents" tool. */
object FiaDocumentRaces {

    // The FIA starts publishing a weekend's documents a few days before first practice.
    private const val PUBLISHING_LEAD_MILLIS = 7 * 24 * 60 * 60 * 1000L

    /** Races whose weekend has started or starts within a week, most recent first. */
    fun listed(races: List<SeasonRaceItem>, nowMillis: Long): List<SeasonRaceItem> =
        races
            .filter { race ->
                val start = weekendStartMillis(race) ?: return@filter false
                start - PUBLISHING_LEAD_MILLIS <= nowMillis
            }
            .sortedByDescending { it.round?.toIntOrNull() ?: 0 }

    /** First practice start, or the race start for a weekend listed without practice sessions. */
    internal fun weekendStartMillis(race: SeasonRaceItem): Long? {
        val session = race.firstPractice
        val date = session?.date ?: race.date
        val time = if (session?.date != null) session.time else race.time
        return parseUtcMillis(date, time)
    }

    private fun parseUtcMillis(date: String?, time: String?): Long? {
        if (date.isNullOrBlank()) return null
        val cleanTime = time?.replace("Z", "")?.trim().takeUnless { it.isNullOrBlank() }
            ?.let { if (it.length == 5) "$it:00" else it } ?: "00:00:00"
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse("${date.trim()}T$cleanTime")?.time
        } catch (_: Exception) {
            null
        }
    }
}
