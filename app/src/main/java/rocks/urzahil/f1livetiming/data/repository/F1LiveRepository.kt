package rocks.urzahil.f1livetiming.data.repository

import android.util.Log
import rocks.urzahil.f1livetiming.data.api.NetworkClient
import rocks.urzahil.f1livetiming.data.model.LiveStatusResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

data class ParsedSession(
    val meetingName: String = "",
    val sessionName: String = "",
    val sessionType: String = "", // Race, Practice, Qualifying, Sprint or empty
    val currentLap: Int = 0,
    val totalLaps: Int = 0,
    val status: String = "NO ACTIVE SESSION",
    val isLive: Boolean = false,
    val clock: ParsedSessionClock? = null
)

/**
 * Session clock as last reported by the feed: [remainingMillis] were left at [utcMillis]. The feed
 * only reports changes, so while [extrapolating] the clock keeps counting down from that point.
 */
data class ParsedSessionClock(
    val remainingMillis: Long,
    val utcMillis: Long,
    val extrapolating: Boolean
) {
    fun remainingAt(nowMillis: Long): Long =
        if (extrapolating) (remainingMillis - (nowMillis - utcMillis)).coerceAtLeast(0L) else remainingMillis
}

data class ParsedStint(
    val stintNumber: Int,
    val compound: String,
    val totalLaps: Int,
    val isNew: Boolean
)

data class ParsedDriverTiming(
    val position: String,
    val racingNumber: String,
    val driverCode: String,
    val fullName: String,
    val teamName: String,
    val teamColorHex: String,
    val gapToLeader: String,
    val intervalToAhead: String,
    val bestLapTime: String,
    val lastLapTime: String,
    val sector1: String,
    val sector2: String,
    val sector3: String,
    val inPit: Boolean,
    val pitStops: Int,
    val totalLaps: Int,
    val isRetired: Boolean,
    val isStopped: Boolean,
    val currentCompound: String,
    val currentCompoundAge: Int,
    val isCurrentCompoundNew: Boolean,
    val stintsHistory: List<ParsedStint>,
    val isOverallBestLap: Boolean = false,
    val isCatchingAhead: Boolean? = null,
    /** Qualifying only: eliminated in Q1 or Q2. The screen hides these and counts them to tell which part is running. */
    val isKnockedOut: Boolean = false
)

data class ParsedWeather(
    val airTemp: String = "--",
    val trackTemp: String = "--",
    val humidity: String = "--",
    val pressure: String = "--",
    val windSpeed: String = "--",
    val windDirection: String = "--",
    val isRaining: Boolean = false
)

data class ParsedRaceControlMessage(
    val time: String, // Local wall-clock time "HH:mm:ss", or "" when the feed gave none
    val category: String,
    val flag: String,
    val message: String,
    val scope: String = ""
)

/** Track-wide condition shown in the header status pill. */
enum class TrackStatus { CLEAR, SAFETY_CAR, VIRTUAL_SAFETY_CAR, RED_FLAG }

/** Latest race control messages plus the track condition derived from the full message history. */
data class ParsedRaceControl(
    val messages: List<ParsedRaceControlMessage>,
    val trackStatus: TrackStatus = TrackStatus.CLEAR
)

class F1LiveRepository {

    private companion object {
        const val TAG = "F1LiveRepository"
        const val AUXILIARY_DATA_TTL_MS = 15_000L
    }

    private data class AuxiliaryData(
        val tyres: Map<String, List<ParsedStint>>,
        val catching: Map<String, Boolean>,
        val fetchedAt: Long
    )

    private val auxiliaryDataCache = ConcurrentHashMap<String, AuxiliaryData>()

