package com.example

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsMotorsports
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
import com.example.ui.components.DriverTyreStintDialog
import com.example.ui.components.ServerSettingsDialog
import com.example.ui.components.WeatherDialog
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.LiveTimingScreen
import com.example.ui.screens.StandingsScreen
import com.example.ui.theme.F1CarbonBackground
import com.example.ui.theme.F1LiveTimingTheme
import com.example.ui.theme.F1PitWallSurface
import com.example.ui.theme.F1Red
import com.example.ui.theme.F1TextMuted
import com.example.ui.theme.F1TextPrimary
import com.example.ui.theme.F1TextSecondary
import com.example.ui.viewmodel.F1Tab
import com.example.ui.viewmodel.F1ViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: F1ViewModel by viewModels()

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
    val raceResults by viewModel.raceResults.collectAsStateWithLifecycle()
    val qualifyingResults by viewModel.qualifyingResults.collectAsStateWithLifecycle()
    val sprintResults by viewModel.sprintResults.collectAsStateWithLifecycle()
    val sprintQualifyingResults by viewModel.sprintQualifyingResults.collectAsStateWithLifecycle()
    val practiceTimings by viewModel.practiceTimings.collectAsStateWithLifecycle()
    val isSessionResultsLoading by viewModel.isSessionResultsLoading.collectAsStateWithLifecycle()
    val sessionResultsError by viewModel.sessionResultsError.collectAsStateWithLifecycle()

    val calendarListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

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
                    IconButton(
                        onClick = { viewModel.setSettingsDialogVisible(true) },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Backend Settings",
                            tint = F1TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
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
                        isLoading = isChampionshipLoading,
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
                        raceResults = raceResults,
                        qualifyingResults = qualifyingResults,
                        sprintResults = sprintResults,
                        sprintQualifyingResults = sprintQualifyingResults,
                        practiceTimings = practiceTimings,
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
