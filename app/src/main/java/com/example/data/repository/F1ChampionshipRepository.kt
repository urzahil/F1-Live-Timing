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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

    private val driverMetadataMutex = Mutex()

    private suspend fun getDriverMetadata(): DriverMetadata = driverMetadataMutex.withLock {
        val existing = cachedDriverMetadata
        if (existing != null && (existing.headshots.isNotEmpty() || existing.driverColors.isNotEmpty())) {
            return@withLock existing
        }
        val fetched = fetchDriverMetadataFromApi()
        if (fetched.headshots.isNotEmpty() || fetched.driverColors.isNotEmpty()) {
            cachedDriverMetadata = fetched
        }
        fetched
    }

    suspend fun fetchDriverStandings(): Result<Pair<String, List<DriverStandingItem>>> =
        withContext(Dispatchers.IO) {
            cancellableResult {
                val root = service.getCurrentDriverStandings()
                val season = root.mrData?.standingsTable?.season.orEmpty()
                val rawList = root.mrData?.standingsTable?.standingsLists?.firstOrNull()?.driverStandings.orEmpty()

                // Fetch real official driver photos and colors extracted from OpenF1 feed at runtime
                val metadata = getDriverMetadata()
                val headshotsMap = metadata.headshots
                val driverColorsMap = metadata.driverColors

                val enrichedList = rawList.map { item ->
                    val code = item.driver?.code?.uppercase().orEmpty()
                    val number = item.driver?.permanentNumber.orEmpty()
                    val familyName = item.driver?.familyName?.lowercase().orEmpty()

                    val headshot = item.driver?.headshotUrl
                        ?: headshotsMap[code]
                        ?: headshotsMap[number]
                        ?: headshotsMap[familyName]

                    // Infer constructor colors directly from the drivers' colors
                    val driverColor = driverColorsMap[code]
                        ?: driverColorsMap[number]
                        ?: driverColorsMap[familyName]

                    if (driverColor != null) {
                        val constructorId = item.constructors?.firstOrNull()?.constructorId
                        val constructorName = item.constructors?.firstOrNull()?.name
                        if (!constructorId.isNullOrBlank()) {
                            com.example.ui.theme.TeamColors.registerColor(constructorId, driverColor)
                        }
                        if (!constructorName.isNullOrBlank()) {
                            com.example.ui.theme.TeamColors.registerColor(constructorName, driverColor)
                        }
                    }

                    val enrichedDriver = item.driver?.copy(headshotUrl = headshot)
                    item.copy(driver = enrichedDriver, headshotUrl = headshot)
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
                val headshots = metadata.headshots
                val colors = metadata.driverColors
                raw.map { item ->
                    val code = item.driver?.code?.uppercase().orEmpty()
                    val num = item.driver?.permanentNumber.orEmpty()
                    val last = item.driver?.familyName?.lowercase().orEmpty()
                    val photo = item.driver?.headshotUrl ?: headshots[code] ?: headshots[num] ?: headshots[last]

                    val driverColor = colors[code] ?: colors[num] ?: colors[last]
                    if (driverColor != null) {
                        val cId = item.constructor?.constructorId
                        val cName = item.constructor?.name
                        if (!cId.isNullOrBlank()) com.example.ui.theme.TeamColors.registerColor(cId, driverColor)
                        if (!cName.isNullOrBlank()) com.example.ui.theme.TeamColors.registerColor(cName, driverColor)
                    }

                    item.copy(driver = item.driver?.copy(headshotUrl = photo))
                }
            }
        }

    suspend fun fetchQualifyingResults(round: String): Result<List<QualifyingResultItem>> =
        withContext(Dispatchers.IO) {
            cancellableResult {
                val root = service.getQualifyingResults(round)
                val raw = root.mrData?.raceTable?.races?.firstOrNull()?.qualifyingResults.orEmpty()
                val metadata = getDriverMetadata()
                val headshots = metadata.headshots
                val colors = metadata.driverColors
                raw.map { item ->
                    val code = item.driver?.code?.uppercase().orEmpty()
                    val num = item.driver?.permanentNumber.orEmpty()
                    val last = item.driver?.familyName?.lowercase().orEmpty()
                    val photo = item.driver?.headshotUrl ?: headshots[code] ?: headshots[num] ?: headshots[last]

                    val driverColor = colors[code] ?: colors[num] ?: colors[last]
                    if (driverColor != null) {
                        val cId = item.constructor?.constructorId
                        val cName = item.constructor?.name
                        if (!cId.isNullOrBlank()) com.example.ui.theme.TeamColors.registerColor(cId, driverColor)
                        if (!cName.isNullOrBlank()) com.example.ui.theme.TeamColors.registerColor(cName, driverColor)
                    }

                    item.copy(driver = item.driver?.copy(headshotUrl = photo))
                }
            }
        }

    suspend fun fetchSprintResults(round: String): Result<List<RaceResultItem>> =
        withContext(Dispatchers.IO) {
            cancellableResult {
                val root = service.getSprintResults(round)
                val raw = root.mrData?.raceTable?.races?.firstOrNull()?.sprintResults.orEmpty()
                val metadata = getDriverMetadata()
                val headshots = metadata.headshots
                val colors = metadata.driverColors
                raw.map { item ->
                    val code = item.driver?.code?.uppercase().orEmpty()
                    val num = item.driver?.permanentNumber.orEmpty()
                    val last = item.driver?.familyName?.lowercase().orEmpty()
                    val photo = item.driver?.headshotUrl ?: headshots[code] ?: headshots[num] ?: headshots[last]

                    val driverColor = colors[code] ?: colors[num] ?: colors[last]
                    if (driverColor != null) {
                        val cId = item.constructor?.constructorId
                        val cName = item.constructor?.name
                        if (!cId.isNullOrBlank()) com.example.ui.theme.TeamColors.registerColor(cId, driverColor)
                        if (!cName.isNullOrBlank()) com.example.ui.theme.TeamColors.registerColor(cName, driverColor)
                    }

                    item.copy(driver = item.driver?.copy(headshotUrl = photo))
                }
            }
        }

    private suspend fun fetchDriverMetadataFromApi(): DriverMetadata {
        val headshotsMap = mutableMapOf<String, String>()
        val driverColorsMap = mutableMapOf<String, androidx.compose.ui.graphics.Color>()
        try {
            val req = Request.Builder()
                .url("https://api.openf1.org/v1/drivers?session_key=latest")
                .build()
            httpClient.newCall(req).awaitResponse().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.startsWith("[")) {
                        val arr = JSONArray(body)
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

    private fun findOpenF1SessionKey(year: String, country: String, raceDate: String, sessionName: String): Int? {
        val mappedCountry = URLEncoder.encode(mapCountryForOpenF1(country), "UTF-8")
        val encSessionName = URLEncoder.encode(sessionName, "UTF-8")
        val y = if (year.isBlank() || year.equals("current", ignoreCase = true)) {
            Calendar.getInstance().get(Calendar.YEAR).toString()
        } else year

        // 1. Try with year
        val keyWithYear = querySessionKey("https://api.openf1.org/v1/sessions?year=$y&country_name=$mappedCountry&session_name=$encSessionName", raceDate)
        if (keyWithYear != null) return keyWithYear

        // 2. Try without year
        val keyWithoutYear = querySessionKey("https://api.openf1.org/v1/sessions?country_name=$mappedCountry&session_name=$encSessionName", raceDate)
        if (keyWithoutYear != null) return keyWithoutYear

        // 3. Alternate session name spellings
        val altName = when (sessionName) {
            "Practice 1" -> "FP1"
            "Practice 2" -> "FP2"
            "Practice 3" -> "FP3"
            "Sprint Qualifying" -> "Sprint Shootout"
            "Sprint Shootout" -> "Sprint Qualifying"
            else -> null
        }
        if (altName != null) {
            val encAlt = URLEncoder.encode(altName, "UTF-8")
            val keyAlt = querySessionKey("https://api.openf1.org/v1/sessions?country_name=$mappedCountry&session_name=$encAlt", raceDate)
            if (keyAlt != null) return keyAlt
        }

        return null
    }

    private fun querySessionKey(url: String, raceDate: String): Int? {
        return try {
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).awaitResponse().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                if (!body.startsWith("[")) return null
                val arr = JSONArray(body)
                if (arr.length() == 0) return null

                var bestKey: Int? = null
                var bestDiff = Long.MAX_VALUE
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val targetMillis = try {
                    if (raceDate.length >= 10) dateFormat.parse(raceDate.take(10))?.time else null
                } catch (_: Exception) { null }

                for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    val key = s.optInt("session_key", -1)
                    if (key <= 0) continue

                    val dateStart = s.optString("date_start", "")
                    if (targetMillis != null && dateStart.length >= 10) {
                        val sMillis = try { dateFormat.parse(dateStart.take(10))?.time } catch (_: Exception) { null }
                        if (sMillis != null) {
                            val diff = kotlin.math.abs(sMillis - targetMillis)
                            if (diff < bestDiff) {
                                bestDiff = diff
                                bestKey = key
                            }
                        } else if (bestKey == null) {
                            bestKey = key
                        }
                    } else if (bestKey == null) {
                        bestKey = key
                    }
                }
                bestKey
            }
        } catch (_: Exception) {
            null
        }
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

            // Fetch drivers for this session
            val driversReq = Request.Builder().url("https://api.openf1.org/v1/drivers?session_key=$sessionKey").build()
            val driversMap = mutableMapOf<Int, JSONObject>()
            httpClient.newCall(driversReq).awaitResponse().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.startsWith("[")) {
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val d = arr.optJSONObject(i) ?: continue
                            val num = d.optInt("driver_number", -1)
                            if (num > 0) driversMap[num] = d
                        }
                    }
                }
            }

            // Fetch laps for this session
            val lapsReq = Request.Builder().url("https://api.openf1.org/v1/laps?session_key=$sessionKey").build()
            val bestLapMap = mutableMapOf<Int, JSONObject>()
            val lapCountMap = mutableMapOf<Int, Int>()

            httpClient.newCall(lapsReq).awaitResponse().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.startsWith("[")) {
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val lap = arr.optJSONObject(i) ?: continue
                            val num = lap.optInt("driver_number", -1)
                            if (num <= 0) continue
                            lapCountMap[num] = (lapCountMap[num] ?: 0) + 1
                            val dur = lap.optDouble("lap_duration", 0.0)
                            if (dur > 0.0) {
                                val cur = bestLapMap[num]?.optDouble("lap_duration", 9999.0) ?: 9999.0
                                if (dur < cur) {
                                    bestLapMap[num] = lap
                                }
                            }
                        }
                    }
                }
            }

            val driverItems = driversMap.map { (num, dObj) ->
                val bestLap = bestLapMap[num]
                Triple(num, dObj, bestLap)
            }.sortedWith(
                compareBy<Triple<Int, JSONObject, JSONObject?>> { it.third == null || (it.third?.optDouble("lap_duration", 0.0) ?: 0.0) <= 0.0 }
                    .thenBy { it.third?.optDouble("lap_duration", 9999.0) ?: 9999.0 }
            )

            val leaderDur = driverItems.firstOrNull()?.third?.optDouble("lap_duration", 0.0) ?: 0.0
            if (leaderDur <= 0.0 || bestLapMap.isEmpty()) {
                return@cancellableResult emptyList()
            }

            driverItems.mapIndexed { index, (num, dObj, bestLap) ->
                val pos = (index + 1).toString()
                val fullName = dObj.optString("full_name", "")
                val lastName = dObj.optString("last_name", "")
                val acronym = dObj.optString("name_acronym", "")
                val teamName = dObj.optString("team_name", "")
                val headshotUrl = dObj.optString("headshot_url", "")
                val teamColour = dObj.optString("team_colour", "")

                val color = com.example.ui.theme.TeamColors.parseHex(teamColour)
                if (color != null) {
                    if (teamName.isNotBlank()) com.example.ui.theme.TeamColors.registerColor(teamName, color)
                    if (acronym.isNotBlank()) com.example.ui.theme.TeamColors.registerColor(acronym, color)
                }

                val bestDur = bestLap?.optDouble("lap_duration", 0.0) ?: 0.0
                val bestLapStr = if (bestDur > 0.0) formatLapTime(bestDur) else "--"
                val gapStr = when {
                    bestDur <= 0.0 -> "--"
                    index == 0 -> "Leader"
                    leaderDur > 0.0 -> String.format(Locale.US, "+%.3f", bestDur - leaderDur)
                    else -> "--"
                }

                val s1 = bestLap?.optDouble("duration_sector_1", 0.0) ?: 0.0
                val s2 = bestLap?.optDouble("duration_sector_2", 0.0) ?: 0.0
                val s3 = bestLap?.optDouble("duration_sector_3", 0.0) ?: 0.0

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

                PracticeTimingItem(
                    position = pos,
                    driverNumber = num.toString(),
                    driver = driver,
                    constructor = constructor,
                    bestLapTime = bestLapStr,
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
            var sessionKey = findOpenF1SessionKey(year, country, raceDate, "Sprint Qualifying")
            if (sessionKey == null) {
                sessionKey = findOpenF1SessionKey(year, country, raceDate, "Sprint Shootout")
            }
            if (sessionKey == null) return@cancellableResult emptyList()

            val driversReq = Request.Builder().url("https://api.openf1.org/v1/drivers?session_key=$sessionKey").build()
            val driversMap = mutableMapOf<Int, JSONObject>()
            httpClient.newCall(driversReq).awaitResponse().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.startsWith("[")) {
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val d = arr.optJSONObject(i) ?: continue
                            val num = d.optInt("driver_number", -1)
                            if (num > 0) driversMap[num] = d
                        }
                    }
                }
            }

            data class LapEntry(val driverNumber: Int, val lapDuration: Double, val dateStart: String)
            val lapsReq = Request.Builder().url("https://api.openf1.org/v1/laps?session_key=$sessionKey").build()
            val validLaps = mutableListOf<LapEntry>()

            httpClient.newCall(lapsReq).awaitResponse().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.startsWith("[")) {
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val lap = arr.optJSONObject(i) ?: continue
                            val num = lap.optInt("driver_number", -1)
                            val dur = lap.optDouble("lap_duration", 0.0)
                            val dateStart = lap.optString("date_start", "")
                            if (num > 0 && dur > 0.0 && dateStart.isNotBlank()) {
                                validLaps.add(LapEntry(num, dur, dateStart))
                            }
                        }
                    }
                }
            }

            if (validLaps.isEmpty()) {
                return@cancellableResult emptyList()
            }

            validLaps.sortBy { it.dateStart }

            // Find gaps of > 180 seconds between consecutive laps indicating breaks between SQ1, SQ2, and SQ3
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val breaks = mutableListOf<String>()
            for (i in 1 until validLaps.size) {
                val prevMillis = try { isoFormat.parse(validLaps[i - 1].dateStart.take(19))?.time ?: 0L } catch (_: Exception) { 0L }
                val curMillis = try { isoFormat.parse(validLaps[i].dateStart.take(19))?.time ?: 0L } catch (_: Exception) { 0L }
                if (curMillis > 0 && prevMillis > 0 && (curMillis - prevMillis) > 180_000L) {
                    breaks.add(validLaps[i].dateStart)
                    if (breaks.size == 2) break
                }
            }

            val break1 = breaks.getOrNull(0)
            val break2 = breaks.getOrNull(1)

            fun getStage(dateStr: String): Int {
                return when {
                    break1 != null && dateStr < break1 -> 1
                    break2 != null && dateStr < break2 -> 2
                    break1 != null && break2 != null -> 3
                    break1 != null -> 2
                    else -> 1
                }
            }

            val driverSq = mutableMapOf<Int, Array<Double?>>()
            driversMap.keys.forEach { driverSq[it] = arrayOf(null, null, null) }

            validLaps.forEach { lap ->
                val stageIdx = getStage(lap.dateStart) - 1
                val cur = driverSq[lap.driverNumber]?.get(stageIdx)
                if (cur == null || lap.lapDuration < cur) {
                    driverSq[lap.driverNumber]?.set(stageIdx, lap.lapDuration)
                }
            }

            val sortedDriverNums = driversMap.keys.sortedWith(
                compareBy<Int> { num ->
                    val sq3 = driverSq[num]?.get(2)
                    val sq2 = driverSq[num]?.get(1)
                    val sq1 = driverSq[num]?.get(0)
                    if (sq3 != null) 0 else if (sq2 != null) 1 else if (sq1 != null) 2 else 3
                }.thenBy { num ->
                    driverSq[num]?.get(2) ?: driverSq[num]?.get(1) ?: driverSq[num]?.get(0) ?: 9999.0
                }
            )

            sortedDriverNums.mapIndexed { index, num ->
                val dObj = driversMap[num]
                val fullName = dObj?.optString("full_name", "").orEmpty()
                val lastName = dObj?.optString("last_name", "").orEmpty()
                val acronym = dObj?.optString("name_acronym", "").orEmpty()
                val teamName = dObj?.optString("team_name", "").orEmpty()
                val headshotUrl = dObj?.optString("headshot_url", "").orEmpty()
                val teamColour = dObj?.optString("team_colour", "").orEmpty()

                val color = com.example.ui.theme.TeamColors.parseHex(teamColour)
                if (color != null) {
                    if (teamName.isNotBlank()) com.example.ui.theme.TeamColors.registerColor(teamName, color)
                    if (acronym.isNotBlank()) com.example.ui.theme.TeamColors.registerColor(acronym, color)
                }

                val sq1Val = driverSq[num]?.get(0)
                val sq2Val = driverSq[num]?.get(1)
                val sq3Val = driverSq[num]?.get(2)

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

                QualifyingResultItem(
                    number = num.toString(),
                    position = (index + 1).toString(),
                    driver = driver,
                    constructor = constructor,
                    q1 = if (sq1Val != null) formatLapTime(sq1Val) else null,
                    q2 = if (sq2Val != null) formatLapTime(sq2Val) else null,
                    q3 = if (sq3Val != null) formatLapTime(sq3Val) else null
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