    private suspend fun <T> requestResult(endpoint: String, block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Log endpoint labels and exception types only; never log URLs, payloads, or secrets.
            Log.w(TAG, "Request failed at $endpoint (${error.javaClass.simpleName})")
            Result.failure(error)
        }
    }

    suspend fun fetchStatus(baseUrl: String): Result<LiveStatusResponse> = withContext(Dispatchers.IO) {
        requestResult("status") {
            NetworkClient.createLiveService(baseUrl).getStatus()
        }
    }

    suspend fun fetchSession(baseUrl: String): Result<ParsedSession> = withContext(Dispatchers.IO) {
        requestResult("session") {
            val service = NetworkClient.createLiveService(baseUrl)
            val raw = service.getSessionRaw().string()
            var parsed = parseSessionJson(raw)
            if (parsed.sessionName.isBlank() && parsed.meetingName.isBlank()) {
                val lbRaw = requestResult("leaderboard-session-fallback") {
                    service.getLeaderboardRaw().string()
                }.getOrNull()
                if (!lbRaw.isNullOrBlank()) parsed = parseSessionJson(lbRaw)
            }
            if (parsed.totalLaps == 0) {
                val lapCountRaw = requestResult("lap-count-fallback") {
                    service.getLapCountRaw().string()
                }.getOrNull()
                if (!lapCountRaw.isNullOrBlank()) {
                    val lapJson = JSONObject(lapCountRaw)
                    val lcObj = lapJson.optJSONObject("LapCount") ?: lapJson
                    val cLap = lcObj.optInt("CurrentLap", 0)
                    val tLaps = lcObj.optInt("TotalLaps", 0)
                    if (tLaps > 0) parsed = parsed.copy(currentLap = cLap, totalLaps = tLaps)
                }
            }
            parsed
        }
    }

    suspend fun fetchWeather(baseUrl: String): Result<ParsedWeather> = withContext(Dispatchers.IO) {
        requestResult("weather") {
            val raw = NetworkClient.createLiveService(baseUrl).getWeatherRaw().string()
            parseWeather(raw)
        }
    }

    suspend fun fetchRaceControl(baseUrl: String): Result<ParsedRaceControl> = withContext(Dispatchers.IO) {
        requestResult("race-control") {
            parseRaceControl(NetworkClient.createLiveService(baseUrl).getRaceControlRaw().string())
        }
    }

    /**
     * Returns the latest [limit] race control messages, newest first, and the track status derived from
     * all of them. Accepts the backend's {"RaceControl": [...]} wrapper, the raw {"Messages": ...}
     * channel shape, or a bare array.
     */
    fun parseRaceControl(raw: String, limit: Int = 5): ParsedRaceControl {
        val all = parseAllRaceControlMessages(raw)
        // The whole history is replayed for the track status, but only the messages shown need their
        // feed timestamp converted to a local time.
        val latest = all.takeLast(limit).reversed().map { it.copy(time = formatRaceControlTime(it.time)) }
        return ParsedRaceControl(latest, deriveTrackStatus(all))
    }

    /**
     * Track condition after replaying [messagesOldestFirst]. The feed has no live track status
     * here, so it is read from the messages: a safety car, VSC or red flag stays in force until
     * a track-wide clear/green (or the VSC announces its end). Sector-scoped flags are ignored.
     */
    fun deriveTrackStatus(messagesOldestFirst: List<ParsedRaceControlMessage>): TrackStatus {
        var status = TrackStatus.CLEAR
        for (m in messagesOldestFirst) {
            val text = m.message.uppercase()
            val trackWide = m.scope.equals("Track", ignoreCase = true) || text.trim() == "TRACK CLEAR"
            status = when {
                "VIRTUAL SAFETY CAR" in text || "VSC" in text -> when {
                    "ENDING" in text -> TrackStatus.CLEAR
                    "DEPLOYED" in text -> TrackStatus.VIRTUAL_SAFETY_CAR
                    else -> status
                }
                "SAFETY CAR" in text -> when {
                    "DEPLOYED" in text -> TrackStatus.SAFETY_CAR
                    "ENDING" in text -> TrackStatus.CLEAR
                    else -> status
                }
                m.flag == "RED" && (trackWide || m.scope.isBlank()) -> TrackStatus.RED_FLAG
                trackWide && (m.flag == "CLEAR" || m.flag == "GREEN") -> TrackStatus.CLEAR
                else -> status
            }
        }
        return status
    }

    /** All messages, oldest first, with [ParsedRaceControlMessage.time] still holding the feed's UTC timestamp. */
    private fun parseAllRaceControlMessages(raw: String): List<ParsedRaceControlMessage> {
        if (raw.isBlank() || raw == "{}" || raw == "[]") return emptyList()
        return try {
            val messages = if (raw.trimStart().startsWith("[")) {
                JSONArray(raw)
            } else {
                val root = JSONObject(raw)
                optIndexedArray(root, "RaceControl") ?: optIndexedArray(root, "Messages") ?: JSONArray()
            }
            (0 until messages.length())
                .mapNotNull { messages.optJSONObject(it) }
                .mapNotNull { m ->
                    val text = m.optString("Message", "").trim()
                    if (text.isBlank()) null else ParsedRaceControlMessage(
                        time = m.optString("Utc", ""),
                        category = m.optString("Category", "").trim(),
                        flag = m.optString("Flag", "").trim().uppercase(),
                        message = text,
                        scope = m.optString("Scope", "").trim()
                    )
                }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to parse race control data (${error.javaClass.simpleName})")
            emptyList()
        }
    }

    /** Converts the feed's UTC timestamp ("2026-10-23T17:51:31", optional fraction/zone) to local "HH:mm:ss". */
    private fun formatRaceControlTime(utc: String): String {
        val millis = parseFeedUtcMillis(utc) ?: return ""
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(millis))
    }

    private val feedFractionPattern = Regex("""^\.(\d{1,3})""")

    /** Parses the feed's UTC timestamps ("2026-10-23T17:51:31", optional ".fff" and "Z") to epoch millis. */
    private fun parseFeedUtcMillis(utc: String): Long? {
        val trimmed = utc.trim()
        if (trimmed.length < 19) return null
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val seconds = parser.parse(trimmed.take(19))?.time ?: return null
            val fraction = feedFractionPattern.find(trimmed.substring(19))?.groupValues?.get(1)
            seconds + (fraction?.padEnd(3, '0')?.toLong() ?: 0L)
        } catch (error: Exception) {
            null
        }
    }

    /** Parses ExtrapolatedClock ({Utc, Remaining: "HH:MM:SS", Extrapolating}); null when absent or malformed. */
    fun parseSessionClock(clock: JSONObject?): ParsedSessionClock? {
        if (clock == null) return null
        val parts = clock.optString("Remaining", "").trim().split(":")
        if (parts.size != 3) return null
        val (h, m, s) = parts.map { it.toLongOrNull() ?: return null }
        val utcMillis = parseFeedUtcMillis(clock.optString("Utc", "")) ?: return null
        return ParsedSessionClock(
            remainingMillis = (h * 3600 + m * 60 + s) * 1000,
            utcMillis = utcMillis,
            extrapolating = clock.optBoolean("Extrapolating", false)
        )
    }

    suspend fun fetchLiveTimings(baseUrl: String): Result<List<ParsedDriverTiming>> = withContext(Dispatchers.IO) {
        requestResult("live-timings") {
            val service = NetworkClient.createLiveService(baseUrl)
            val cacheKey = NetworkClient.normalizeBaseUrl(baseUrl)
            val now = System.currentTimeMillis()
            val cachedAuxiliary = auxiliaryDataCache[cacheKey]
                ?.takeIf { now - it.fetchedAt < AUXILIARY_DATA_TTL_MS }

            coroutineScope {
                // The leaderboard and, when they are not cached, the tyre and standings feeds are
                // independent requests, so they run side by side rather than one after another.
                val leaderboardRaw = async { requestResult("leaderboard") { service.getLeaderboardRaw().string() } }
                val tyresRaw = if (cachedAuxiliary == null) {
                    async { requestResult("tyres") { service.getTyresRaw().string() } }
                } else null
                val standingsRaw = if (cachedAuxiliary == null) {
                    async { requestResult("standings") { service.getStandingsRaw().string() } }
                } else null

                val auxiliary = cachedAuxiliary ?: run {
                    val tyresResult = tyresRaw!!.await()
                    val standingsResult = standingsRaw!!.await()
                    val fetched = AuxiliaryData(
                        tyres = tyresResult.map { parseTyresJson(it) }.getOrDefault(emptyMap()),
                        catching = standingsResult.map { parseCatchingMap(it) }.getOrDefault(emptyMap()),
                        fetchedAt = now
                    )
                    // Cache only when at least one auxiliary endpoint completed successfully.
                    if (tyresResult.isSuccess || standingsResult.isSuccess) {
                        auxiliaryDataCache[cacheKey] = fetched
                    }
                    fetched
                }

                val leaderboard = leaderboardRaw.await().getOrNull()
                    ?.let { parseStandingsJson(it, auxiliary.tyres, auxiliary.catching) }
                if (!leaderboard.isNullOrEmpty()) {
                    leaderboard
                } else {
                    // Reuse the standings fetched above for the catching flags when there is a copy.
                    val standings = standingsRaw?.await()?.takeIf { it.isSuccess }
                        ?: requestResult("standings-fallback") { service.getStandingsRaw().string() }
                    parseStandingsJson(standings.getOrThrow(), auxiliary.tyres, auxiliary.catching)
                }
            }
        }
    }

    fun parseCatchingMap(raw: String): Map<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        if (raw.isBlank() || raw == "{}" || raw == "[]") return map
        try {
            val root = JSONObject(raw)
            val lines = root.optJSONObject("Lines") ?: return map
            val keys = lines.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val d = lines.optJSONObject(key) ?: continue
                val racingNumber = d.optString("RacingNumber", key).trim()
                val tla = d.optString("Tla", "").trim().uppercase()
                val intervalObj = d.optJSONObject("IntervalToPositionAhead")
                val catching = when {
                    intervalObj != null && intervalObj.has("Catching") -> intervalObj.optBoolean("Catching")
                    d.has("Catching") -> d.optBoolean("Catching")
                    else -> null
                }
                if (catching != null) {
                    if (racingNumber.isNotBlank()) map[racingNumber] = catching
                    if (tla.isNotBlank()) map[tla] = catching
                    map[key] = catching
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to parse catching data (${error.javaClass.simpleName})")
        }
        return map
    }

    fun parseSessionJson(raw: String): ParsedSession {
        if (raw.isBlank() || raw == "{}" || raw == "[]") return ParsedSession()
        return try {
            val json = JSONObject(raw)
            val sessionRoot = json.optJSONObject("Session") ?: json

            val meetingObj = sessionRoot.optJSONObject("Meeting") ?: json.optJSONObject("Meeting")
            // optString returns "" (never null) for a missing key, so each fallback is taken with ifBlank.
            val meetingName = meetingObj?.optString("Name").orEmpty()
                .ifBlank { meetingObj?.optString("OfficialName").orEmpty() }
                .ifBlank { json.optString("MeetingName", "") }

            val sessionObj = sessionRoot.optJSONObject("Session")
            val sessionName = sessionObj?.optString("Name").orEmpty()
                .ifBlank { sessionRoot.optString("Name") }
                .ifBlank { sessionRoot.optString("SessionName", json.optString("SessionName", "")) }
            val sessionType = sessionObj?.optString("Type").orEmpty()
                .ifBlank { sessionRoot.optString("Type") }
                .ifBlank { sessionRoot.optString("SessionType", json.optString("SessionType", "")) }

            val lapCountObj = json.optJSONObject("LapCount") ?: sessionRoot.optJSONObject("LapCount")
            val currentLap = lapCountObj?.optInt("CurrentLap", 0)
                ?: sessionRoot.optInt("CurrentLap", json.optInt("CurrentLap", json.optInt("Lap", 0)))
            val totalLaps = lapCountObj?.optInt("TotalLaps", 0)
                ?: sessionRoot.optInt("TotalLaps", json.optInt("TotalLaps", 0))

            val rawStatus = sessionRoot.optString("SessionStatus").ifBlank {
                sessionRoot.optString("Status", json.optString("Status", ""))
            }

            val isStarted = rawStatus.equals("Started", ignoreCase = true) ||
                    rawStatus.equals("Active", ignoreCase = true) ||
                    rawStatus.equals("Live", ignoreCase = true)

            val isFinished = rawStatus.equals("Finished", ignoreCase = true) ||
                    rawStatus.equals("Finalised", ignoreCase = true) ||
                    rawStatus.equals("Finalized", ignoreCase = true) ||
                    rawStatus.equals("Ended", ignoreCase = true)

            val displayStatus = when {
                isStarted && !isFinished -> "STARTED"
                isFinished -> "SESSION FINISHED"
                rawStatus.isNotBlank() -> rawStatus.uppercase()
                else -> "NO ACTIVE SESSION"
            }

            // A session is ONLY live if explicitly started, not finished, and has a valid session or meeting
            val isLive = isStarted && !isFinished && (sessionName.isNotBlank() || meetingName.isNotBlank() || sessionType.isNotBlank())

            ParsedSession(
                meetingName = meetingName,
                sessionName = sessionName,
                sessionType = sessionType,
                currentLap = currentLap,
                totalLaps = totalLaps,
                status = displayStatus,
                isLive = isLive,
                clock = parseSessionClock(json.optJSONObject("ExtrapolatedClock"))
            )
        } catch (error: Exception) {
            Log.w(TAG, "Unable to parse session data (${error.javaClass.simpleName})")
            ParsedSession(status = "NO ACTIVE SESSION", isLive = false)
        }
    }

    fun parseWeather(raw: String): ParsedWeather {
        if (raw.isBlank() || raw == "{}" || raw == "[]") return ParsedWeather()
        return try {
            val json = JSONObject(raw)
            val w = json.optJSONObject("Weather") ?: json
            val air = if (w.has("AirTemp") && w.optString("AirTemp").isNotBlank()) "${w.optString("AirTemp")} °C" else "--"
            val track = if (w.has("TrackTemp") && w.optString("TrackTemp").isNotBlank()) "${w.optString("TrackTemp")} °C" else "--"
            val hum = if (w.has("Humidity") && w.optString("Humidity").isNotBlank()) "${w.optString("Humidity")} %" else "--"
            val press = if (w.has("Pressure") && w.optString("Pressure").isNotBlank()) "${w.optString("Pressure")} hPa" else "--"
            val wind = if (w.has("WindSpeed") && w.optString("WindSpeed").isNotBlank()) "${w.optString("WindSpeed")} m/s" else "--"
            val dir = if (w.has("WindDirection") && w.optString("WindDirection").isNotBlank()) "${w.optString("WindDirection")}°" else "--"
            val rainVal = w.optString("Rainfall", "0").toDoubleOrNull() ?: 0.0
            ParsedWeather(
                airTemp = air,
                trackTemp = track,
                humidity = hum,
                pressure = press,
                windSpeed = wind,
                windDirection = dir,
                isRaining = rainVal > 0.0
            )
        } catch (error: Exception) {
            Log.w(TAG, "Unable to parse weather data (${error.javaClass.simpleName})")
            ParsedWeather()
        }
    }

    fun parseStintsArray(stintsArray: JSONArray): List<ParsedStint> {
        val stintsList = mutableListOf<ParsedStint>()
        for (i in 0 until stintsArray.length()) {
            val sObj = stintsArray.optJSONObject(i) ?: continue
            val compound = sObj.optString("Compound", "").trim().uppercase().ifBlank { "UNKNOWN" }
            val totalLaps = sObj.optInt("TotalLaps", sObj.optString("TotalLaps", "0").toIntOrNull() ?: 0)
            val newRaw = sObj.opt("New")
            val isNew = when (newRaw) {
                is Boolean -> newRaw
                is String -> newRaw.equals("true", ignoreCase = true)
                is Number -> newRaw.toInt() != 0
                else -> false
            }
            stintsList.add(
                ParsedStint(
                    stintNumber = i + 1,
                    compound = compound,
                    totalLaps = totalLaps,
                    isNew = isNew
                )
            )
        }
        return stintsList
    }

    fun parseTyresJson(raw: String): Map<String, List<ParsedStint>> {
        val result = mutableMapOf<String, List<ParsedStint>>()
        if (raw.isBlank() || raw == "{}" || raw == "[]") return result
        try {
            if (raw.trimStart().startsWith("{")) {
                val root = JSONObject(raw)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val driverKey = keys.next()
                    val driverVal = root.opt(driverKey)
                    val stintsArray = when (driverVal) {
                        is JSONObject -> optIndexedArray(driverVal, "Stints")
                        is JSONArray -> driverVal
                        else -> null
                    }
                    if (stintsArray != null) {
                        val parsed = parseStintsArray(stintsArray)
                        if (parsed.isNotEmpty()) {
                            result[driverKey] = parsed
                        }
                    }
                }
            } else if (raw.trimStart().startsWith("[")) {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val driverNum = item.optString("RacingNumber", item.optString("DriverNumber", ""))
                    val stintsArray = optIndexedArray(item, "Stints")
                    if (stintsArray != null) {
                        val parsed = parseStintsArray(stintsArray)
                        if (driverNum.isNotBlank() && parsed.isNotEmpty()) {
                            result[driverNum] = parsed
                        }
                    }
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to parse tyre stint data (${error.javaClass.simpleName})")
        }
        return result
    }

    private val lapTimePattern = Regex("""^(?:(\d+):)?(?:(\d+):)?(\d+(?:\.\d+)?)$""")

    /**
     * Parses "ss.mmm", "m:ss.mmm" or "h:mm:ss.mmm". Anything else (placeholders such as "--" or
     * "NO TIME", "NaN", exponent notation) is rejected rather than guessed at.
     */
    fun parseLapTimeToSeconds(timeStr: String): Double? {
        val match = lapTimePattern.matchEntire(timeStr.trim()) ?: return null
        val (first, second, last) = match.destructured
        val seconds = last.toDoubleOrNull() ?: return null
        return when {
            first.isNotEmpty() && second.isNotEmpty() ->
                first.toDouble() * 3600.0 + second.toDouble() * 60.0 + seconds
            first.isNotEmpty() -> first.toDouble() * 60.0 + seconds
            else -> seconds
        }
    }

    private fun sectorValue(sectors: JSONArray?, index: Int): String =
        sectors?.optJSONObject(index)?.optString("Value", "")?.trim().orEmpty().ifBlank { "--" }

    fun parseStandingsJson(
        raw: String,
        tyresMap: Map<String, List<ParsedStint>> = emptyMap(),
        standingsCatchingMap: Map<String, Boolean> = emptyMap()
    ): List<ParsedDriverTiming> {
        val list = mutableListOf<ParsedDriverTiming>()
        if (raw.isBlank() || raw == "{}" || raw == "[]") return list

        try {
            val root = if (raw.trimStart().startsWith("[")) {
                val arr = JSONArray(raw)
                val obj = JSONObject()
                obj.put("list", arr)
                obj
            } else {
                JSONObject(raw)
            }

            val candidateDrivers = mutableListOf<JSONObject>()

            if (root.has("list")) {
                val arr = root.optJSONArray("list")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { candidateDrivers.add(it) }
                    }
                }
            } else if (root.has("Leaderboard") && root.has("Lines")) {
                val leaderboard = root.optJSONArray("Leaderboard")
                val lines = root.optJSONObject("Lines")
                if (leaderboard != null && lines != null && leaderboard.length() > 0) {
                    for (i in 0 until leaderboard.length()) {
                        val num = leaderboard.optString(i)
                        lines.optJSONObject(num)?.let { candidateDrivers.add(it) }
                    }
                }
            } else if (root.has("Lines")) {
                val lines = root.optJSONObject("Lines")
                if (lines != null) {
                    val keys = lines.keys()
                    while (keys.hasNext()) {
                        lines.optJSONObject(keys.next())?.let { candidateDrivers.add(it) }
                    }
                }
            } else if (root.has("Drivers") || root.has("Standings")) {
                val arr = root.optJSONArray("Drivers") ?: root.optJSONArray("Standings")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { candidateDrivers.add(it) }
                    }
                }
            }

            for (d in candidateDrivers) {
                val racingNumber = d.optString("RacingNumber", d.optString("DriverNumber", "")).trim()
                val broadcastName = d.optString("BroadcastName", "").trim()
                val tla = d.optString("Tla", d.optString("Code", "")).trim().uppercase().ifBlank {
                    broadcastName.take(3).uppercase()
                }
                val first = d.optString("FirstName", "").trim()
                val last = d.optString("LastName", "").trim()
                val fullName = d.optString("FullName", "").trim().ifBlank {
                    if (first.isNotBlank() || last.isNotBlank()) "$first $last".trim()
                    else broadcastName
                }
                val teamName = d.optString("TeamName", "").trim()
                val teamColour = d.optString("TeamColour", "").replace("#", "").trim()
                if (teamColour.isNotBlank()) {
                    if (teamName.isNotBlank()) rocks.urzahil.f1livetiming.ui.theme.TeamColors.registerHexColor(teamName, teamColour)
                    if (racingNumber.isNotBlank()) rocks.urzahil.f1livetiming.ui.theme.TeamColors.registerHexColor(racingNumber, teamColour)
                    if (tla.isNotBlank()) rocks.urzahil.f1livetiming.ui.theme.TeamColors.registerHexColor(tla, teamColour)
                }

                // STRICT: If driver has no identifiable name, code, or team, it is empty placeholder: DO NOT ADD
                if (fullName.isBlank() && tla.isBlank() && broadcastName.isBlank() && teamName.isBlank()) {
                    continue
                }

                val pos = d.optString("Position", "").trim()
                if (racingNumber.isBlank() && pos.isBlank()) {
                    continue
                }

                val bestLap = extractValue(d, "BestLapTime").trim().ifBlank { "--" }

                // Races report GapToLeader/IntervalToPositionAhead; practice reports TimeDiffToFastest/
                // TimeDiffToPositionAhead instead, and qualifying keeps them per part (Q1-Q3) in Stats.
                // The Stats fallback walks back to an earlier part when the current one is empty, so it is
                // only used once the driver has a lap time in this part; otherwise Q1's gap would linger
                // at the start of Q2/Q3.
                val hasLap = bestLap != "--"
                val gapRaw = extractValue(d, "GapToLeader").trim()
                    .ifBlank { extractValue(d, "TimeDiffToFastest").trim() }
                    .ifBlank { if (hasLap) latestQualifyingStat(d, "TimeDiffToFastest") else "" }
                val gapToLeader = if (gapRaw.isNotBlank()) gapRaw else if (pos == "1") "LEADER" else "--"

                val intervalRaw = extractValue(d, "IntervalToPositionAhead").trim()
                    .ifBlank { extractValue(d, "TimeDiffToPositionAhead").trim() }
                    .ifBlank { if (hasLap) latestQualifyingStat(d, "TimeDiffToPositionAhead", "TimeDifftoPositionAhead") else "" }
                val intervalAhead = if (intervalRaw.isNotBlank()) intervalRaw else if (pos == "1") "-" else "--"

                val lastLap = extractValue(d, "LastLapTime").trim().ifBlank { "--" }

                val sectorsArray = optIndexedArray(d, "Sectors")
                val s1 = sectorValue(sectorsArray, 0)
                val s2 = sectorValue(sectorsArray, 1)
                val s3 = sectorValue(sectorsArray, 2)

                val inPit = d.optBoolean("InPit", false)
                val isRetired = d.optBoolean("Retired", false)
                val isStopped = d.optBoolean("Stopped", false)
                val pits = d.optInt("NumberOfPitStops", d.optInt("PitStops", 0))
                val totalLaps = d.optInt("NumberOfLaps", d.optInt("Laps", 0))

                // Extract Catching flag from IntervalToPositionAhead or standingsCatchingMap
                val intervalObj = d.optJSONObject("IntervalToPositionAhead")
                val catchingFromD = when {
                    intervalObj != null && intervalObj.has("Catching") -> intervalObj.optBoolean("Catching")
                    d.has("Catching") -> d.optBoolean("Catching")
                    else -> null
                }
                val rawCatching = catchingFromD
                    ?: standingsCatchingMap[racingNumber]
                    ?: standingsCatchingMap[tla]

                // For leader (P1), there is no car ahead. Retired, stopped or in-pit also don't have an active gap chase.
                val finalCatching = if (pos == "1" || gapToLeader == "LEADER" || isRetired || isStopped || inPit) {
                    null
                } else {
                    rawCatching
                }

                // Extract Stints directly from the driver object (from /api/leaderboard), or fallback to tyresMap
                val driverStintsArray = optIndexedArray(d, "Stints")
                val stints = if (driverStintsArray != null && driverStintsArray.length() > 0) {
                    parseStintsArray(driverStintsArray)
                } else {
                    tyresMap[racingNumber] ?: tyresMap[tla] ?: emptyList()
                }

                val currentStint = stints.lastOrNull()
                val compound = if (currentStint != null && currentStint.compound != "UNKNOWN" && currentStint.compound.isNotBlank()) {
                    currentStint.compound
                } else {
                    d.optString("Compound", "--").uppercase().ifBlank { "--" }
                }
                val compoundAge = currentStint?.totalLaps ?: d.optInt("TyreAge", 0)
                val isNew = currentStint?.isNew ?: false

                list.add(
                    ParsedDriverTiming(
                        position = if (pos.isNotBlank()) pos else "${list.size + 1}",
                        racingNumber = racingNumber,
                        driverCode = tla.ifBlank { "DRV" },
                        fullName = fullName.ifBlank { "Driver $racingNumber" },
                        teamName = teamName,
                        teamColorHex = teamColour,
                        gapToLeader = gapToLeader,
                        intervalToAhead = intervalAhead,
                        bestLapTime = bestLap,
                        lastLapTime = lastLap,
                        sector1 = s1,
                        sector2 = s2,
                        sector3 = s3,
                        inPit = inPit,
                        pitStops = pits,
                        totalLaps = totalLaps,
                        isRetired = isRetired,
                        isStopped = isStopped,
                        currentCompound = compound,
                        currentCompoundAge = compoundAge,
                        isCurrentCompoundNew = isNew,
                        stintsHistory = stints,
                        isOverallBestLap = false,
                        isCatchingAhead = finalCatching,
                        isKnockedOut = d.optBoolean("KnockedOut", false)
                    )
                )
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to parse standings data (${error.javaClass.simpleName})")
        }

        // Unpositioned entries go last; among equals, running cars precede retired/stopped ones.
        // sortedWith is stable, so feed order is kept for any remaining ties.
        val sortedList = list.sortedWith(
            compareBy<ParsedDriverTiming> { it.position.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.isRetired || it.isStopped }
        )

        // Dynamically compute the overall fastest best lap of the session without hardcoding
        val overallFastestDriverNumber = sortedList
            .mapNotNull { driver ->
                val sec = parseLapTimeToSeconds(driver.bestLapTime)
                if (sec != null && sec > 0.0) Pair(driver.racingNumber, sec) else null
            }
            .minByOrNull { it.second }
            ?.first

        return if (overallFastestDriverNumber != null) {
            sortedList.map { driver ->
                if (driver.racingNumber == overallFastestDriverNumber) {
                    driver.copy(isOverallBestLap = true)
                } else {
                    driver
                }
            }
        } else {
            sortedList
        }
    }

    /**
     * Reads a list field that SignalR may deliver either as an array or as an index-keyed object
     * ({"0": {...}, "1": {...}}), which is how backends that merge raw deltas expose Stints/Sectors.
     * Object entries are placed at their index so positional fields (sector 1-3) stay aligned.
     */
    private fun optIndexedArray(obj: JSONObject, key: String): JSONArray? {
        obj.optJSONArray(key)?.let { return it }
        val indexed = obj.optJSONObject(key) ?: return null
        val entries = indexed.keys().asSequence()
            .mapNotNull { k -> k.toIntOrNull()?.takeIf { it >= 0 }?.let { it to indexed.opt(k) } }
            .toList()
        if (entries.isEmpty()) return null
        val array = JSONArray()
        for ((index, value) in entries) array.put(index, value)
        return array
    }

    /** Value of [key] from the most recent qualifying part (last Stats entry) that has one. */
    private fun latestQualifyingStat(obj: JSONObject, vararg keys: String): String {
        val stats = optIndexedArray(obj, "Stats") ?: return ""
        // Only the current part's entry counts: the leader has no gap in Q2/Q3, and walking back to
        // an earlier part would show the gap he had in Q1 instead of LEADER.
        val times = optIndexedArray(obj, "BestLapTimes")
        val parts = if (times != null) {
            val part = (times.length() - 1 downTo 0).firstOrNull {
                times.optJSONObject(it)?.let { t -> extractValue(t, "Value").isNotBlank() } == true
            } ?: return ""
            listOf(part)
        } else {
            stats.length() - 1 downTo 0
        }
        for (i in parts) {
            val entry = stats.optJSONObject(i) ?: continue
            keys.map { extractValue(entry, it).trim() }.firstOrNull { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    private fun extractValue(obj: JSONObject, key: String): String {
        val item = obj.opt(key) ?: return ""
        return when (item) {
            is JSONObject -> item.optString("Value", "")
            is String -> item
            is Number -> item.toString()
            else -> ""
        }
    }
}
