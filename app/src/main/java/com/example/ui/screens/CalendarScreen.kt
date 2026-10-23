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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.trimToCapHeight
import com.example.data.model.PracticeTimingItem
import com.example.data.model.QualifyingResultItem
import com.example.data.model.RaceResultItem
import com.example.data.model.SeasonRaceItem
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
internal fun GrandPrixCalendarList(
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                        .width(56.dp)
                        .background(F1Red, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 5.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = season,
                        modifier = Modifier.trimToCapHeight(11.sp),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
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
internal fun GrandPrixRaceCard(
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

