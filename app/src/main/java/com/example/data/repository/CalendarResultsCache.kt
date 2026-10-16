package com.example.data.repository

import com.example.data.model.PracticeTimingItem
import com.example.data.model.QualifyingResultItem
import com.example.data.model.RaceResultItem

/** Owns the five expiring caches used by calendar session/results screens. */
internal class CalendarResultsCache {
    val race = ExpiringListCache<String, RaceResultItem>()
    val qualifying = ExpiringListCache<String, QualifyingResultItem>()
    val sprint = ExpiringListCache<String, RaceResultItem>()
    val sprintQualifying = ExpiringListCache<String, QualifyingResultItem>()
    val practice = ExpiringListCache<String, PracticeTimingItem>()

    fun clear() {
        race.clear()
        qualifying.clear()
        sprint.clear()
        sprintQualifying.clear()
        practice.clear()
    }
}
