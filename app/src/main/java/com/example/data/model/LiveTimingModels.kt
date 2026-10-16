package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Models for the self-hosted f1-livetiming-api backend (SignalR live proxy)
 */

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

@JsonClass(generateAdapter = true)
data class LiveSessionResponse(
    @Json(name = "Meeting") val meeting: MeetingInfo? = null,
    @Json(name = "Session") val session: SessionInfo? = null,
    @Json(name = "SessionName") val sessionName: String? = null,
    @Json(name = "SessionType") val sessionType: String? = null,
    @Json(name = "TotalLaps") val totalLaps: Int? = null,
    @Json(name = "CurrentLap") val currentLap: Int? = null,
    @Json(name = "Status") val status: String? = null
)

@JsonClass(generateAdapter = true)
data class MeetingInfo(
    @Json(name = "Key") val key: Any? = null,
    @Json(name = "Name") val name: String? = null,
    @Json(name = "OfficialName") val officialName: String? = null,
    @Json(name = "Location") val location: String? = null,
    @Json(name = "Country") val country: CountryInfo? = null
)

@JsonClass(generateAdapter = true)
data class CountryInfo(
    @Json(name = "Code") val code: String? = null,
    @Json(name = "Name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class SessionInfo(
    @Json(name = "Key") val key: Any? = null,
    @Json(name = "Name") val name: String? = null,
    @Json(name = "Type") val type: String? = null,
    @Json(name = "Status") val status: String? = null
)

@JsonClass(generateAdapter = true)
data class DriverTelemetryItem(
    @Json(name = "Position") val position: String? = null,
    @Json(name = "RacingNumber") val racingNumber: String? = null,
    @Json(name = "BroadcastName") val broadcastName: String? = null,
    @Json(name = "Tla") val tla: String? = null,
    @Json(name = "FullName") val fullName: String? = null,
    @Json(name = "FirstName") val firstName: String? = null,
    @Json(name = "LastName") val lastName: String? = null,
    @Json(name = "TeamName") val teamName: String? = null,
    @Json(name = "TeamColour") val teamColour: String? = null,
    
    // Race-specific timings
    @Json(name = "GapToLeader") val gapToLeader: Any? = null,
    @Json(name = "IntervalToPositionAhead") val intervalToPositionAhead: Any? = null,
    
    // Practice & Qualifying timings
    @Json(name = "BestLapTime") val bestLapTime: Any? = null,
    @Json(name = "LastLapTime") val lastLapTime: Any? = null,
    @Json(name = "TimeDiffToFastest") val timeDiffToFastest: String? = null,
    @Json(name = "TimeDiffToPositionAhead") val timeDiffToPositionAhead: String? = null,
    
    // Sectors
    @Json(name = "Sectors") val sectors: List<SectorItem>? = null,
    
    // Status flags
    @Json(name = "InPit") val inPit: Boolean? = null,
    @Json(name = "PitOut") val pitOut: Boolean? = null,
    @Json(name = "Stopped") val stopped: Boolean? = null,
    @Json(name = "Retired") val retired: Boolean? = null,
    @Json(name = "NumberOfPitStops") val numberOfPitStops: Int? = null,
    @Json(name = "NumberOfLaps") val numberOfLaps: Int? = null
)

@JsonClass(generateAdapter = true)
data class SectorItem(
    @Json(name = "Value") val value: String? = null,
    @Json(name = "OverallFastest") val overallFastest: Boolean? = null,
    @Json(name = "PersonalFastest") val personalFastest: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class StintItem(
    @Json(name = "Compound") val compound: String? = null,
    @Json(name = "TotalLaps") val totalLaps: Int? = null,
    @Json(name = "New") val isNew: Any? = null,
    @Json(name = "StartLaps") val startLaps: Int? = null
)

@JsonClass(generateAdapter = true)
data class DriverTyreStints(
    @Json(name = "RacingNumber") val racingNumber: String? = null,
    @Json(name = "Stints") val stints: List<StintItem>? = null
)

@JsonClass(generateAdapter = true)
data class LiveWeatherResponse(
    @Json(name = "AirTemp") val airTemp: String? = null,
    @Json(name = "TrackTemp") val trackTemp: String? = null,
    @Json(name = "Humidity") val humidity: String? = null,
    @Json(name = "Pressure") val pressure: String? = null,
    @Json(name = "WindSpeed") val windSpeed: String? = null,
    @Json(name = "WindDirection") val windDirection: String? = null,
    @Json(name = "Rainfall") val rainfall: String? = null
)

@JsonClass(generateAdapter = true)
data class LiveRaceControlItem(
    @Json(name = "Utc") val utc: String? = null,
    @Json(name = "Category") val category: String? = null,
    @Json(name = "Message") val message: String? = null,
    @Json(name = "Flag") val flag: String? = null,
    @Json(name = "Scope") val scope: String? = null
)
