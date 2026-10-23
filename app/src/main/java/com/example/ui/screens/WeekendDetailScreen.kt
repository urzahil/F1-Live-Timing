package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.theme.F1TextPrimary
import com.example.ui.theme.F1TextSecondary
import java.util.Calendar

@Composable
internal fun GrandPrixWeekendDetailScreen(
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
                            style = androidx.compose.ui.text.TextStyle(
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                                )
                            ),
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
internal fun WeekendSessionPill(
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
internal fun SessionNotRunView(
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

