package rocks.urzahil.f1livetiming.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import rocks.urzahil.f1livetiming.data.api.NetworkClient
import rocks.urzahil.f1livetiming.data.api.PingResult
import rocks.urzahil.f1livetiming.data.model.ConstructorStandingItem
import rocks.urzahil.f1livetiming.data.model.DriverStandingItem
import rocks.urzahil.f1livetiming.data.model.FiaDocument
import rocks.urzahil.f1livetiming.data.model.PracticeTimingItem
import rocks.urzahil.f1livetiming.data.model.QualifyingResultItem
import rocks.urzahil.f1livetiming.data.model.RaceResultItem
import rocks.urzahil.f1livetiming.data.model.SeasonRaceItem
import rocks.urzahil.f1livetiming.data.model.WeekendSessionType
import rocks.urzahil.f1livetiming.data.repository.F1ChampionshipRepository
import rocks.urzahil.f1livetiming.data.repository.CalendarResultsCache
import rocks.urzahil.f1livetiming.data.repository.ExpiringListCache
import rocks.urzahil.f1livetiming.data.repository.FiaDocumentsRepository
import rocks.urzahil.f1livetiming.data.repository.ParsedDriverTiming
import rocks.urzahil.f1livetiming.data.repository.ParsedRaceControlMessage
import rocks.urzahil.f1livetiming.data.repository.ParsedSession
import rocks.urzahil.f1livetiming.data.repository.ParsedWeather
import rocks.urzahil.f1livetiming.data.repository.ServerSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.File
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

/** Pages listed on the "More tools" screen; add an entry here and a branch in MainContent to add a tool. */
enum class MoreTool {
    CHAMPIONSHIP_PREDICTOR,
    FIA_DOCUMENTS
}

/** Results for the selected race weekend, one list per session type. */
data class SessionResultsState(
    val race: List<RaceResultItem> = emptyList(),
    val qualifying: List<QualifyingResultItem> = emptyList(),
    val sprint: List<RaceResultItem> = emptyList(),
    val sprintQualifying: List<QualifyingResultItem> = emptyList(),
    val practice: List<PracticeTimingItem> = emptyList()
)

/**
 * "FIA Documents" tool: the race whose documents are listed, and the document shown in the PDF viewer.
 * Both null means the race list is showing.
 */
data class FiaDocumentsState(
    val race: SeasonRaceItem? = null,
    val documents: List<FiaDocument> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val document: FiaDocument? = null,
    val pdfFile: File? = null,
    val isPdfLoading: Boolean = false,
    val pdfError: String? = null
)

