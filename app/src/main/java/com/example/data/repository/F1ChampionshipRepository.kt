package com.example.data.repository

import com.example.data.api.NetworkClient
import com.example.data.model.ConstructorStandingItem
import com.example.data.model.DriverStandingItem
import com.example.data.model.F1Constructor
import com.example.data.model.F1Driver
import com.example.data.model.PracticeTimingItem
import com.example.data.model.QualifyingResultItem
import com.example.data.model.RaceResultItem
import com.example.data.model.SeasonRaceItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class F1ChampionshipRepository {

    /** Await OkHttp without blocking an IO thread and cancel the call with its coroutine. */
    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: java.io.IOException) {
                continuation.resumeWith(Result.failure(error))
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(response))
                } else {
                    response.close()
                }
            }
        })
    }

    /** Preserve structured cancellation instead of converting it into a normal failed Result. */
    private suspend fun <T> cancellableResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private val service = NetworkClient.jolpicaService
    private val httpClient = NetworkClient.okHttpClient

    data class DriverMetadata(
        val headshots: Map<String, String>,
        val driverColors: Map<String, androidx.compose.ui.graphics.Color>
    )

    @Volatile
    private var cachedDriverMetadata: DriverMetadata? = null

    @Volatile
    private var lastMetadataFailureAt = 0L

    private val driverMetadataMutex = Mutex()

    private suspend fun getDriverMetadata(): DriverMetadata = driverMetadataMutex.withLock {
        val existing = cachedDriverMetadata
        if (existing != null && (existing.headshots.isNotEmpty() || existing.driverColors.isNotEmpty())) {
            return@withLock existing
        }
        // After a failed or empty fetch, back off so an OpenF1 outage is not retried (serially,
        // under the mutex) by every standings and results load.
        val now = System.currentTimeMillis()
        if (now - lastMetadataFailureAt < METADATA_RETRY_COOLDOWN_MS) {
            return@withLock DriverMetadata(emptyMap(), emptyMap())
        }
        val fetched = fetchDriverMetadataFromApi()
        if (fetched.headshots.isNotEmpty() || fetched.driverColors.isNotEmpty()) {
            cachedDriverMetadata = fetched
            lastMetadataFailureAt = 0L
        } else {
            lastMetadataFailureAt = now
        }
        fetched
    }

    private fun enrichDriver(
        driver: F1Driver?,
        constructor: F1Constructor?,
        metadata: DriverMetadata
    ): F1Driver? {
        if (driver == null) return null
        val code = driver.code?.uppercase().orEmpty()
        val number = driver.permanentNumber.orEmpty()
        val familyName = driver.familyName?.lowercase().orEmpty()
        val headshot = driver.headshotUrl
            ?: metadata.headshots[code]
            ?: metadata.headshots[number]
            ?: metadata.headshots[familyName]
        val driverColor = metadata.driverColors[code]
            ?: metadata.driverColors[number]
            ?: metadata.driverColors[familyName]

        if (driverColor != null) {
            constructor?.constructorId?.takeIf { it.isNotBlank() }?.let {
                com.example.ui.theme.TeamColors.registerColor(it, driverColor)
            }
            constructor?.name?.takeIf { it.isNotBlank() }?.let {
                com.example.ui.theme.TeamColors.registerColor(it, driverColor)
            }
        }
        return driver.copy(headshotUrl = headshot)
    }

    suspend fun fetchDriverStandings(): Result<Pair<String, List<DriverStandingItem>>> =
        withContext(Dispatchers.IO) {
            cancellableResult {
                val root = service.getCurrentDriverStandings()
                val season = root.mrData?.standingsTable?.season.orEmpty()
                val rawList = root.mrData?.standingsTable?.standingsLists?.firstOrNull()?.driverStandings.orEmpty()

                // Fetch real official driver photos and colors extracted from OpenF1 feed at runtime
                val metadata = getDriverMetadata()

                val enrichedList = rawList.map { item ->
                    val enrichedDriver = enrichDriver(item.driver, item.constructors?.firstOrNull(), metadata)
                    item.copy(driver = enrichedDriver, headshotUrl = enrichedDriver?.headshotUrl)
                }

                Pair(season, enrichedList)
            }
        }

    suspend fun fetchConstructorStandings(): Result<Pair<String, List<ConstructorStandingItem>>> =
        withContext(Dispatchers.IO) {
            cancellableResult {
                // Ensure driver metadata is fetched first so constructor colors can be inferred from drivers
                getDriverMetadata()

                val root = service.getCurrentConstructorStandings()
                val season = root.mrData?.standingsTable?.season.orEmpty()
                val list = root.mrData?.standingsTable?.standingsLists?.firstOrNull()?.constructorStandings.orEmpty()
                Pair(season, list)
            }
        }

    suspend fun fetchCurrentCalendar(): Result<Pair<String, List<SeasonRaceItem>>> =
        withContext(Dispatchers.IO) {
            cancellableResult {
                val root = service.getCurrentCalendar()
                val season = root.mrData?.raceTable?.season.orEmpty()
                val list = root.mrData?.raceTable?.races.orEmpty()
                Pair(season, list)
            }
        }

    suspend fun fetchRaceResults(round: String): Result<List<RaceResultItem>> =
        withContext(Dispatchers.IO) {
            cancellableResult {
                val root = service.getRaceResults(round)
                val raw = root.mrData?.raceTable?.races?.firstOrNull()?.results.orEmpty()
                val metadata = getDriverMetadata()
                raw.map { item ->
                    item.copy(driver = enrichDriver(item.driver, item.constructor, metadata))
                }
            }
        }

    suspend fun fetchQualifyingResults(round: String): Result<List<QualifyingResultItem>> =
        withContext(Dispatchers.IO) {
            cancellableResult {
                val root = service.getQualifyingResults(round)
                val raw = root.mrData?.raceTable?.races?.firstOrNull()?.qualifyingResults.orEmpty()
                val metadata = getDriverMetadata()
                raw.map { item ->
                    item.copy(driver = enrichDriver(item.driver, item.constructor, metadata))
                }
            }
        }

    suspend fun fetchSprintResults(round: String): Result<List<RaceResultItem>> =
        withContext(Dispatchers.IO) {
            cancellableResult {
                val root = service.getSprintResults(round)
                val raw = root.mrData?.raceTable?.races?.firstOrNull()?.sprintResults.orEmpty()
                val metadata = getDriverMetadata()
                raw.map { item ->
                    item.copy(driver = enrichDriver(item.driver, item.constructor, metadata))
                }
            }
        }


    private companion object {
        const val OPENF1_BASE = "https://api.openf1.org/v1"
        const val METADATA_RETRY_COOLDOWN_MS = 60_000L
        const val SESSION_DATE_TOLERANCE_MS = 2L * 24 * 60 * 60 * 1000
        const val SEGMENT_BREAK_MS = 180_000L
    }

    /** GET an OpenF1 endpoint that returns a JSON array; null for HTTP errors or other shapes. */
    private suspend fun fetchJsonArray(url: String): JSONArray? =
        httpClient.newCall(Request.Builder().url(url).build()).awaitResponse().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string().orEmpty().trimStart()
            if (body.startsWith("[")) JSONArray(body) else null
        }

    private suspend fun fetchDriverMetadataFromApi(): DriverMetadata {
        val headshotsMap = mutableMapOf<String, String>()
        val driverColorsMap = mutableMapOf<String, androidx.compose.ui.graphics.Color>()
        try {
            val arr = fetchJsonArray("$OPENF1_BASE/drivers?session_key=latest")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val d = arr.optJSONObject(i) ?: continue
                    val headshot = d.optString("headshot_url", "").trim()
                    val teamColour = d.optString("team_colour", "").trim()
                    val teamName = d.optString("team_name", "").trim()
                    val acronym = d.optString("name_acronym", "").trim().uppercase()
                    val driverNum = d.optString("driver_number", "").trim()
                    val lastName = d.optString("last_name", "").trim().lowercase()

                    // Extract and register team & driver colors at runtime from API
                    val color = com.example.ui.theme.TeamColors.parseHex(teamColour)
                    if (color != null) {
                        if (teamName.isNotBlank()) {
                            com.example.ui.theme.TeamColors.registerColor(teamName, color)
                        }
                        if (acronym.isNotBlank()) {
                            driverColorsMap[acronym] = color
                            com.example.ui.theme.TeamColors.registerColor(acronym, color)
                        }
                        if (driverNum.isNotBlank()) {
                            driverColorsMap[driverNum] = color
                            com.example.ui.theme.TeamColors.registerColor(driverNum, color)
                        }
                        if (lastName.isNotBlank()) {
                            driverColorsMap[lastName] = color
                            com.example.ui.theme.TeamColors.registerColor(lastName, color)
                        }
                    }

                    if (headshot.isNotBlank()) {
                        if (acronym.isNotBlank()) headshotsMap[acronym] = headshot
                        if (driverNum.isNotBlank()) headshotsMap[driverNum] = headshot
                        if (lastName.isNotBlank()) headshotsMap[lastName] = headshot
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("F1ChampionshipRepository", "Driver metadata request failed (${error.javaClass.simpleName})")
        }
        return DriverMetadata(headshotsMap, driverColorsMap)
    }

    private fun mapCountryForOpenF1(country: String): String {
        return when (country.trim().lowercase(Locale.US)) {
            "usa", "united states" -> "United States"
            "uk", "great britain", "united kingdom" -> "United Kingdom"
            "uae", "united arab emirates" -> "United Arab Emirates"
            else -> country.trim()
        }
    }

    private fun alternateSessionName(sessionName: String): String? = when (sessionName) {
        "Practice 1" -> "FP1"
        "Practice 2" -> "FP2"
        "Practice 3" -> "FP3"
        "Sprint Qualifying" -> "Sprint Shootout"
        "Sprint Shootout" -> "Sprint Qualifying"
        else -> null
    }

    /** Parses the date part of an ISO timestamp as midnight UTC. A fresh formatter per call: SimpleDateFormat is not thread-safe. */
    private fun parseUtcDateMillis(value: String): Long? {
        if (value.length < 10) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(value.take(10))?.time
        } catch (_: ParseException) {
            null
        }
    }

    /** Parses an OpenF1 timestamp such as "2025-03-15T05:03:21.123000+00:00" (always UTC) to epoch millis. */
    private fun parseUtcDateTimeMillis(value: String): Long? {
        if (value.length < 19) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(value.take(19))?.time
        } catch (_: ParseException) {
            null
        }
    }

    /**
     * Finds the OpenF1 session for a calendar event. Lookups are always bound to the season year
     * and matched on the scheduled date (a Grand Prix can be branded for one country but held in
     * another, so country is only a secondary filter). A candidate is accepted only when its date
     * is within a couple of days of the scheduled one, so a wrong season or weekend is never
     * returned.
     */
    private suspend fun findOpenF1SessionKey(year: String, country: String, raceDate: String, sessionName: String): Int? {
        val y = if (year.isBlank() || year.equals("current", ignoreCase = true)) {
            Calendar.getInstance().get(Calendar.YEAR).toString()
        } else year
        val target = parseUtcDateMillis(raceDate)
        val encCountry = URLEncoder.encode(mapCountryForOpenF1(country), "UTF-8")

        for (name in listOfNotNull(sessionName, alternateSessionName(sessionName))) {
            val encName = URLEncoder.encode(name, "UTF-8")
            if (target != null) {
                querySessionKey("$OPENF1_BASE/sessions?year=$y&session_name=$encName", target)
                    ?.let { return it }
            }
            if (country.isNotBlank()) {
                querySessionKey("$OPENF1_BASE/sessions?year=$y&country_name=$encCountry&session_name=$encName", target)
                    ?.let { return it }
            }
        }
        return null
    }

    /** Picks the session closest to [target]; without a target only an unambiguous single match is accepted. */
    private suspend fun querySessionKey(url: String, target: Long?): Int? {
        return try {
            val arr = fetchJsonArray(url) ?: return null
            var bestKey: Int? = null
            var bestDiff = Long.MAX_VALUE
            var candidates = 0
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val key = s.optInt("session_key", -1)
                if (key <= 0) continue
                candidates++
                if (target == null) {
                    bestKey = key
                    continue
                }
                val start = parseUtcDateMillis(s.optString("date_start", "")) ?: continue
                val diff = kotlin.math.abs(start - target)
                if (diff <= SESSION_DATE_TOLERANCE_MS && diff < bestDiff) {
                    bestDiff = diff
                    bestKey = key
                }
            }
            if (target == null && candidates != 1) null else bestKey
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchSessionDrivers(sessionKey: Int): Map<Int, JSONObject> {
        val drivers = mutableMapOf<Int, JSONObject>()
        val arr = fetchJsonArray("$OPENF1_BASE/drivers?session_key=$sessionKey") ?: return drivers
        for (i in 0 until arr.length()) {
            val d = arr.optJSONObject(i) ?: continue
            val num = d.optInt("driver_number", -1)
            if (num > 0) drivers[num] = d
        }
        return drivers
    }

    private suspend fun fetchSessionLaps(sessionKey: Int): JSONArray =
        fetchJsonArray("$OPENF1_BASE/laps?session_key=$sessionKey") ?: JSONArray()

    /** Builds the shared driver/constructor models from an OpenF1 driver entry and registers its team colour. */
    private fun openF1Entry(num: Int, d: JSONObject?): Pair<F1Driver, F1Constructor> {
        val fullName = d?.optString("full_name", "").orEmpty()
        val lastName = d?.optString("last_name", "").orEmpty()
        val acronym = d?.optString("name_acronym", "").orEmpty()
        val teamName = d?.optString("team_name", "").orEmpty()
        val headshotUrl = d?.optString("headshot_url", "").orEmpty()
        val teamColour = d?.optString("team_colour", "").orEmpty()

        val color = com.example.ui.theme.TeamColors.parseHex(teamColour)
        if (color != null) {
            if (teamName.isNotBlank()) com.example.ui.theme.TeamColors.registerColor(teamName, color)
            if (acronym.isNotBlank()) com.example.ui.theme.TeamColors.registerColor(acronym, color)
        }

        val driver = F1Driver(
            driverId = acronym.lowercase(Locale.US),
            permanentNumber = num.toString(),
            code = acronym,
            givenName = fullName.substringBefore(" "),
            familyName = lastName.ifBlank { fullName },
            dateOfBirth = null,
            nationality = null,
            headshotUrl = headshotUrl
        )
        val constructor = F1Constructor(
            constructorId = teamName.lowercase(Locale.US).replace(" ", "_"),
            name = teamName,
            nationality = null
        )
        return driver to constructor
    }

    suspend fun fetchPracticeTimings(
        year: String,
        country: String,
        raceDate: String,
        sessionName: String
    ): Result<List<PracticeTimingItem>> = withContext(Dispatchers.IO) {
        cancellableResult {
            val sessionKey = findOpenF1SessionKey(year, country, raceDate, sessionName)
                ?: return@cancellableResult emptyList()

            val driversMap = fetchSessionDrivers(sessionKey)
            val lapsArray = fetchSessionLaps(sessionKey)

            val bestLapMap = mutableMapOf<Int, JSONObject>()
            val lapCountMap = mutableMapOf<Int, Int>()
            for (i in 0 until lapsArray.length()) {
                val lap = lapsArray.optJSONObject(i) ?: continue
                val num = lap.optInt("driver_number", -1)
                if (num <= 0) continue
                lapCountMap[num] = (lapCountMap[num] ?: 0) + 1
                val dur = lap.optDouble("lap_duration", 0.0)
                if (dur > 0.0) {
                    val cur = bestLapMap[num]?.optDouble("lap_duration", 9999.0) ?: 9999.0
                    if (dur < cur) bestLapMap[num] = lap
                }
            }

            if (bestLapMap.isEmpty()) return@cancellableResult emptyList()

            fun bestDuration(num: Int): Double = bestLapMap[num]?.optDouble("lap_duration", 0.0) ?: 0.0

            // Drivers with a timed lap first (fastest first), then those without.
            val ranked = driversMap.keys.sortedWith(
                compareBy<Int> { bestDuration(it) <= 0.0 }.thenBy { bestDuration(it) }
            )
            val leaderDur = ranked.firstOrNull()?.let { bestDuration(it) } ?: 0.0
            if (leaderDur <= 0.0) return@cancellableResult emptyList()

            ranked.mapIndexed { index, num ->
                val (driver, constructor) = openF1Entry(num, driversMap[num])
                val bestLap = bestLapMap[num]
                val bestDur = bestDuration(num)
                val gapStr = when {
                    bestDur <= 0.0 -> "--"
                    index == 0 -> "Leader"
                    else -> String.format(Locale.US, "+%.3f", bestDur - leaderDur)
                }
                val s1 = bestLap?.optDouble("duration_sector_1", 0.0) ?: 0.0
                val s2 = bestLap?.optDouble("duration_sector_2", 0.0) ?: 0.0
                val s3 = bestLap?.optDouble("duration_sector_3", 0.0) ?: 0.0

                PracticeTimingItem(
                    position = (index + 1).toString(),
                    driverNumber = num.toString(),
                    driver = driver,
                    constructor = constructor,
                    bestLapTime = if (bestDur > 0.0) formatLapTime(bestDur) else "--",
                    gapToLeader = gapStr,
                    lapsCompleted = lapCountMap[num] ?: 0,
                    sector1 = if (s1 > 0.0) String.format(Locale.US, "%.3f", s1) else null,
                    sector2 = if (s2 > 0.0) String.format(Locale.US, "%.3f", s2) else null,
                    sector3 = if (s3 > 0.0) String.format(Locale.US, "%.3f", s3) else null
                )
            }
        }
    }

    suspend fun fetchSprintQualifyingResults(
        year: String,
        country: String,
        raceDate: String
    ): Result<List<QualifyingResultItem>> = withContext(Dispatchers.IO) {
        cancellableResult {
            val sessionKey = findOpenF1SessionKey(year, country, raceDate, "Sprint Qualifying")
                ?: findOpenF1SessionKey(year, country, raceDate, "Sprint Shootout")
                ?: return@cancellableResult emptyList()

            val driversMap = fetchSessionDrivers(sessionKey)
            val lapsArray = fetchSessionLaps(sessionKey)

            data class LapEntry(val driverNumber: Int, val lapDuration: Double, val startMillis: Long)
            val validLaps = mutableListOf<LapEntry>()
            for (i in 0 until lapsArray.length()) {
                val lap = lapsArray.optJSONObject(i) ?: continue
                val num = lap.optInt("driver_number", -1)
                val dur = lap.optDouble("lap_duration", 0.0)
                val start = parseUtcDateTimeMillis(lap.optString("date_start", ""))
                if (num > 0 && dur > 0.0 && start != null) {
                    validLaps.add(LapEntry(num, dur, start))
                }
            }
            if (validLaps.isEmpty()) return@cancellableResult emptyList()

            validLaps.sortBy { it.startMillis }

            // SQ1/SQ2/SQ3 are separated by the longest pauses in lap starts. Take the two largest
            // gaps above the threshold (rather than the first two) so a red-flag stoppage inside a
            // segment is less likely to be mistaken for a segment break.
            val breaks = (1 until validLaps.size)
                .map { validLaps[it - 1].startMillis to validLaps[it].startMillis }
                .filter { (prev, cur) -> cur - prev > SEGMENT_BREAK_MS }
                .sortedByDescending { (prev, cur) -> cur - prev }
                .take(2)
                .map { (_, cur) -> cur }
                .sorted()

            val break1 = breaks.getOrNull(0)
            val break2 = breaks.getOrNull(1)

            fun getStage(startMillis: Long): Int = when {
                break1 == null || startMillis < break1 -> 1
                break2 == null || startMillis < break2 -> 2
                else -> 3
            }

            val driverSq = mutableMapOf<Int, Array<Double?>>()
            driversMap.keys.forEach { driverSq[it] = arrayOfNulls<Double>(3) }

            validLaps.forEach { lap ->
                val times = driverSq[lap.driverNumber] ?: return@forEach
                val stageIdx = getStage(lap.startMillis) - 1
                val cur = times[stageIdx]
                if (cur == null || lap.lapDuration < cur) times[stageIdx] = lap.lapDuration
            }

            val sortedDriverNums = driversMap.keys.sortedWith(
                compareBy<Int> { num ->
                    val t = driverSq[num]
                    if (t?.get(2) != null) 0 else if (t?.get(1) != null) 1 else if (t?.get(0) != null) 2 else 3
                }.thenBy { num ->
                    val t = driverSq[num]
                    t?.get(2) ?: t?.get(1) ?: t?.get(0) ?: 9999.0
                }
            )

            sortedDriverNums.mapIndexed { index, num ->
                val (driver, constructor) = openF1Entry(num, driversMap[num])
                val times = driverSq[num]
                QualifyingResultItem(
                    number = num.toString(),
                    position = (index + 1).toString(),
                    driver = driver,
                    constructor = constructor,
                    q1 = times?.get(0)?.let { formatLapTime(it) },
                    q2 = times?.get(1)?.let { formatLapTime(it) },
                    q3 = times?.get(2)?.let { formatLapTime(it) }
                )
            }
        }
    }

    private fun formatLapTime(seconds: Double): String {
        val m = (seconds / 60.0).toInt()
        val s = seconds % 60.0
        return String.format(Locale.US, "%d:%06.3f", m, s)
    }
}
