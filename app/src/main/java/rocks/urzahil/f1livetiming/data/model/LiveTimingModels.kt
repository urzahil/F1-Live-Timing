package rocks.urzahil.f1livetiming.data.model

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson

/** Reply of the self-hosted f1-livetiming-api backend's GET /api/status. */
@JsonClass(generateAdapter = true)
data class LiveStatusResponse(
    @Json(name = "status") val status: String? = null,
    @Json(name = "service") val service: String? = null,
    @Json(name = "isConnected") val isConnected: Boolean? = null,
    @Json(name = "sessionActive") val sessionActive: Boolean? = null,
    @Json(name = "live") val live: Boolean? = null,
    @Json(name = "lastMessageAt") val lastMessageAt: String? = null,
    @Json(name = "uptimeSeconds") val uptimeSeconds: Long? = null,
    @Json(name = "message") val message: String? = null
)

/**
 * Reply of the backend's GET /api/live (docker/live/live-snapshot.js): everything the live timing screen
 * shows, already interpreted and ready to display. Every field has a default, so fields a newer backend adds
 * are ignored and ones it leaves out do not fail the whole reply.
 */
@JsonClass(generateAdapter = true)
data class LiveSnapshotResponse(
    /** Raised by the backend only when a field changes meaning or is removed. */
    val schemaVersion: Int = 0,
    val generatedAt: Long = 0L,
    val session: ParsedSession = ParsedSession(),
    val trackStatus: TrackStatus = TrackStatus.CLEAR,
    val weather: ParsedWeather = ParsedWeather(),
    /** Latest race control messages, newest first. */
    val raceControl: List<ParsedRaceControlMessage> = emptyList(),
    /** Driver table in display order. */
    val drivers: List<ParsedDriverTiming> = emptyList()
) {
    companion object {
        /** The newest [schemaVersion] this app can display. */
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

@JsonClass(generateAdapter = true)
data class ParsedSession(
    val meetingName: String = "",
    val sessionName: String = "",
    val sessionType: String = "", // Race, Practice, Qualifying, Sprint or empty
    val currentLap: Int = 0,
    val totalLaps: Int = 0,
    /** "STARTED", "SESSION FINISHED", "NO ACTIVE SESSION" or the feed's own status in capitals. */
    val status: String = "NO ACTIVE SESSION",
    val isLive: Boolean = false,
    /** Practice, Qualifying and Sprint Qualifying/Shootout run on time rather than a lap count. */
    val isTimed: Boolean = false,
    /** "Q1"/"Q2"/"Q3" (or "SQ1"… for sprint qualifying), or null outside qualifying. */
    val qualifyingPart: String? = null,
    val clock: ParsedSessionClock? = null
)

/** Whether the backend named a session at all, finished or not. */
val ParsedSession.hasIdentity: Boolean
    get() = meetingName.isNotBlank() || sessionName.isNotBlank() || sessionType.isNotBlank()

/**
 * Session clock as last reported by the feed: [remainingMillis] were left at [utcMillis]. The feed
 * only reports changes, so while [extrapolating] the clock keeps counting down from that point.
 */
@JsonClass(generateAdapter = true)
data class ParsedSessionClock(
    val remainingMillis: Long,
    val utcMillis: Long,
    val extrapolating: Boolean = false
) {
    fun remainingAt(nowMillis: Long): Long =
        if (extrapolating) (remainingMillis - (nowMillis - utcMillis)).coerceAtLeast(0L) else remainingMillis
}

@JsonClass(generateAdapter = true)
data class ParsedStint(
    val stintNumber: Int = 0,
    val compound: String = "UNKNOWN",
    val totalLaps: Int = 0,
    val isNew: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ParsedDriverTiming(
    val position: String = "",
    val racingNumber: String = "",
    val driverCode: String = "DRV",
    val fullName: String = "",
    val teamName: String = "",
    val teamColorHex: String = "",
    val gapToLeader: String = "--",
    val intervalToAhead: String = "--",
    val bestLapTime: String = "--",
    val lastLapTime: String = "--",
    val sector1: String = "--",
    val sector2: String = "--",
    val sector3: String = "--",
    val inPit: Boolean = false,
    val pitStops: Int = 0,
    val totalLaps: Int = 0,
    val isRetired: Boolean = false,
    val isStopped: Boolean = false,
    val currentCompound: String = "--",
    val currentCompoundAge: Int = 0,
    val isCurrentCompoundNew: Boolean = false,
    val stintsHistory: List<ParsedStint> = emptyList(),
    val isOverallBestLap: Boolean = false,
    /** Gaining (true) or losing (false) on the car ahead; null when not chasing one. */
    val isCatchingAhead: Boolean? = null,
    /** Qualifying only: eliminated in Q1 or Q2. The screen hides these rows. */
    val isKnockedOut: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ParsedWeather(
    val airTemp: String = "--",
    val trackTemp: String = "--",
    val humidity: String = "--",
    val pressure: String = "--",
    val windSpeed: String = "--",
    val windDirection: String = "--",
    val isRaining: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ParsedRaceControlMessage(
    /** When the message was published, in epoch millis; null when the feed gave no time. */
    val utcMillis: Long? = null,
    val category: String = "",
    val flag: String = "",
    val message: String = "",
    val scope: String = ""
)

/** Track-wide condition shown in the header status pill. */
enum class TrackStatus { CLEAR, SAFETY_CAR, VIRTUAL_SAFETY_CAR, RED_FLAG }

/** Reads [TrackStatus] by name; a value this app does not know yet reads as [TrackStatus.CLEAR]. */
object TrackStatusAdapter {
    @FromJson
    fun fromJson(value: String?): TrackStatus =
        TrackStatus.entries.firstOrNull { it.name == value } ?: TrackStatus.CLEAR

    @ToJson
    fun toJson(status: TrackStatus): String = status.name
}