class F1ViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "F1ViewModel"
    }

    private val settingsRepo = ServerSettingsRepository(application)
    private val liveTimingCoordinator = LiveTimingCoordinator()
    private val championshipRepo = F1ChampionshipRepository()
    private val fiaDocumentsRepo = FiaDocumentsRepository(application.cacheDir)

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

    private val _raceControl = MutableStateFlow<List<ParsedRaceControlMessage>>(emptyList())
    val raceControl: StateFlow<List<ParsedRaceControlMessage>> = _raceControl.asStateFlow()

    private val _liveError = MutableStateFlow<String?>(null)
    val liveError: StateFlow<String?> = _liveError.asStateFlow()

    // Modals & Dialogs state
    private val _selectedDriverForStints = MutableStateFlow<ParsedDriverTiming?>(null)
    val selectedDriverForStints: StateFlow<ParsedDriverTiming?> = _selectedDriverForStints.asStateFlow()

    private val _showWeatherDialog = MutableStateFlow(false)
    val showWeatherDialog: StateFlow<Boolean> = _showWeatherDialog.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    // "More tools" hub and the tool page open on top of it
    private val _showMoreTools = MutableStateFlow(false)
    val showMoreTools: StateFlow<Boolean> = _showMoreTools.asStateFlow()

    private val _selectedMoreTool = MutableStateFlow<MoreTool?>(null)
    val selectedMoreTool: StateFlow<MoreTool?> = _selectedMoreTool.asStateFlow()

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

    // Round the standings were last updated after; null before the first race of the season
    private val _standingsRound = MutableStateFlow<Int?>(null)
    val standingsRound: StateFlow<Int?> = _standingsRound.asStateFlow()

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

    private val _sessionResults = MutableStateFlow(SessionResultsState())
    val sessionResults: StateFlow<SessionResultsState> = _sessionResults.asStateFlow()

    private val calendarResultsCache = CalendarResultsCache()

    private val _fiaDocuments = MutableStateFlow(FiaDocumentsState())
    val fiaDocuments: StateFlow<FiaDocumentsState> = _fiaDocuments.asStateFlow()

    // Documents keep being published during a race weekend, so lists are only cached briefly.
    private val fiaDocumentsCache = ExpiringListCache<String, FiaDocument>()
    private var fiaDocumentsJob: Job? = null
    private var fiaPdfJob: Job? = null

    // The next weekend, set only once the backend has documents for it (it has not started yet)
    private val _fiaUpcomingRace = MutableStateFlow<SeasonRaceItem?>(null)
    val fiaUpcomingRace: StateFlow<SeasonRaceItem?> = _fiaUpcomingRace.asStateFlow()
    private var fiaUpcomingJob: Job? = null

    private var pollingJob: Job? = null
    private var isAppVisible = false
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

    fun setMoreToolsVisible(visible: Boolean) {
        _showMoreTools.value = visible
        if (!visible) _selectedMoreTool.value = null
    }

    fun openMoreTool(tool: MoreTool?) {
        _selectedMoreTool.value = tool
        if (tool == MoreTool.CHAMPIONSHIP_PREDICTOR) {
            if (_driverStandings.value.isEmpty() && !_isChampionshipLoading.value) loadChampionshipData()
        }
        if (tool != null && _calendarRaces.value.isEmpty() && !_isCalendarLoading.value) loadCalendarData()
        // The FIA Documents tool always opens on its race list
        selectFiaDocumentsRace(null)
        if (tool == MoreTool.FIA_DOCUMENTS) checkFiaUpcomingRace()
    }

    /** Year and round the backend files [race]'s documents under, or null when the race has no round. */
    private fun fiaDocumentsKey(race: SeasonRaceItem): Pair<Int, Int>? {
        val year = race.season?.toIntOrNull() ?: _calendarSeason.value.toIntOrNull() ?: return null
        val round = race.round?.toIntOrNull() ?: return null
        return year to round
    }

    /** Lists the next weekend in the race list only when the FIA has already published documents for it. */
    private fun checkFiaUpcomingRace() {
        fiaUpcomingJob?.cancel()
        fiaUpcomingJob = null
        val race = FiaDocumentRaces.next(_calendarRaces.value, System.currentTimeMillis())
        val key = race?.let { fiaDocumentsKey(it) }
        if (race == null || key == null) {
            _fiaUpcomingRace.value = null
            return
        }
        val (year, round) = key
        fiaUpcomingJob = viewModelScope.launch {
            fiaDocumentsRepo.fetchDocuments(backendUrl.value, year, round).onSuccess { list ->
                // Opening the race then shows these documents straight from the cache
                fiaDocumentsCache["$year:$round"] = list
                _fiaUpcomingRace.value = race.takeIf { list.isNotEmpty() }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "FIA documents check for the next race failed (${error.javaClass.simpleName})")
            }
        }
    }

    /** Opens the document list of [race], or returns to the race list when null. */
    fun selectFiaDocumentsRace(race: SeasonRaceItem?) {
        openFiaDocument(null)
        fiaDocumentsJob?.cancel()
        fiaDocumentsJob = null
        _fiaDocuments.value = FiaDocumentsState(race = race)
        if (race != null) loadFiaDocuments()
    }

    fun loadFiaDocuments() {
        val race = _fiaDocuments.value.race ?: return
        val (year, round) = fiaDocumentsKey(race) ?: run {
            _fiaDocuments.update { it.copy(error = "This race has no round number.") }
            return
        }
        val cacheKey = "$year:$round"
        fiaDocumentsCache[cacheKey]?.let { cached ->
            _fiaDocuments.update { it.copy(documents = cached, isLoading = false, error = null) }
            return
        }
        fiaDocumentsJob?.cancel()
        fiaDocumentsJob = viewModelScope.launch {
            _fiaDocuments.update { it.copy(isLoading = true, error = null) }
            fiaDocumentsRepo.fetchDocuments(backendUrl.value, year, round).onSuccess { list ->
                fiaDocumentsCache[cacheKey] = list
                _fiaDocuments.update { it.copy(documents = list, isLoading = false) }
            }.onFailure { error ->
                // A cancelled request must not write its error into the next race's state.
                if (error is CancellationException) throw error
                Log.w(TAG, "FIA documents request failed (${error.javaClass.simpleName})")
                _fiaDocuments.update {
                    it.copy(isLoading = false, error = "Unable to load the FIA documents. Please try again.")
                }
            }
        }
    }

    /** Shows [document] in the PDF viewer, downloading it first; null closes the viewer. */
    fun openFiaDocument(document: FiaDocument?) {
        fiaPdfJob?.cancel()
        fiaPdfJob = null
        _fiaDocuments.update {
            it.copy(document = document, pdfFile = null, isPdfLoading = false, pdfError = null)
        }
        val url = document?.fileUrl ?: return
        fiaPdfJob = viewModelScope.launch {
            _fiaDocuments.update { it.copy(isPdfLoading = true) }
            fiaDocumentsRepo.downloadPdf(url).onSuccess { file ->
                _fiaDocuments.update { it.copy(pdfFile = file, isPdfLoading = false) }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "FIA document download failed (${error.javaClass.simpleName})")
                _fiaDocuments.update {
                    it.copy(isPdfLoading = false, pdfError = "Unable to download this document.")
                }
            }
        }
    }

    fun retryFiaDocument() {
        openFiaDocument(_fiaDocuments.value.document)
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
        _raceControl.value = snapshot.raceControl
        _liveDataStatus.value = snapshot.status
        _liveError.value = snapshot.error
    }


    fun loadChampionshipData() {
        viewModelScope.launch {
            _isChampionshipLoading.value = true
            _championshipError.value = null

            val driversResult = championshipRepo.fetchDriverStandings()
            driversResult.onSuccess { (season, list, round) ->
                _championshipSeason.value = season
                _driverStandings.value = list
                _standingsRound.value = round
            }.onFailure { error ->
                Log.w(TAG, "Driver standings request failed (${error.javaClass.simpleName})")
                _championshipError.value = "Unable to load driver standings. Please try again."
            }

            val constructorsResult = championshipRepo.fetchConstructorStandings()
            constructorsResult.onSuccess { (season, list, round) ->
                if (_championshipSeason.value.isBlank()) _championshipSeason.value = season
                if (_standingsRound.value == null) _standingsRound.value = round
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
                // The tool may have been opened before the calendar arrived
                if (_selectedMoreTool.value == MoreTool.FIA_DOCUMENTS) checkFiaUpcomingRace()
            }.onFailure { error ->
                Log.w(TAG, "Season calendar request failed (${error.javaClass.simpleName})")
                _calendarError.value = "Unable to load the season calendar. Please try again."
            }

            _isCalendarLoading.value = false
        }
    }

    fun selectCalendarRace(race: SeasonRaceItem?, initialSession: WeekendSessionType = WeekendSessionType.RACE) {
        sessionResultsJob?.cancel()
        sessionResultsJob = null
        _selectedCalendarRace.value = race
        // Results belong to a single race: never let the previous race's lists show for the next one.
        _sessionResults.value = SessionResultsState()
        _sessionResultsError.value = null
        _isSessionResultsLoading.value = false
        if (race != null) {
            _selectedSessionType.value = initialSession
            loadSessionResultsForSelectedRace(initialSession)
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
        sessionResultsJob = null
        _isSessionResultsLoading.value = false
        _sessionResultsError.value = null
        val race = _selectedCalendarRace.value ?: return
        val round = race.round.orEmpty()
        if (round.isBlank()) return
        val season = race.season.orEmpty().ifBlank { _calendarSeason.value }
        val country = race.circuit?.location?.country.orEmpty()
        val cacheKey = "$season:$round"

        when (type) {
            WeekendSessionType.RACE -> loadResults(
                label = "race results",
                cache = calendarResultsCache.race,
                cacheKey = cacheKey,
                store = { state, list -> state.copy(race = list) }
            ) { championshipRepo.fetchRaceResults(round) }

            WeekendSessionType.QUALIFYING -> loadResults(
                label = "qualifying results",
                cache = calendarResultsCache.qualifying,
                cacheKey = cacheKey,
                store = { state, list -> state.copy(qualifying = list) }
            ) { championshipRepo.fetchQualifyingResults(round) }

            WeekendSessionType.SPRINT -> loadResults(
                label = "sprint results",
                cache = calendarResultsCache.sprint,
                cacheKey = cacheKey,
                store = { state, list -> state.copy(sprint = list) }
            ) { championshipRepo.fetchSprintResults(round) }

            WeekendSessionType.SPRINT_QUALIFYING -> {
                val date = race.sprintQualifying?.date ?: race.sprintShootout?.date ?: race.date.orEmpty()
                loadResults(
                    label = "sprint qualifying results",
                    cache = calendarResultsCache.sprintQualifying,
                    cacheKey = cacheKey,
                    store = { state, list -> state.copy(sprintQualifying = list) }
                ) { championshipRepo.fetchSprintQualifyingResults(season, country, date) }
            }

            WeekendSessionType.FP1,
            WeekendSessionType.FP2,
            WeekendSessionType.FP3 -> {
                val (sessionName, scheduledDate) = when (type) {
                    WeekendSessionType.FP1 -> "Practice 1" to race.firstPractice?.date
                    WeekendSessionType.FP2 -> "Practice 2" to race.secondPractice?.date
                    else -> "Practice 3" to race.thirdPractice?.date
                }
                val date = scheduledDate ?: race.date.orEmpty()
                loadResults(
                    label = "practice timings",
                    cache = calendarResultsCache.practice,
                    cacheKey = "$cacheKey-${type.name}",
                    store = { state, list -> state.copy(practice = list) }
                ) { championshipRepo.fetchPracticeTimings(season, country, date, sessionName) }
            }
        }
    }

    /**
     * Shows [cache] hits immediately; otherwise fetches, stores non-empty replies in the cache and
     * publishes the list (or an empty list plus an error message on failure) into [_sessionResults].
     */
    private fun <T> loadResults(
        label: String,
        cache: ExpiringListCache<String, T>,
        cacheKey: String,
        store: (SessionResultsState, List<T>) -> SessionResultsState,
        fetch: suspend () -> Result<List<T>>
    ) {
        cache[cacheKey]?.let { cached ->
            _sessionResults.update { store(it, cached) }
            return
        }
        sessionResultsJob = viewModelScope.launch {
            _isSessionResultsLoading.value = true
            fetch().onSuccess { list ->
                if (list.isNotEmpty()) cache[cacheKey] = list
                _sessionResults.update { store(it, list) }
            }.onFailure { error ->
                Log.w(TAG, "Request for $label failed (${error.javaClass.simpleName})")
                _sessionResults.update { store(it, emptyList()) }
                _sessionResultsError.value = "Unable to load $label. Please try again."
            }
            _isSessionResultsLoading.value = false
        }
    }
}
