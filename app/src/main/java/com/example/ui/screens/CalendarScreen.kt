package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.PracticeTimingItem
import com.example.data.model.QualifyingResultItem
import com.example.data.model.RaceResultItem
import com.example.data.model.SeasonRaceItem
import com.example.data.model.WeekendSessionInfo
import com.example.data.model.WeekendSessionType
import com.example.data.model.getAllSessions
import com.example.ui.theme.F1CarbonBackground
import com.example.ui.theme.F1CardBorder
import com.example.ui.theme.F1CardSurface
import com.example.ui.theme.F1PitWallSurface
import com.example.ui.theme.F1Red
import com.example.ui.theme.F1TextMuted
import com.example.ui.theme.F1TextPrimary
import com.example.ui.theme.F1TextSecondary
import com.example.ui.theme.TeamColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun CalendarScreen(
    season: String,
    races: List<SeasonRaceItem>,
    selectedRace: SeasonRaceItem?,
    selectedSessionType: WeekendSessionType,
    raceResults: List<RaceResultItem>,
    qualifyingResults: List<QualifyingResultItem>,
    sprintResults: List<RaceResultItem>,
    isResultsLoading: Boolean,
    resultsError: String?,
    isLoading: Boolean,
    error: String?,
    onSelectRace: (SeasonRaceItem?) -> Unit,
    onSelectSession: (WeekendSessionType) -> Unit,
    onRetry: () -> Unit,
    onRetryResults: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectRaceSession: ((SeasonRaceItem, WeekendSessionType) -> Unit)? = null,
    sprintQualifyingResults: List<QualifyingResultItem> = emptyList(),
    practiceTimings: List<PracticeTimingItem> = emptyList(),
    calendarListState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
) {
    if (selectedRace != null) {
        // Back navigation handler for gesture/system back button
        BackHandler {
            onSelectRace(null)
        }

        GrandPrixWeekendDetailScreen(
            race = selectedRace,
            selectedSessionType = selectedSessionType,
            raceResults = raceResults,
            qualifyingResults = qualifyingResults,
            sprintResults = sprintResults,
            sprintQualifyingResults = sprintQualifyingResults,
            practiceTimings = practiceTimings,
            isResultsLoading = isResultsLoading,
            resultsError = resultsError,
            onBack = { onSelectRace(null) },
            onSelectSession = onSelectSession,
            onRetryResults = onRetryResults,
            modifier = modifier
        )
    } else {
        GrandPrixCalendarList(
            season = season,
            races = races,
            isLoading = isLoading,
            error = error,
            onSelectRace = onSelectRace,
            onSelectRaceSession = { race, type ->
                if (onSelectRaceSession != null) {
                    onSelectRaceSession(race, type)
                } else {
                    onSelectRace(race)
                    onSelectSession(type)
                }
            },
            onRetry = onRetry,
            listState = calendarListState,
            modifier = modifier
        )
    }
}

