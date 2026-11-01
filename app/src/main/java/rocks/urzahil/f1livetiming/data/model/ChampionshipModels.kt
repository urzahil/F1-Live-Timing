package rocks.urzahil.f1livetiming.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Data models for Ergast/Jolpica public F1 API (Season Championship & Calendar)
 */

/** A standings table as returned by the repository, with the round it was last updated after. */
data class StandingsResult<T>(
    val season: String,
    val items: List<T>,
    val round: Int?
)

// --- Driver Standings ---

@JsonClass(generateAdapter = true)
data class DriverStandingsRoot(
    @Json(name = "MRData") val mrData: DriverStandingsMRData? = null
)

@JsonClass(generateAdapter = true)
data class DriverStandingsMRData(
    @Json(name = "season") val season: String? = null,
    @Json(name = "StandingsTable") val standingsTable: DriverStandingsTable? = null
)

@JsonClass(generateAdapter = true)
data class DriverStandingsTable(
    @Json(name = "season") val season: String? = null,
    @Json(name = "StandingsLists") val standingsLists: List<DriverStandingsList>? = null
)

@JsonClass(generateAdapter = true)
data class DriverStandingsList(
    @Json(name = "season") val season: String? = null,
    @Json(name = "round") val round: String? = null,
    @Json(name = "DriverStandings") val driverStandings: List<DriverStandingItem>? = null
)

@JsonClass(generateAdapter = true)
data class DriverStandingItem(
    @Json(name = "position") val position: String? = null,
    @Json(name = "positionText") val positionText: String? = null,
    @Json(name = "points") val points: String? = null,
    @Json(name = "wins") val wins: String? = null,
    @Json(name = "Driver") val driver: F1Driver? = null,
    @Json(name = "Constructors") val constructors: List<F1Constructor>? = null
)

@JsonClass(generateAdapter = true)
data class F1Driver(
    @Json(name = "driverId") val driverId: String? = null,
    @Json(name = "permanentNumber") val permanentNumber: String? = null,
    @Json(name = "code") val code: String? = null,
    @Json(name = "givenName") val givenName: String? = null,
    @Json(name = "familyName") val familyName: String? = null,
    @Json(name = "dateOfBirth") val dateOfBirth: String? = null,
    @Json(name = "nationality") val nationality: String? = null,
    @Json(name = "HeadshotUrl") val headshotUrl: String? = null
) {
    val fullName: String
        get() = "${givenName.orEmpty()} ${familyName.orEmpty()}".trim()
}

@JsonClass(generateAdapter = true)
data class F1Constructor(
    @Json(name = "constructorId") val constructorId: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "nationality") val nationality: String? = null
)

// --- Constructor Standings ---

@JsonClass(generateAdapter = true)
data class ConstructorStandingsRoot(
    @Json(name = "MRData") val mrData: ConstructorStandingsMRData? = null
)

@JsonClass(generateAdapter = true)
data class ConstructorStandingsMRData(
    @Json(name = "season") val season: String? = null,
    @Json(name = "StandingsTable") val standingsTable: ConstructorStandingsTable? = null
)

@JsonClass(generateAdapter = true)
data class ConstructorStandingsTable(
    @Json(name = "season") val season: String? = null,
    @Json(name = "StandingsLists") val standingsLists: List<ConstructorStandingsList>? = null
)

@JsonClass(generateAdapter = true)
data class ConstructorStandingsList(
    @Json(name = "season") val season: String? = null,
    @Json(name = "round") val round: String? = null,
    @Json(name = "ConstructorStandings") val constructorStandings: List<ConstructorStandingItem>? = null
)

@JsonClass(generateAdapter = true)
data class ConstructorStandingItem(
    @Json(name = "position") val position: String? = null,
    @Json(name = "positionText") val positionText: String? = null,
    @Json(name = "points") val points: String? = null,
    @Json(name = "wins") val wins: String? = null,
    @Json(name = "Constructor") val constructor: F1Constructor? = null
)

// --- Season Calendar & Weekend Sessions ---

