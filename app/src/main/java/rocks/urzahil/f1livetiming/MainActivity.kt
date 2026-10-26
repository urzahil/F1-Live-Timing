package rocks.urzahil.f1livetiming

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rocks.urzahil.f1livetiming.ui.components.DriverTyreStintDialog
import rocks.urzahil.f1livetiming.ui.components.ServerSettingsDialog
import rocks.urzahil.f1livetiming.ui.components.WeatherDialog
import rocks.urzahil.f1livetiming.ui.screens.CalendarScreen
import rocks.urzahil.f1livetiming.ui.screens.ChampionshipPredictorScreen
import rocks.urzahil.f1livetiming.ui.screens.FiaDocumentsScreen
import rocks.urzahil.f1livetiming.ui.screens.LiveTimingScreen
import rocks.urzahil.f1livetiming.ui.screens.MoreToolsScreen
import rocks.urzahil.f1livetiming.ui.screens.StandingsScreen
import rocks.urzahil.f1livetiming.ui.screens.ToolPageScaffold
import rocks.urzahil.f1livetiming.ui.screens.fiaDocumentsTitle
import rocks.urzahil.f1livetiming.ui.screens.pageTitle
import rocks.urzahil.f1livetiming.ui.theme.F1CarbonBackground
import rocks.urzahil.f1livetiming.ui.theme.F1CardSurface
import rocks.urzahil.f1livetiming.ui.theme.F1LiveTimingTheme
import rocks.urzahil.f1livetiming.ui.theme.F1PitWallSurface
import rocks.urzahil.f1livetiming.ui.theme.F1Red
import rocks.urzahil.f1livetiming.ui.theme.F1TextMuted
import rocks.urzahil.f1livetiming.ui.theme.F1TextPrimary
import rocks.urzahil.f1livetiming.ui.theme.F1TextSecondary
import rocks.urzahil.f1livetiming.ui.viewmodel.F1Tab
import rocks.urzahil.f1livetiming.ui.viewmodel.F1ViewModel
import rocks.urzahil.f1livetiming.ui.viewmodel.MoreTool

class MainActivity : ComponentActivity() {

