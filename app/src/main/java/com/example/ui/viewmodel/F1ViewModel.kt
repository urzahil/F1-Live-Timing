package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.NetworkClient
import com.example.data.api.PingResult
import com.example.data.model.ConstructorStandingItem
import com.example.data.model.DriverStandingItem
import com.example.data.model.PracticeTimingItem
import com.example.data.model.QualifyingResultItem
import com.example.data.model.RaceResultItem
import com.example.data.model.SeasonRaceItem
import com.example.data.model.WeekendSessionType
import com.example.data.repository.F1ChampionshipRepository
import com.example.data.repository.CalendarResultsCache
import com.example.data.repository.ParsedDriverTiming
import com.example.data.repository.ParsedSession
import com.example.data.repository.ParsedWeather
import com.example.data.repository.ServerSettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

enum class F1Tab {
    LIVE_TIMING,
    STANDINGS,
    CALENDAR
}

enum class StandingsCategory {
    DRIVERS,
    CONSTRUCTORS
}

class F1ViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "F1ViewModel"
    }

    private val settingsRepo = ServerSettingsRepository(application)
    private val liveTimingCoordinator = LiveTimingCoordinator()
    private val championshipRepo = F1ChampionshipRepository()

    val backendUrl: StateFlow<String> = settingsRepo.backendUrl
    val showLatestSession: StateFlow<Boolean> = settingsRepo.showLatestSession

    private val _selectedTab = MutableStateFlow(F1Tab.LIVE_TIMING)
    val selectedTab: StateFlow<F1Tab> = _selectedTab.asStateFlow()

    private val _isPollingActive = MutableStateFlow(true)
    val isPollingActive: StateFlow<Boolean> = _isPollingActive.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Live session & telemetry state
    private val _session = MutableStateFlow(ParsedSession())
    val session: StateFlow<ParsedSession> = _session.asStateFlow()

    private val _weather = MutableStateFlow(ParsedWeather())
    val weather: StateFlow<ParsedWeather> = _weather.asStateFlow()

    private val _liveTimings = MutableStateFlow<List<ParsedDriverTiming>>(emptyList())
    val liveTimings: StateFlow<List<ParsedDriverTiming>> = _liveTimings.asStateFlow()

    private val _liveError = MutableStateFlow<String?>(null)
    val liveError: StateFlow<String?> = _liveError.asStateFlow()

    // Modals & Dialogs state
    private val _selectedDriverForStints = MutableStateFlow<ParsedDriverTiming?>(null)
    val selectedDriverForStints: StateFlow<ParsedDriverTiming?> = _selectedDriverForStints.asStateFlow()

    private val _showWeatherDialog = MutableStateFlow(false)
    val showWeatherDialog: StateFlow<Boolean> = _showWeatherDialog.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    // Connection testing
    private val _pingResult = MutableStateFlow<PingResult?>(null)
    val pingResult: StateFlow<PingResult?> = _pingResult.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    // Championship Standings state
    private val _standingsCategory = MutableStateFlow(StandingsCategory.DRIVERS)
    val standingsCategory: StateFlow<StandingsCategory> = _standingsCategory.asStateFlow()

    private val _driverStandings = MutableStateFlow<List<DriverStandingItem>>(emptyList())
    val driverStandings: StateFlow<List<DriverStandingItem>> = _driverStandings.asStateFlow()

    private val _constructorStandings = MutableStateFlow<List<ConstructorStandingItem>>(emptyList())
    val constructorStandings: StateFlow<List<ConstructorStandingItem>> = _constructorStandings.asStateFlow()

    private val _championshipSeason = MutableStateFlow("")
    val championshipSeason: StateFlow<String> = _championshipSeason.asStateFlow()

    private val _isChampionshipLoading = MutableStateFlow(false)
    val isChampionshipLoading: StateFlow<Boolean> = _isChampionshipLoading.asStateFlow()

    private val _championshipError = MutableStateFlow<String?>(null)
    val championshipError: StateFlow<String?> = _championshipError.asStateFlow()

    // Season Calendar state
    private val _calendarSeason = MutableStateFlow("")
    val calendarSeason: StateFlow<String> = _calendarSeason.asStateFlow()

    private val _calendarRaces = MutableStateFlow<List<SeasonRaceItem>>(emptyList())
    val calendarRaces: StateFlow<List<SeasonRaceItem>> = _calendarRaces.asStateFlow()

    private val _isCalendarLoading = MutableStateFlow(false)
    val isCalendarLoading: StateFlow<Boolean> = _isCalendarLoading.asStateFlow()

    private val _calendarError = MutableStateFlow<String?>(null)
    val calendarError: StateFlow<String?> = _calendarError.asStateFlow()

    // Weekend Session Detail & Results state
    private val _selectedCalendarRace = MutableStateFlow<SeasonRaceItem?>(null)
    val selectedCalendarRace: StateFlow<SeasonRaceItem?> = _selectedCalendarRace.asStateFlow()

    private val _selectedSessionType = MutableStateFlow(WeekendSessionType.RACE)
    val selectedSessionType: StateFlow<WeekendSessionType> = _selectedSessionType.asStateFlow()

    private val _isSessionResultsLoading = MutableStateFlow(false)
    val isSessionResultsLoading: StateFlow<Boolean> = _isSessionResultsLoading.asStateFlow()

    private val _sessionResultsError = MutableStateFlow<String?>(null)
    val sessionResultsError: StateFlow<String?> = _sessionResultsError.asStateFlow()

    private val _raceResults = MutableStateFlow<List<RaceResultItem>>(emptyList())
    val raceResults: StateFlow<List<RaceResultItem>> = _raceResults.asStateFlow()

    private val _qualifyingResults = MutableStateFlow<List<QualifyingResultItem>>(emptyList())
    val qualifyingResults: StateFlow<List<QualifyingResultItem>> = _qualifyingResults.asStateFlow()

    private val _sprintResults = MutableStateFlow<List<RaceResultItem>>(emptyList())
    val sprintResults: StateFlow<List<RaceResultItem>> = _sprintResults.asStateFlow()

    private val _sprintQualifyingResults = MutableStateFlow<List<QualifyingResultItem>>(emptyList())
    val sprintQualifyingResults: StateFlow<List<QualifyingResultItem>> = _sprintQualifyingResults.asStateFlow()

    private val _practiceTimings = MutableStateFlow<List<PracticeTimingItem>>(emptyList())
    val practiceTimings: StateFlow<List<PracticeTimingItem>> = _practiceTimings.asStateFlow()

    private val calendarResultsCache = CalendarResultsCache()

    private var pollingJob: Job? = null
    private var isAppVisible = true
    private var sessionResultsJob: Job? = null
    private val refreshMutex = Mutex()
    private val manualRefreshPending = AtomicBoolean(false)

    private val _liveDataStatus = MutableStateFlow("Waiting for the first refresh")
    val liveDataStatus: StateFlow<String> = _liveDataStatus.asStateFlow()

    private val refreshPolicy = LiveTimingRefreshPolicy()

    init {
        startPolling()
        loadChampionshipData()
        loadCalendarData()
    }

    fun setAppVisible(visible: Boolean) {
        isAppVisible = visible
        if (!visible) {
            pollingJob?.cancel()
            pollingJob = null
        } else if (_isPollingActive.value) {
            startPolling()
        }
    }

    fun selectTab(tab: F1Tab) {
        _selectedTab.value = tab
        if (tab == F1Tab.STANDINGS && _driverStandings.value.isEmpty()) {
            loadChampionshipData()
        } else if (tab == F1Tab.CALENDAR && _calendarRaces.value.isEmpty()) {
            loadCalendarData()
        }
    }

    fun setStandingsCategory(category: StandingsCategory) {
        _standingsCategory.value = category
    }

    fun togglePolling() {
        val next = !_isPollingActive.value
        _isPollingActive.value = next
        if (next) {
            startPolling()
        } else {
            pollingJob?.cancel()
        }
    }

    fun refreshLive() {
        viewModelScope.launch {
            // A manual refresh never queues behind an in-flight poll; it runs immediately if free.
            if (!refreshMutex.tryLock()) {
                // Coalesce repeated taps into one refresh after the current request cycle finishes.
                manualRefreshPending.set(true)
                return@launch
            }
            _isRefreshing.value = true
            try {
                fetchLiveCycleLocked(force = true)
            } finally {
                _isRefreshing.value = false
                refreshMutex.unlock()
                runPendingManualRefresh()
            }
        }
    }

    fun updateBackendUrl(url: String) {
        settingsRepo.updateBackendUrl(url)
        liveTimingCoordinator.resetForBackendChange()
        _pingResult.value = null
        restartPolling()
    }

    fun updateShowLatestSession(show: Boolean) {
        settingsRepo.updateShowLatestSession(show)
    }

    fun testBackendConnection(url: String) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _pingResult.value = null
            val result = NetworkClient.pingBackend(url)
            _pingResult.value = result
            _isTestingConnection.value = false
        }
    }

    fun openDriverStints(driver: ParsedDriverTiming) {
        _selectedDriverForStints.value = driver
    }

    fun closeDriverStints() {
        _selectedDriverForStints.value = null
    }

    fun setWeatherDialogVisible(visible: Boolean) {
        _showWeatherDialog.value = visible
    }

    fun setSettingsDialogVisible(visible: Boolean) {
        _showSettingsDialog.value = visible
    }

    private fun restartPolling() {
        pollingJob?.cancel()
        if (isAppVisible && _isPollingActive.value) startPolling()
    }

    private fun startPolling() {
        if (!isAppVisible || !_isPollingActive.value) return
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                if (_isPollingActive.value) {
                    fetchLiveCycle()
                }
                // The delay follows the session state and begins after the previous cycle finishes,
                // so a slow multi-request cycle cannot build a queue of overlapping refreshes.
                delay(refreshPolicy.pollIntervalMillis(_session.value.isLive))
            }
        }
    }

    private suspend fun fetchLiveCycle(force: Boolean = false) {
        if (!refreshMutex.tryLock()) return
        try {
            fetchLiveCycleLocked(force)
        } finally {
            refreshMutex.unlock()
            runPendingManualRefresh()
        }
    }

    private fun runPendingManualRefresh() {
        if (manualRefreshPending.compareAndSet(true, false)) {
            refreshLive()
        }
    }

    private suspend fun fetchLiveCycleLocked(force: Boolean) {
        val snapshot = liveTimingCoordinator.refresh(
            baseUrl = backendUrl.value,
            force = force,
            showLatestSession = showLatestSession.value
        )
        _session.value = snapshot.session
        _weather.value = snapshot.weather
        _liveTimings.value = snapshot.timings
        _liveDataStatus.value = snapshot.status
        _liveError.value = snapshot.error
    }


    fun loadChampionshipData() {
        viewModelScope.launch {
            _isChampionshipLoading.value = true
            _championshipError.value = null

            val driversResult = championshipRepo.fetchDriverStandings()
            driversResult.onSuccess { (season, list) ->
                _championshipSeason.value = season
                _driverStandings.value = list
            }.onFailure { error ->
                Log.w(TAG, "Driver standings request failed (${error.javaClass.simpleName})")
                _championshipError.value = "Unable to load driver standings. Please try again."
            }

            val constructorsResult = championshipRepo.fetchConstructorStandings()
            constructorsResult.onSuccess { (season, list) ->
                if (_championshipSeason.value.isBlank()) _championshipSeason.value = season
                _constructorStandings.value = list
            }.onFailure { error ->
                Log.w(TAG, "Constructor standings request failed (${error.javaClass.simpleName})")
                if (_driverStandings.value.isEmpty()) {
                    _championshipError.value = "Unable to load constructor standings. Please try again."
                }
            }

            _isChampionshipLoading.value = false
        }
    }

    fun loadCalendarData() {
        viewModelScope.launch {
            _isCalendarLoading.value = true
            _calendarError.value = null

            val result = championshipRepo.fetchCurrentCalendar()
            result.onSuccess { (season, races) ->
                _calendarSeason.value = season
                _calendarRaces.value = races
            }.onFailure { error ->
                Log.w(TAG, "Season calendar request failed (${error.javaClass.simpleName})")
                _calendarError.value = "Unable to load the season calendar. Please try again."
            }

            _isCalendarLoading.value = false
        }
    }

    fun selectCalendarRace(race: SeasonRaceItem?, initialSession: WeekendSessionType = WeekendSessionType.RACE) {
        _selectedCalendarRace.value = race
        if (race != null) {
            _selectedSessionType.value = initialSession
            loadSessionResultsForSelectedRace(initialSession)
        } else {
            _raceResults.value = emptyList()
            _qualifyingResults.value = emptyList()
            _sprintResults.value = emptyList()
            _sprintQualifyingResults.value = emptyList()
            _practiceTimings.value = emptyList()
            _sessionResultsError.value = null
        }
    }

    fun selectWeekendSession(type: WeekendSessionType) {
        _selectedSessionType.value = type
        loadSessionResultsForSelectedRace(type)
    }

    fun retrySessionResults() {
        loadSessionResultsForSelectedRace(_selectedSessionType.value)
    }

    private fun loadSessionResultsForSelectedRace(type: WeekendSessionType) {
        sessionResultsJob?.cancel()
        _isSessionResultsLoading.value = false
        _sessionResultsError.value = null
        val race = _selectedCalendarRace.value ?: return
        val round = race.round.orEmpty()
        if (round.isBlank()) return
        val resultCacheKey = "${race.season.orEmpty().ifBlank { _calendarSeason.value }}:$round"

        when (type) {
            WeekendSessionType.RACE -> {
                val cached = calendarResultsCache.race[resultCacheKey]
                if (cached != null) {
                    _raceResults.value = cached
                    _sessionResultsError.value = null
                    return
                }
                sessionResultsJob = viewModelScope.launch {
                    _isSessionResultsLoading.value = true
                    _sessionResultsError.value = null
                    val res = championshipRepo.fetchRaceResults(round)
                    res.onSuccess { list ->
                        if (list.isNotEmpty()) {
                            calendarResultsCache.race[resultCacheKey] = list
                            _raceResults.value = list
                        } else if (_raceResults.value.isEmpty()) {
                            _raceResults.value = emptyList()
                        }
                    }.onFailure { error ->
                        Log.w(TAG, "Race results request failed (${error.javaClass.simpleName})")
                        _raceResults.value = emptyList()
                        _sessionResultsError.value = "Unable to load race results. Please try again."
                    }
                    _isSessionResultsLoading.value = false
                }
            }
            WeekendSessionType.QUALIFYING -> {
                val cached = calendarResultsCache.qualifying[resultCacheKey]
                if (cached != null) {
                    _qualifyingResults.value = cached
                    _sessionResultsError.value = null
                    return
                }
                sessionResultsJob = viewModelScope.launch {
                    _isSessionResultsLoading.value = true
                    _sessionResultsError.value = null
                    val res = championshipRepo.fetchQualifyingResults(round)
                    res.onSuccess { list ->
                        if (list.isNotEmpty()) {
                            calendarResultsCache.qualifying[resultCacheKey] = list
                            _qualifyingResults.value = list
                        } else if (_qualifyingResults.value.isEmpty()) {
                            _qualifyingResults.value = emptyList()
                        }
                    }.onFailure { error ->
                        Log.w(TAG, "Qualifying results request failed (${error.javaClass.simpleName})")
                        _qualifyingResults.value = emptyList()
                        _sessionResultsError.value = "Unable to load qualifying results. Please try again."
                    }
                    _isSessionResultsLoading.value = false
                }
            }
            WeekendSessionType.SPRINT -> {
                val cached = calendarResultsCache.sprint[resultCacheKey]
                if (cached != null) {
                    _sprintResults.value = cached
                    _sessionResultsError.value = null
                    return
                }
                sessionResultsJob = viewModelScope.launch {
                    _isSessionResultsLoading.value = true
                    _sessionResultsError.value = null
                    val res = championshipRepo.fetchSprintResults(round)
                    res.onSuccess { list ->
                        if (list.isNotEmpty()) {
                            calendarResultsCache.sprint[resultCacheKey] = list
                            _sprintResults.value = list
                        } else if (_sprintResults.value.isEmpty()) {
                            _sprintResults.value = emptyList()
                        }
                    }.onFailure { error ->
                        Log.w(TAG, "Sprint results request failed (${error.javaClass.simpleName})")
                        _sprintResults.value = emptyList()
                        _sessionResultsError.value = "Unable to load sprint results. Please try again."
                    }
                    _isSessionResultsLoading.value = false
                }
            }
            WeekendSessionType.SPRINT_QUALIFYING -> {
                val cached = calendarResultsCache.sprintQualifying[resultCacheKey]
                if (cached != null) {
                    _sprintQualifyingResults.value = cached
                    _sessionResultsError.value = null
                    return
                }
                sessionResultsJob = viewModelScope.launch {
                    _isSessionResultsLoading.value = true
                    _sessionResultsError.value = null
                    val year = race.season.orEmpty().ifBlank { _calendarSeason.value }
                    val country = race.circuit?.location?.country.orEmpty()
                    val raceDate = race.sprintQualifying?.date ?: race.sprintShootout?.date ?: race.date.orEmpty()
                    val res = championshipRepo.fetchSprintQualifyingResults(year, country, raceDate)
                    res.onSuccess { list ->
                        if (list.isNotEmpty()) {
                            calendarResultsCache.sprintQualifying[resultCacheKey] = list
                            _sprintQualifyingResults.value = list
                        } else {
                            _sprintQualifyingResults.value = emptyList()
                        }
                    }.onFailure { error ->
                        Log.w(TAG, "Sprint qualifying results request failed (${error.javaClass.simpleName})")
                        _sprintQualifyingResults.value = emptyList()
                        _sessionResultsError.value = "Unable to load sprint qualifying results. Please try again."
                    }
                    _isSessionResultsLoading.value = false
                }
            }
            WeekendSessionType.FP1,
            WeekendSessionType.FP2,
            WeekendSessionType.FP3 -> {
                val cacheKey = "$resultCacheKey-${type.name}"
                val cached = calendarResultsCache.practice[cacheKey]
                if (cached != null) {
                    _practiceTimings.value = cached
                    _sessionResultsError.value = null
                    return
                }
                sessionResultsJob = viewModelScope.launch {
                    _isSessionResultsLoading.value = true
                    _sessionResultsError.value = null
                    val sessionName = when (type) {
                        WeekendSessionType.FP1 -> "Practice 1"
                        WeekendSessionType.FP2 -> "Practice 2"
                        WeekendSessionType.FP3 -> "Practice 3"
                        else -> "Practice 1"
                    }
                    val sessionDate = when (type) {
                        WeekendSessionType.FP1 -> race.firstPractice?.date ?: race.date.orEmpty()
                        WeekendSessionType.FP2 -> race.secondPractice?.date ?: race.date.orEmpty()
                        WeekendSessionType.FP3 -> race.thirdPractice?.date ?: race.date.orEmpty()
                        else -> race.date.orEmpty()
                    }
                    val year = race.season.orEmpty().ifBlank { _calendarSeason.value }
                    val country = race.circuit?.location?.country.orEmpty()
                    val res = championshipRepo.fetchPracticeTimings(year, country, sessionDate, sessionName)
                    res.onSuccess { list ->
                        if (list.isNotEmpty()) {
                            calendarResultsCache.practice[cacheKey] = list
                            _practiceTimings.value = list
                        } else if (_practiceTimings.value.isEmpty()) {
                            _practiceTimings.value = emptyList()
                        }
                    }.onFailure { error ->
                        Log.w(TAG, "Practice timings request failed (${error.javaClass.simpleName})")
                        _practiceTimings.value = emptyList()
                        _sessionResultsError.value = "Unable to load practice timings. Please try again."
                    }
                    _isSessionResultsLoading.value = false
                }
            }
        }
    }
}