@JsonClass(generateAdapter = true)
data class SeasonCalendarRoot(
    @Json(name = "MRData") val mrData: SeasonCalendarMRData? = null
)

@JsonClass(generateAdapter = true)
data class SeasonCalendarMRData(
    @Json(name = "season") val season: String? = null,
    @Json(name = "RaceTable") val raceTable: SeasonRaceTable? = null
)

@JsonClass(generateAdapter = true)
data class SeasonRaceTable(
    @Json(name = "season") val season: String? = null,
    @Json(name = "Races") val races: List<SeasonRaceItem>? = null
)

@JsonClass(generateAdapter = true)
data class SessionSchedule(
    @Json(name = "date") val date: String? = null,
    @Json(name = "time") val time: String? = null
)

@JsonClass(generateAdapter = true)
data class SeasonRaceItem(
    @Json(name = "season") val season: String? = null,
    @Json(name = "round") val round: String? = null,
    @Json(name = "raceName") val raceName: String? = null,
    @Json(name = "Circuit") val circuit: CircuitItem? = null,
    @Json(name = "date") val date: String? = null,
    @Json(name = "time") val time: String? = null,
    @Json(name = "FirstPractice") val firstPractice: SessionSchedule? = null,
    @Json(name = "SecondPractice") val secondPractice: SessionSchedule? = null,
    @Json(name = "ThirdPractice") val thirdPractice: SessionSchedule? = null,
    @Json(name = "Qualifying") val qualifying: SessionSchedule? = null,
    @Json(name = "Sprint") val sprint: SessionSchedule? = null,
    @Json(name = "SprintQualifying") val sprintQualifying: SessionSchedule? = null,
    @Json(name = "SprintShootout") val sprintShootout: SessionSchedule? = null
)

@JsonClass(generateAdapter = true)
data class CircuitItem(
    @Json(name = "circuitId") val circuitId: String? = null,
    @Json(name = "circuitName") val circuitName: String? = null,
    @Json(name = "Location") val location: CircuitLocation? = null
)

@JsonClass(generateAdapter = true)
data class CircuitLocation(
    @Json(name = "lat") val lat: String? = null,
    @Json(name = "long") val long: String? = null,
    @Json(name = "locality") val locality: String? = null,
    @Json(name = "country") val country: String? = null
)

// --- Session Results Models ---

@JsonClass(generateAdapter = true)
data class SessionResultsRoot(
    @Json(name = "MRData") val mrData: SessionResultsMRData? = null
)

@JsonClass(generateAdapter = true)
data class SessionResultsMRData(
    @Json(name = "RaceTable") val raceTable: SessionResultsRaceTable? = null
)

@JsonClass(generateAdapter = true)
data class SessionResultsRaceTable(
    @Json(name = "round") val round: String? = null,
    @Json(name = "Races") val races: List<RaceWithSessionResults>? = null
)

@JsonClass(generateAdapter = true)
data class RaceWithSessionResults(
    @Json(name = "raceName") val raceName: String? = null,
    @Json(name = "Results") val results: List<RaceResultItem>? = null,
    @Json(name = "QualifyingResults") val qualifyingResults: List<QualifyingResultItem>? = null,
    @Json(name = "SprintResults") val sprintResults: List<RaceResultItem>? = null
)

@JsonClass(generateAdapter = true)
data class RaceResultItem(
    @Json(name = "number") val number: String? = null,
    @Json(name = "position") val position: String? = null,
    @Json(name = "positionText") val positionText: String? = null,
    @Json(name = "points") val points: String? = null,
    @Json(name = "Driver") val driver: F1Driver? = null,
    @Json(name = "Constructor") val constructor: F1Constructor? = null,
    @Json(name = "grid") val grid: String? = null,
    @Json(name = "laps") val laps: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "Time") val time: ResultTime? = null,
    @Json(name = "FastestLap") val fastestLap: FastestLapItem? = null
)