@Composable
private fun GrandPrixCalendarList(
    season: String,
    races: List<SeasonRaceItem>,
    isLoading: Boolean,
    error: String?,
    onSelectRace: (SeasonRaceItem) -> Unit,
    onSelectRaceSession: (SeasonRaceItem, WeekendSessionType) -> Unit,
    onRetry: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(F1CarbonBackground)
    ) {
        // Header Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(F1PitWallSurface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = F1Red,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GRAND PRIX CALENDAR",
                    color = F1TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            if (season.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .background(F1Red, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = season,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = F1Red)
            }
        } else if (error != null && races.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Unable to load calendar", color = F1TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = error, color = F1TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = F1Red)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(races, key = { it.round ?: it.raceName.orEmpty() }) { race ->
                    GrandPrixRaceCard(
                        race = race,
                        onClick = { onSelectRace(race) },
                        onSelectSession = { type -> onSelectRaceSession(race, type) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun GrandPrixRaceCard(
    race: SeasonRaceItem,
    onClick: () -> Unit,
    onSelectSession: (WeekendSessionType) -> Unit
) {
    val (localDate, localTime) = formatUtcToLocal(race.date, race.time)
    val isSprint = race.sprint != null || race.sprintQualifying != null
    val sessions = race.getAllSessions()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("race_card_round_${race.round.orEmpty()}"),
        colors = CardDefaults.cardColors(containerColor = F1CardSurface),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, F1CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row: Round Badge, Name, Sprint Tag, Open Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(F1Red.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "R${race.round.orEmpty()}",
                            color = F1Red,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = race.raceName.orEmpty(),
                        color = F1TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSprint) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF9B51E0).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFFC785F8).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SPRINT",
                                color = Color(0xFFC785F8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Details",
                        tint = F1TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Circuit & Location
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = F1TextMuted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    //text = "${race.circuit?.circuitName.orEmpty()}, ${race.circuit?.location?.locality.orEmpty()} (${race.circuit?.location?.country.orEmpty()})", CM
                    text = "${race.circuit?.circuitName.orEmpty()}, ${race.circuit?.location?.locality.orEmpty()}",
                    color = F1TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            // All Weekend Sessions Chips (dynamically resized across width)
            if (sessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "WEEKEND SESSIONS (LOCAL TIME)",
                        color = F1TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    sessions.forEach { session ->
                        WeekendSessionPill(
                            session = session,
                            isSelected = false,
                            onClick = { onSelectSession(session.type) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GrandPrixWeekendDetailScreen(
    race: SeasonRaceItem,
    selectedSessionType: WeekendSessionType,
    raceResults: List<RaceResultItem>,
    qualifyingResults: List<QualifyingResultItem>,
    sprintResults: List<RaceResultItem>,
    sprintQualifyingResults: List<QualifyingResultItem>,
    practiceTimings: List<PracticeTimingItem>,
    isResultsLoading: Boolean,
    resultsError: String?,
    onBack: () -> Unit,
    onSelectSession: (WeekendSessionType) -> Unit,
    onRetryResults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessions = race.getAllSessions()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(F1CarbonBackground)
    ) {
        // Navigation Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(F1PitWallSurface)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("btn_back_to_calendar")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Calendar",
                    tint = F1TextPrimary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(F1Red, RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "ROUND ${race.round.orEmpty()}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = race.raceName.orEmpty(),
                        color = F1TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Text(
                    //text = "${race.circuit?.circuitName.orEmpty()} · ${race.circuit?.location?.locality.orEmpty()} (${race.circuit?.location?.country.orEmpty()})", CM
                    text = "${race.circuit?.circuitName.orEmpty()} · ${race.circuit?.location?.locality.orEmpty()}",
                    color = F1TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        // Sessions Selector Row (dynamically resized across width, matching Races list format)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(F1PitWallSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sessions.forEach { session ->
                    WeekendSessionPill(
                        session = session,
                        isSelected = session.type == selectedSessionType,
                        onClick = { onSelectSession(session.type) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Session Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (isResultsLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = F1Red)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Loading ${selectedSessionType.title} data...",
                            color = F1TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                val sessionSchedule = when (selectedSessionType) {
                WeekendSessionType.RACE -> Pair(race.date, race.time)
                WeekendSessionType.QUALIFYING -> Pair(race.qualifying?.date, race.qualifying?.time)
                WeekendSessionType.SPRINT -> Pair(race.sprint?.date, race.sprint?.time)
                WeekendSessionType.SPRINT_QUALIFYING -> Pair(race.sprintQualifying?.date ?: race.sprintShootout?.date, race.sprintQualifying?.time ?: race.sprintShootout?.time)
                WeekendSessionType.FP1 -> Pair(race.firstPractice?.date, race.firstPractice?.time)
                WeekendSessionType.FP2 -> Pair(race.secondPractice?.date, race.secondPractice?.time)
                WeekendSessionType.FP3 -> Pair(race.thirdPractice?.date, race.thirdPractice?.time)
            }
            val isFutureOrTodaySession = isDateInFutureOrToday(sessionSchedule.first ?: race.date)

            if (resultsError != null && !isFutureOrTodaySession) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Unable to load session data", color = F1TextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = resultsError, color = F1TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onRetryResults, colors = ButtonDefaults.buttonColors(containerColor = F1Red)) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }
            } else {
                when (selectedSessionType) {
                    WeekendSessionType.QUALIFYING -> {
                        if (qualifyingResults.isNotEmpty()) {
                            QualifyingResultsView(results = qualifyingResults, isSprintQualifying = false)
                        } else {
                            SessionNotRunView(sessionTitle = "Qualifying", race = race, sessionType = selectedSessionType)
                        }
                    }
                    WeekendSessionType.RACE -> {
                        if (raceResults.isNotEmpty()) {
                            RaceResultsView(results = raceResults, isSprint = false)
                        } else {
                            SessionNotRunView(sessionTitle = "Grand Prix Race", race = race, sessionType = selectedSessionType)
                        }
                    }
                    WeekendSessionType.SPRINT -> {
                        if (sprintResults.isNotEmpty()) {
                            RaceResultsView(results = sprintResults, isSprint = true)
                        } else {
                            SessionNotRunView(sessionTitle = "Sprint Race", race = race, sessionType = selectedSessionType)
                        }
                    }
                    WeekendSessionType.SPRINT_QUALIFYING -> {
                        val hasValidSqTimes = sprintQualifyingResults.any { !it.q1.isNullOrBlank() || !it.q2.isNullOrBlank() || !it.q3.isNullOrBlank() }
                        if (sprintQualifyingResults.isNotEmpty() && hasValidSqTimes) {
                            QualifyingResultsView(results = sprintQualifyingResults, isSprintQualifying = true)
                        } else {
                            SessionNotRunView(sessionTitle = "Sprint Qualifying", race = race, sessionType = selectedSessionType)
                        }
                    }
                    WeekendSessionType.FP1,
                    WeekendSessionType.FP2,
                    WeekendSessionType.FP3 -> {
                        val hasValidPractice = practiceTimings.any { it.bestLapTime != "--" && it.bestLapTime.isNotBlank() }
                        if (practiceTimings.isNotEmpty() && hasValidPractice) {
                            PracticeTimingsView(timings = practiceTimings, sessionType = selectedSessionType, race = race)
                        } else {
                            SessionNotRunView(sessionTitle = selectedSessionType.title, race = race, sessionType = selectedSessionType)
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun WeekendSessionPill(
    session: WeekendSessionInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (sDate, sTime) = formatUtcToLocalShort(session.date, session.time)

    val (badgeBg, badgeText, badgeBorder) = when (session.type) {
        WeekendSessionType.FP1, WeekendSessionType.FP2, WeekendSessionType.FP3 ->
            Triple(Color(0xFF00E5FF).copy(alpha = 0.12f), Color(0xFF80D8FF), Color(0xFF00B0FF).copy(alpha = 0.4f))
        WeekendSessionType.SPRINT_QUALIFYING ->
            Triple(Color(0xFF9B51E0).copy(alpha = 0.18f), Color(0xFFD4A5FF), Color(0xFFAB47BC).copy(alpha = 0.5f))
        WeekendSessionType.SPRINT ->
            Triple(Color(0xFF8E24AA).copy(alpha = 0.22f), Color(0xFFE1BEE7), Color(0xFFBA68C8).copy(alpha = 0.5f))
        WeekendSessionType.QUALIFYING ->
            Triple(Color(0xFFFFB300).copy(alpha = 0.18f), Color(0xFFFFD54F), Color(0xFFFFC107).copy(alpha = 0.5f))
        WeekendSessionType.RACE ->
            Triple(F1Red.copy(alpha = 0.18f), Color(0xFFFF8A80), F1Red.copy(alpha = 0.5f))
    }

    val (activeBg, activeBorder, activeText) = when (session.type) {
        WeekendSessionType.FP1, WeekendSessionType.FP2, WeekendSessionType.FP3 ->
            Triple(Color(0xFF00E5FF).copy(alpha = 0.28f), Color(0xFF00E5FF), Color(0xFFE0F7FA))
        WeekendSessionType.SPRINT_QUALIFYING ->
            Triple(Color(0xFF9B51E0).copy(alpha = 0.32f), Color(0xFFC785F8), Color(0xFFF3E5F5))
        WeekendSessionType.SPRINT ->
            Triple(Color(0xFF8E24AA).copy(alpha = 0.35f), Color(0xFFE1BEE7), Color(0xFFF3E5F5))
        WeekendSessionType.QUALIFYING ->
            Triple(Color(0xFFFFB300).copy(alpha = 0.32f), Color(0xFFFFD54F), Color(0xFFFFF8E1))
        WeekendSessionType.RACE ->
            Triple(F1Red.copy(alpha = 0.35f), Color(0xFFFF5252), Color.White)
    }

    val badgeLabel = when (session.type) {
        WeekendSessionType.QUALIFYING -> "QUAL"
        WeekendSessionType.SPRINT_QUALIFYING -> "SQ"
        WeekendSessionType.SPRINT -> "SPRINT"
        else -> session.type.shortBadge
    }

    val containerBg = if (isSelected) activeBg else badgeBg
    val containerBorderColor = if (isSelected) activeBorder else badgeBorder
    val containerBorderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerBg)
            .border(containerBorderWidth, containerBorderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp, horizontal = 2.dp)
            .testTag("session_pill_${session.type.name}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = badgeLabel,
                color = if (isSelected) activeText else badgeText,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                lineHeight = 11.sp,
                maxLines = 1
            )
            if (sDate.isNotBlank()) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = sDate,
                    color = if (isSelected) Color.White.copy(alpha = 0.9f) else F1TextSecondary,
                    fontSize = 8.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 10.sp,
                    maxLines = 1
                )
            }
            if (sTime.isNotBlank()) {
                Text(
                    text = sTime,
                    color = if (isSelected) Color.White else F1TextPrimary,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 10.sp,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(1.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(1.5.dp)
                    .background(
                        if (isSelected) activeBorder else Color.Transparent,
                        RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

@Composable
private fun QualifyingResultsView(
    results: List<QualifyingResultItem>,
    isSprintQualifying: Boolean = false
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            // Qualifying Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(F1PitWallSurface, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "POS",
                    color = F1TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(26.dp)
                )
                Text(
                    text = "DRIVER / TEAM",
                    color = F1TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.3f)
                )
                Text(
                    text = if (isSprintQualifying) "SQ1" else "Q1",
                    color = F1TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.9f)
                )
                Text(
                    text = if (isSprintQualifying) "SQ2" else "Q2",
                    color = F1TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.9f)
                )
                Text(
                    text = if (isSprintQualifying) "SQ3" else "Q3",
                    color = ColorxFFFFD700,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.9f)
                )
            }
        }

        items(results) { item ->
            val pos = item.position.orEmpty()
            val isPole = pos == "1"

            // Qualifying Cutoff Separator Badges
            if (pos == "11") {
                QualifyingCutoffDivider(label = if (isSprintQualifying) "SQ2 ELIMINATED (P11 - P16)" else "Q2 ELIMINATED (P11 - P16)")
            } else if (pos == "17") {
                QualifyingCutoffDivider(label = if (isSprintQualifying) "SQ1 ELIMINATED (P17 - P22)" else "Q1 ELIMINATED (P17 - P22)")
            }

            val teamColor = TeamColors.getTeamColor(item.constructor?.constructorId ?: item.constructor?.name)
            val headshotUrl = item.driver?.headshotUrl

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(F1CardSurface, RoundedCornerShape(6.dp))
                    .border(1.dp, if (isPole) ColorxFFFFD700.copy(alpha = 0.5f) else F1CardBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pos
                Text(
                    text = pos,
                    color = if (isPole) ColorxFFFFD700 else F1TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(26.dp)
                )

                // Driver Info with Photo & Team Color Bar
                Row(
                    modifier = Modifier.weight(1.3f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Headshot
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(F1PitWallSurface)
                            .border(1.dp, teamColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!headshotUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = headshotUrl,
                                contentDescription = item.driver?.fullName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SportsMotorsports,
                                contentDescription = null,
                                tint = teamColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(5.dp))

                    Column {
                        Text(
                            text = item.driver?.familyName.orEmpty().ifBlank { item.driver?.fullName.orEmpty() },
                            color = F1TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.constructor?.name.orEmpty(),
                            color = teamColor,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Q1
                Text(
                    text = item.q1.orEmpty().ifBlank { "-" },
                    color = F1TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.9f)
                )

                // Q2
                Text(
                    text = item.q2.orEmpty().ifBlank { "-" },
                    color = F1TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.9f)
                )

                // Q3
                Text(
                    text = item.q3.orEmpty().ifBlank { "-" },
                    color = if (!item.q3.isNullOrBlank()) ColorxFFFFD700 else F1TextMuted,
                    fontSize = 10.sp,
                    fontWeight = if (!item.q3.isNullOrBlank()) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.9f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QualifyingCutoffDivider(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = F1Red.copy(alpha = 0.5f))
        Text(
            text = " $label ",
            color = F1Red,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = F1Red.copy(alpha = 0.5f))
    }
}

@Composable
private fun RaceResultsView(
    results: List<RaceResultItem>,
    isSprint: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            // Table Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(F1PitWallSurface, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "POS",
                    color = F1TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(26.dp)
                )
                Text(
                    text = "DRIVER / TEAM",
                    color = F1TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.3f)
                )
                Text(
                    text = "TIME / GAP",
                    color = F1TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.1f)
                )
                Text(
                    text = "PTS",
                    color = ColorxFFFFD700,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.55f)
                )
                Text(
                    text = "GRID",
                    color = F1TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.65f)
                )
            }
        }

        items(results) { item ->
            val pos = item.position.orEmpty()
            val isWinner = pos == "1"
            val badgeColor = when (pos) {
                "1" -> ColorxFFFFD700
                "2" -> Color(0xFFC0C0C0)
                "3" -> Color(0xFFCD7F32)
                else -> if (item.status == "Finished" || item.status?.contains("Lap") == true) F1TextPrimary else F1TextMuted
            }

            val teamColor = TeamColors.getTeamColor(item.constructor?.constructorId ?: item.constructor?.name)
            val headshotUrl = item.driver?.headshotUrl

            val timeDisplay = when {
                !item.time?.time.isNullOrBlank() -> item.time.time
                item.status == "Finished" -> "--"
                !item.status.isNullOrBlank() -> item.status
                else -> "--"
            }

            val isFastestLap = item.fastestLap?.rank == "1"
            val pointsVal = item.points.orEmpty()

            val gridPos = item.grid?.toIntOrNull() ?: 0
            val finishPos = item.position?.toIntOrNull() ?: 0
            val posDelta = if (gridPos > 0 && finishPos > 0) gridPos - finishPos else 0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(F1CardSurface, RoundedCornerShape(6.dp))
                    .border(1.dp, if (isWinner) ColorxFFFFD700.copy(alpha = 0.5f) else F1CardBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pos
                Text(
                    text = pos,
                    color = badgeColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(26.dp)
                )

                // Driver Photo & Info
                Row(
                    modifier = Modifier.weight(1.3f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(F1PitWallSurface)
                            .border(1.dp, teamColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!headshotUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = headshotUrl,
                                contentDescription = item.driver?.fullName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SportsMotorsports,
                                contentDescription = null,
                                tint = teamColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(5.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.driver?.familyName.orEmpty().ifBlank { item.driver?.fullName.orEmpty() },
                                color = F1TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isFastestLap) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Fastest Lap",
                                    tint = Color(0xFFC785F8),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Text(
                            text = item.constructor?.name.orEmpty(),
                            color = teamColor,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Time / Gap / Status
                Text(
                    text = timeDisplay,
                    color = if (timeDisplay.contains("Retired") || timeDisplay.contains("Did not") || timeDisplay.contains("Disqualified")) {
                        Color(0xFFFF5252)
                    } else {
                        F1TextPrimary
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.1f)
                )

                // Points
                Text(
                    text = if (pointsVal != "0" && pointsVal.isNotBlank()) pointsVal else "-",
                    color = if (pointsVal != "0" && pointsVal.isNotBlank()) ColorxFFFFD700 else F1TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(0.55f)
                )

                // Grid Delta
                Row(
                    modifier = Modifier.weight(0.65f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (gridPos > 0) {
                        Text(
                            text = "P$gridPos",
                            color = F1TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        when {
                            posDelta > 0 -> {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = "Gained positions",
                                    tint = Color(0xFF2ECC71),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            posDelta < 0 -> {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = "Lost positions",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingFlat,
                                    contentDescription = "Maintained",
                                    tint = F1TextMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Free Practice Timings View
 * Displays individual lap times, gaps to leader, sector splits, and laps completed for FP1, FP2, and FP3.
 */
@Composable
private fun PracticeTimingsView(
    timings: List<PracticeTimingItem>,
    sessionType: WeekendSessionType,
    race: SeasonRaceItem
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            // Table Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(F1PitWallSurface, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "POS",
                    color = F1TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(26.dp)
                )
                Text(
                    text = "DRIVER / TEAM",
                    color = F1TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.3f)
                )
                Text(
                    text = "TIME",
                    color = ColorxFFFFD700,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.0f)
                )
                Text(
                    text = "GAP",
                    color = F1TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.85f)
                )
                Text(
                    text = "LAPS",
                    color = F1TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.55f)
                )
            }
        }

        items(timings) { item ->
            val pos = item.position
            val isLeader = pos == "1"

            val teamColor = TeamColors.getTeamColor(item.constructor?.constructorId ?: item.constructor?.name ?: item.driver?.code)
            val headshotUrl = item.driver?.headshotUrl

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(F1CardSurface, RoundedCornerShape(6.dp))
                    .border(
                        1.dp,
                        if (isLeader) ColorxFFFFD700.copy(alpha = 0.5f) else F1CardBorder,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pos
                Text(
                    text = pos,
                    color = if (isLeader) ColorxFFFFD700 else F1TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(26.dp)
                )

                // Driver Photo & Info
                Row(
                    modifier = Modifier.weight(1.3f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(F1PitWallSurface)
                            .border(1.dp, teamColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!headshotUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = headshotUrl,
                                contentDescription = item.driver?.fullName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SportsMotorsports,
                                contentDescription = null,
                                tint = teamColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(5.dp))

                    Column {
                        Text(
                            text = item.driver?.familyName.orEmpty().ifBlank { item.driver?.fullName.orEmpty() },
                            color = F1TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.constructor?.name.orEmpty(),
                            color = teamColor,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Best Lap Time
                Text(
                    text = item.bestLapTime.ifBlank { "--" },
                    color = if (isLeader) ColorxFFFFD700 else F1TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = if (isLeader) FontWeight.Bold else FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.0f)
                )

                // Gap
                Text(
                    text = item.gapToLeader.ifBlank { "--" },
                    color = if (isLeader) ColorxFFFFD700 else F1TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.85f)
                )

                // Laps
                Text(
                    text = if (item.lapsCompleted > 0) item.lapsCompleted.toString() else "-",
                    color = F1TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(0.55f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}



@Composable
private fun SessionNotRunView(
    sessionTitle: String,
    race: SeasonRaceItem,
    sessionType: WeekendSessionType
) {
    val sessionSchedule = when (sessionType) {
        WeekendSessionType.RACE -> Pair(race.date, race.time)
        WeekendSessionType.QUALIFYING -> Pair(race.qualifying?.date, race.qualifying?.time)
        WeekendSessionType.SPRINT -> Pair(race.sprint?.date, race.sprint?.time)
        WeekendSessionType.SPRINT_QUALIFYING -> Pair(race.sprintQualifying?.date ?: race.sprintShootout?.date, race.sprintQualifying?.time ?: race.sprintShootout?.time)
        WeekendSessionType.FP1 -> Pair(race.firstPractice?.date, race.firstPractice?.time)
        WeekendSessionType.FP2 -> Pair(race.secondPractice?.date, race.secondPractice?.time)
        WeekendSessionType.FP3 -> Pair(race.thirdPractice?.date, race.thirdPractice?.time)
    }

    val (localDate, localTime) = formatUtcToLocal(sessionSchedule.first, sessionSchedule.second)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(F1CardSurface, RoundedCornerShape(10.dp))
                .border(1.dp, F1CardBorder, RoundedCornerShape(10.dp))
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(F1Red.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = F1Red,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "$sessionTitle Not Yet Completed".uppercase(),
                color = F1TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Scheduled for: $localDate · $localTime",
                color = ColorxFFFFD700,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Official FIA classification and telemetry will be available here immediately after the session concludes.",
                color = F1TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private val ColorxFFFFD700 = Color(0xFFFFD700)

private fun formatUtcToLocal(dateStr: String?, timeStr: String?): Pair<String, String> {
    if (dateStr.isNullOrBlank()) return Pair("--", "")
    val time = if (!timeStr.isNullOrBlank()) {
        val clean = timeStr.replace("Z", "").trim()
        if (clean.length == 5) "$clean:00" else clean
    } else {
        "12:00:00"
    }

    return try {
        val utcIso = "${dateStr.trim()}T$time"
        val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsedDate = utcFormat.parse(utcIso) ?: return Pair(dateStr, timeStr.orEmpty())

        val localDateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        val localTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }

        val formattedDate = localDateFormat.format(parsedDate)
        val formattedTime = if (!timeStr.isNullOrBlank()) {
            val tzShort = TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT)
            "${localTimeFormat.format(parsedDate)} ($tzShort)"
        } else {
            ""
        }

        Pair(formattedDate, formattedTime)
    } catch (_: Exception) {
        Pair(dateStr, timeStr.orEmpty())
    }
}

private fun formatUtcToLocalShort(dateStr: String?, timeStr: String?): Pair<String, String> {
    if (dateStr.isNullOrBlank()) return Pair("--", "")
    val time = if (!timeStr.isNullOrBlank()) {
        val clean = timeStr.replace("Z", "").trim()
        if (clean.length == 5) "$clean:00" else clean
    } else {
        "12:00:00"
    }

    return try {
        val utcIso = "${dateStr.trim()}T$time"
        val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsedDate = utcFormat.parse(utcIso) ?: return Pair(dateStr, timeStr.orEmpty())

        val localDateFormat = SimpleDateFormat("EEE", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        val localTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }

        Pair(localDateFormat.format(parsedDate), localTimeFormat.format(parsedDate))
    } catch (_: Exception) {
        Pair(dateStr, timeStr.orEmpty())
    }
}

private fun isDateInFutureOrToday(dateStr: String?): Boolean {
    if (dateStr.isNullOrBlank()) return false
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val d = sdf.parse(dateStr.trim().take(10)) ?: return false
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        !d.before(cal.time)
    } catch (_: Exception) {
        false
    }
}
