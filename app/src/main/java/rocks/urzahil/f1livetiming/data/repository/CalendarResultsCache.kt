package rocks.urzahil.f1livetiming.data.repository

import rocks.urzahil.f1livetiming.data.model.PracticeTimingItem
import rocks.urzahil.f1livetiming.data.model.QualifyingResultItem
import rocks.urzahil.f1livetiming.data.model.RaceResultItem

/** Owns the five expiring caches used by calendar session/results screens. */
internal class CalendarResultsCache {
    val race = ExpiringListCache<String, RaceResultItem>()
    val qualifying = ExpiringListCache<String, QualifyingResultItem>()
    val sprint = ExpiringListCache<String, RaceResultItem>()
    val sprintQualifying = ExpiringListCache<String, QualifyingResultItem>()
    val practice = ExpiringListCache<String, PracticeTimingItem>()
}