@JsonClass(generateAdapter = true)
data class ResultTime(
    @Json(name = "millis") val millis: String? = null,
    @Json(name = "time") val time: String? = null
)

@JsonClass(generateAdapter = true)
data class FastestLapItem(
    @Json(name = "rank") val rank: String? = null,
    @Json(name = "lap") val lap: String? = null,
    @Json(name = "Time") val time: ResultTime? = null
)

@JsonClass(generateAdapter = true)
data class QualifyingResultItem(
    @Json(name = "number") val number: String? = null,
    @Json(name = "position") val position: String? = null,
    @Json(name = "Driver") val driver: F1Driver? = null,
    @Json(name = "Constructor") val constructor: F1Constructor? = null,
    @Json(name = "Q1") val q1: String? = null,
    @Json(name = "Q2") val q2: String? = null,
    @Json(name = "Q3") val q3: String? = null
)

@JsonClass(generateAdapter = true)
data class PracticeTimingItem(
    val position: String,
    val driverNumber: String,
    val driver: F1Driver? = null,
    val constructor: F1Constructor? = null,
    val bestLapTime: String = "",
    val gapToLeader: String = "",
    val lapsCompleted: Int = 0,
    val sector1: String? = null,
    val sector2: String? = null,
    val sector3: String? = null
)

// --- Weekend Sessions & Structure ---

enum class WeekendSessionType(val title: String, val shortBadge: String) {
    FP1("Free Practice 1", "FP1"),
    FP2("Free Practice 2", "FP2"),
    FP3("Free Practice 3", "FP3"),
    SPRINT_QUALIFYING("Sprint Qualifying", "SQ"),
    SPRINT("Sprint Race", "SPRINT"),
    QUALIFYING("Qualifying", "QUAL"),
    RACE("Grand Prix Race", "RACE")
}

data class WeekendSessionInfo(
    val type: WeekendSessionType,
    val date: String?,
    val time: String?
)

fun SeasonRaceItem.getAllSessions(): List<WeekendSessionInfo> {
    val list = mutableListOf<WeekendSessionInfo>()

    firstPractice?.let {
        list.add(WeekendSessionInfo(WeekendSessionType.FP1, it.date, it.time))
    }

    val sq = sprintQualifying ?: sprintShootout
    if (sq != null) {
        list.add(WeekendSessionInfo(WeekendSessionType.SPRINT_QUALIFYING, sq.date, sq.time))
    } else if (secondPractice != null) {
        list.add(WeekendSessionInfo(WeekendSessionType.FP2, secondPractice.date, secondPractice.time))
    }

    sprint?.let {
        list.add(WeekendSessionInfo(WeekendSessionType.SPRINT, it.date, it.time))
    }

    if (thirdPractice != null && sprint == null) {
        list.add(WeekendSessionInfo(WeekendSessionType.FP3, thirdPractice.date, thirdPractice.time))
    }

    qualifying?.let {
        list.add(WeekendSessionInfo(WeekendSessionType.QUALIFYING, it.date, it.time))
    }

    list.add(WeekendSessionInfo(WeekendSessionType.RACE, date, time))

    return list.sortedWith(
        compareBy<WeekendSessionInfo> { it.date.orEmpty() }
            .thenBy { it.time.orEmpty() }
    )
}

/**
 * Epoch millis of a calendar entry: [date] such as "2026-03-08" and [time] such as "04:00:00Z" or "04:00",
 * both UTC. [defaultTime] stands in for a missing time. Null when the date is missing or unparseable.
 */
internal fun scheduleUtcMillis(date: String?, time: String?, defaultTime: String = "00:00:00"): Long? {
    if (date.isNullOrBlank()) return null
    val cleanTime = time?.replace("Z", "")?.trim().takeUnless { it.isNullOrBlank() }
        ?.let { if (it.length == 5) "$it:00" else it } ?: defaultTime
    return try {
        // A fresh formatter per call: SimpleDateFormat is not thread-safe.
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse("${date.trim()}T$cleanTime")?.time
    } catch (_: Exception) {
        null
    }
}
