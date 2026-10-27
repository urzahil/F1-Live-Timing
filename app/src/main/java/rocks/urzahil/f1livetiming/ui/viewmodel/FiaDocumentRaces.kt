package rocks.urzahil.f1livetiming.ui.viewmodel

import rocks.urzahil.f1livetiming.data.model.SeasonRaceItem
import rocks.urzahil.f1livetiming.data.model.scheduleUtcMillis

/** Picks the races of the season that can have FIA documents, for the "FIA Documents" tool. */
object FiaDocumentRaces {

    /** Races whose weekend has started, most recent first; these are always listed. */
    fun started(races: List<SeasonRaceItem>, nowMillis: Long): List<SeasonRaceItem> =
        races
            .filter { race -> weekendStartMillis(race)?.let { it <= nowMillis } ?: false }
            .sortedByDescending { it.round?.toIntOrNull() ?: 0 }

    /**
     * The next weekend that has not started yet. The FIA publishes its first documents a few days before
     * first practice, so it is listed only once the backend returns documents for it.
     */
    fun next(races: List<SeasonRaceItem>, nowMillis: Long): SeasonRaceItem? =
        races
            .mapNotNull { race -> weekendStartMillis(race)?.let { start -> race to start } }
            .filter { (_, start) -> start > nowMillis }
            .minByOrNull { (_, start) -> start }
            ?.first

    /** First practice start, or the race start for a weekend listed without practice sessions. */
    internal fun weekendStartMillis(race: SeasonRaceItem): Long? {
        val session = race.firstPractice
        val date = session?.date ?: race.date
        val time = if (session?.date != null) session.time else race.time
        return scheduleUtcMillis(date, time)
    }
}