    private val viewModel: F1ViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        viewModel.setAppVisible(true)
    }

    override fun onStop() {
        viewModel.setAppVisible(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            F1LiveTimingTheme {
                MainContent(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(viewModel: F1ViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val liveTimings by viewModel.liveTimings.collectAsStateWithLifecycle()
    val raceControl by viewModel.raceControl.collectAsStateWithLifecycle()
    val trackStatus by viewModel.trackStatus.collectAsStateWithLifecycle()
    val isPolling by viewModel.isPollingActive.collectAsStateWithLifecycle()
    val backendUrl by viewModel.backendUrl.collectAsStateWithLifecycle()
    val liveError by viewModel.liveError.collectAsStateWithLifecycle()
    val liveDataStatus by viewModel.liveDataStatus.collectAsStateWithLifecycle()

    val selectedDriver by viewModel.selectedDriverForStints.collectAsStateWithLifecycle()
    val showWeatherDialog by viewModel.showWeatherDialog.collectAsStateWithLifecycle()
    val showSettingsDialog by viewModel.showSettingsDialog.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingConnection.collectAsStateWithLifecycle()
    val pingResult by viewModel.pingResult.collectAsStateWithLifecycle()
    val showLatestSession by viewModel.showLatestSession.collectAsStateWithLifecycle()

    val standingsCategory by viewModel.standingsCategory.collectAsStateWithLifecycle()
    val championshipSeason by viewModel.championshipSeason.collectAsStateWithLifecycle()
    val driverStandings by viewModel.driverStandings.collectAsStateWithLifecycle()
    val constructorStandings by viewModel.constructorStandings.collectAsStateWithLifecycle()
    val isChampionshipLoading by viewModel.isChampionshipLoading.collectAsStateWithLifecycle()
    val championshipError by viewModel.championshipError.collectAsStateWithLifecycle()

    val calendarSeason by viewModel.calendarSeason.collectAsStateWithLifecycle()
    val calendarRaces by viewModel.calendarRaces.collectAsStateWithLifecycle()
    val isCalendarLoading by viewModel.isCalendarLoading.collectAsStateWithLifecycle()
    val calendarError by viewModel.calendarError.collectAsStateWithLifecycle()

    val selectedCalendarRace by viewModel.selectedCalendarRace.collectAsStateWithLifecycle()
    val selectedSessionType by viewModel.selectedSessionType.collectAsStateWithLifecycle()
    val sessionResults by viewModel.sessionResults.collectAsStateWithLifecycle()
    val isSessionResultsLoading by viewModel.isSessionResultsLoading.collectAsStateWithLifecycle()
    val sessionResultsError by viewModel.sessionResultsError.collectAsStateWithLifecycle()

    val calendarListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    val standingsRound by viewModel.standingsRound.collectAsStateWithLifecycle()
    val showMoreTools by viewModel.showMoreTools.collectAsStateWithLifecycle()
    val selectedMoreTool by viewModel.selectedMoreTool.collectAsStateWithLifecycle()
    val fiaDocuments by viewModel.fiaDocuments.collectAsStateWithLifecycle()
    val fiaUpcomingRace by viewModel.fiaUpcomingRace.collectAsStateWithLifecycle()
    var overflowMenuOpen by remember { mutableStateOf(false) }

    // "More tools" pages cover the whole screen; back returns from a tool to the hub, then to the tabs
    selectedMoreTool?.let { tool ->
        // The FIA documents tool has its own levels (race list, documents, viewer); back climbs them one by one
        val isFiaDocuments = tool == MoreTool.FIA_DOCUMENTS
        val title = (if (isFiaDocuments) fiaDocuments.fiaDocumentsTitle() else null) ?: tool.pageTitle
        val onBack = {
            when {
                isFiaDocuments && fiaDocuments.document != null -> viewModel.openFiaDocument(null)
                isFiaDocuments && fiaDocuments.race != null -> viewModel.selectFiaDocumentsRace(null)
                else -> viewModel.openMoreTool(null)
            }
        }
        ToolPageScaffold(title = title, onBack = onBack) { contentPadding ->
            when (tool) {
                MoreTool.CHAMPIONSHIP_PREDICTOR -> ChampionshipPredictorScreen(
                    drivers = driverStandings,
                    constructors = constructorStandings,
                    calendar = calendarRaces,
                    standingsRound = standingsRound,
                    isLoading = isChampionshipLoading || isCalendarLoading,
                    error = championshipError ?: calendarError,
                    onRetry = {
                        viewModel.loadChampionshipData()
                        viewModel.loadCalendarData()
                    },
                    contentPadding = contentPadding
                )
                MoreTool.FIA_DOCUMENTS -> FiaDocumentsScreen(
                    races = calendarRaces,
                    upcomingRace = fiaUpcomingRace,
                    isCalendarLoading = isCalendarLoading,
                    calendarError = calendarError,
                    onRetryCalendar = { viewModel.loadCalendarData() },
                    state = fiaDocuments,
                    onSelectRace = { race -> viewModel.selectFiaDocumentsRace(race) },
                    onRetryDocuments = { viewModel.loadFiaDocuments() },
                    onOpenDocument = { document -> viewModel.openFiaDocument(document) },
                    onRetryDocument = { viewModel.retryFiaDocument() },
                    contentPadding = contentPadding
                )
            }
        }
        return
    }
    if (showMoreTools) {
        MoreToolsScreen(
            onOpenTool = { tool -> viewModel.openMoreTool(tool) },
            onBack = { viewModel.setMoreToolsVisible(false) }
        )
        return
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(F1CarbonBackground),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(F1Red, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsMotorsports,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(id = R.string.app_name).uppercase(),
                                color = F1TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (session.isLive) "LIVE TELEMETRY FEED" else "PIT WALL MONITOR",
                                color = if (session.isLive) Color(0xFF2ECC71) else F1TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                actions = {
                    // Polling controls only affect live timing, so they only show on that tab
                    if (selectedTab == F1Tab.LIVE_TIMING) {
                        IconButton(
                            onClick = { viewModel.togglePolling() },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("toggle_polling_button")
                        ) {
                            Icon(
                                imageVector = if (isPolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPolling) "Pause Polling" else "Resume Polling",
                                tint = if (isPolling) Color(0xFF2ECC71) else F1TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.refreshLive() },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("refresh_live_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = F1TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { overflowMenuOpen = true },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("overflow_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = F1TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = overflowMenuOpen,
                            onDismissRequest = { overflowMenuOpen = false },
                            modifier = Modifier.background(F1CardSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("More tools", color = F1TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Apps, contentDescription = null, tint = F1TextSecondary) },
                                onClick = {
                                    overflowMenuOpen = false
                                    viewModel.setMoreToolsVisible(true)
                                },
                                modifier = Modifier.testTag("menu_more_tools")
                            )
                            DropdownMenuItem(
                                text = { Text("Settings", color = F1TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = F1TextSecondary) },
                                onClick = {
                                    overflowMenuOpen = false
                                    viewModel.setSettingsDialogVisible(true)
                                },
                                modifier = Modifier.testTag("settings_button")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = F1PitWallSurface,
                    titleContentColor = F1TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = F1PitWallSurface,
                contentColor = F1TextSecondary
            ) {
                NavigationBarItem(
                    selected = selectedTab == F1Tab.LIVE_TIMING,
                    onClick = { viewModel.selectTab(F1Tab.LIVE_TIMING) },
                    icon = { Icon(Icons.Default.Speed, contentDescription = "Live Timing") },
                    label = { Text("Timing", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = F1Red,
                        indicatorColor = F1Red,
                        unselectedIconColor = F1TextMuted,
                        unselectedTextColor = F1TextMuted
                    ),
                    modifier = Modifier.testTag("bottom_tab_live")
                )
                NavigationBarItem(
                    selected = selectedTab == F1Tab.STANDINGS,
                    onClick = { viewModel.selectTab(F1Tab.STANDINGS) },
                    icon = { Icon(Icons.Default.EmojiEvents, contentDescription = "Standings") },
                    label = { Text("Standings", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = F1Red,
                        indicatorColor = F1Red,
                        unselectedIconColor = F1TextMuted,
                        unselectedTextColor = F1TextMuted
                    ),
                    modifier = Modifier.testTag("bottom_tab_standings")
                )
                NavigationBarItem(
                    selected = selectedTab == F1Tab.CALENDAR,
                    onClick = { viewModel.selectTab(F1Tab.CALENDAR) },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar") },
                    label = { Text("Calendar", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = F1Red,
                        indicatorColor = F1Red,
                        unselectedIconColor = F1TextMuted,
                        unselectedTextColor = F1TextMuted
                    ),
                    modifier = Modifier.testTag("bottom_tab_calendar")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                F1Tab.LIVE_TIMING -> {
                    LiveTimingScreen(
                        session = session,
                        weather = weather,
                        liveTimings = liveTimings,
                        raceControl = raceControl,
                        trackStatus = trackStatus,
                        isPolling = isPolling,
                        backendUrl = backendUrl,
                        liveError = liveError,
                        liveDataStatus = liveDataStatus,
                        showLatestSession = showLatestSession,
                        onTogglePolling = { viewModel.togglePolling() },
                        onRefresh = { viewModel.refreshLive() },
                        onOpenWeather = { viewModel.setWeatherDialogVisible(true) },
                        onOpenSettings = { viewModel.setSettingsDialogVisible(true) },
                        onSelectDriver = { driver -> viewModel.openDriverStints(driver) },
                        onNavigateToStandings = { viewModel.selectTab(F1Tab.STANDINGS) },
                        onNavigateToCalendar = { viewModel.selectTab(F1Tab.CALENDAR) }
                    )
                }
                F1Tab.STANDINGS -> {
                    StandingsScreen(
                        category = standingsCategory,
                        season = championshipSeason,
                        drivers = driverStandings,
                        constructors = constructorStandings,
                        // Only block the list for the first load; a refresh keeps the current standings visible.
                        isLoading = isChampionshipLoading && driverStandings.isEmpty() && constructorStandings.isEmpty(),
                        error = championshipError,
                        onSelectCategory = { cat -> viewModel.setStandingsCategory(cat) },
                        onRetry = { viewModel.loadChampionshipData() }
                    )
                }
                F1Tab.CALENDAR -> {
                    CalendarScreen(
                        season = calendarSeason,
                        races = calendarRaces,
                        selectedRace = selectedCalendarRace,
                        selectedSessionType = selectedSessionType,
                        raceResults = sessionResults.race,
                        qualifyingResults = sessionResults.qualifying,
                        sprintResults = sessionResults.sprint,
                        sprintQualifyingResults = sessionResults.sprintQualifying,
                        practiceTimings = sessionResults.practice,
                        isResultsLoading = isSessionResultsLoading,
                        resultsError = sessionResultsError,
                        isLoading = isCalendarLoading,
                        error = calendarError,
                        onSelectRace = { race -> viewModel.selectCalendarRace(race) },
                        onSelectSession = { type -> viewModel.selectWeekendSession(type) },
                        onSelectRaceSession = { race, type -> viewModel.selectCalendarRace(race, type) },
                        onRetry = { viewModel.loadCalendarData() },
                        onRetryResults = { viewModel.retrySessionResults() },
                        calendarListState = calendarListState
                    )
                }
            }

            // Dialogs & Modals
            selectedDriver?.let { driver ->
                DriverTyreStintDialog(
                    driver = driver,
                    onDismiss = { viewModel.closeDriverStints() }
                )
            }

            if (showWeatherDialog) {
                WeatherDialog(
                    weather = weather,
                    onDismiss = { viewModel.setWeatherDialogVisible(false) }
                )
            }

            if (showSettingsDialog) {
                ServerSettingsDialog(
                    currentUrl = backendUrl,
                    isTesting = isTesting,
                    pingResult = pingResult,
                    showLatestSession = showLatestSession,
                    onToggleShowLatestSession = { viewModel.updateShowLatestSession(it) },
                    onTestConnection = { url -> viewModel.testBackendConnection(url) },
                    onSaveUrl = { url -> viewModel.updateBackendUrl(url) },
                    onDismiss = { viewModel.setSettingsDialogVisible(false) }
                )
            }
        }
    }
}
