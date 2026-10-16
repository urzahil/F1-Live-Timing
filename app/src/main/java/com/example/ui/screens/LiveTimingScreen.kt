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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.ParsedDriverTiming
import com.example.data.repository.ParsedSession
import com.example.data.repository.ParsedWeather
import com.example.ui.components.FlagIndicator
import com.example.ui.components.TyreBadge
import com.example.ui.components.WeatherChip
import com.example.ui.theme.F1CardBorder
import com.example.ui.theme.F1CardSurface
import com.example.ui.theme.F1PitWallSurface
import com.example.ui.theme.F1Red
import com.example.ui.theme.F1TextMuted
import com.example.ui.theme.F1TextPrimary
import com.example.ui.theme.F1TextSecondary

@Composable
fun LiveTimingScreen(
    session: ParsedSession,
    weather: ParsedWeather,
    liveTimings: List<ParsedDriverTiming>,
    isPolling: Boolean,
    backendUrl: String,
    liveError: String?,
    liveDataStatus: String = "Waiting for refresh",
    showLatestSession: Boolean = false,
    onTogglePolling: () -> Unit,
    onRefresh: () -> Unit,
    onOpenWeather: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectDriver: (ParsedDriverTiming) -> Unit,
    onNavigateToStandings: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasSessionData = liveTimings.isNotEmpty() || session.sessionName.isNotBlank() || session.meetingName.isNotBlank()
    val isLiveSessionActive = (session.isLive || (showLatestSession && hasSessionData)) && liveTimings.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(com.example.ui.theme.F1CarbonBackground)
    ) {
        // --- Pit Wall Header Bar ---
        SessionHeaderCard(
            session = session,
            weather = weather,
            showLatestSession = showLatestSession,
            onOpenWeather = onOpenWeather
        )

//        Text(
//            text = liveDataStatus,
//            color = if (liveDataStatus.contains("stale", ignoreCase = true) ||
//                liveDataStatus.contains("unable", ignoreCase = true)) Color(0xFFFFB74D) else F1TextMuted,
//            fontSize = 10.sp,
//            fontWeight = FontWeight.Medium,
//            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
//        )

        if (!isLiveSessionActive) {
            NoActiveSessionCard(
                session = session,
                backendUrl = backendUrl,
                error = liveError,
                onOpenSettings = onOpenSettings,
                onRefresh = onRefresh,
                onNavigateToStandings = onNavigateToStandings,
                onNavigateToCalendar = onNavigateToCalendar
            )
        } else {
            // Above the standings: Current lap / total laps of the race like "LAP 17 / 44"
            val currentLap = if (session.currentLap > 0) session.currentLap else liveTimings.maxOfOrNull { it.totalLaps } ?: 0
            val totalLaps = session.totalLaps
            val lapText = if (totalLaps > 0 && currentLap > 0) {
                "LAP $currentLap / $totalLaps"
            } else if (totalLaps > 0) {
                "LAP 0 / $totalLaps"
            } else if (currentLap > 0) {
                "LAP $currentLap"
            } else {
                ""
            }

            if (lapText.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (session.isLive) Color(0xFF2ECC71) else Color(0xFFFFD700), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = lapText,
                        color = F1TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Table Header Bar (POS, DRIVER, TYRE, BEST LAP, LAST LAP, GAP)
            TableHeaderRow()

            // Live Telemetry Driver Leaderboard
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Key on identity only: a key that includes the position collides (car 1 in P11 vs
                // car 11 in P1) and crashes the list, and it recreates rows on every overtake.
                items(liveTimings, key = { it.racingNumber.ifBlank { it.driverCode } }) { driver ->
                    DriverTimingRow(
                        driver = driver,
                        isFastestLap = driver.isOverallBestLap,
                        onClick = { onSelectDriver(driver) }
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
private fun SessionHeaderCard(
    session: ParsedSession,
    weather: ParsedWeather,
    showLatestSession: Boolean = false,
    onOpenWeather: () -> Unit
) {
    val isDisplayActive = session.isLive || (showLatestSession && (session.meetingName.isNotBlank() || session.sessionName.isNotBlank() || session.sessionType.isNotBlank()))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = F1PitWallSurface),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, F1CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isDisplayActive && session.meetingName.isNotBlank()) {
                            session.meetingName.uppercase()
                        } else if (isDisplayActive && session.sessionName.isNotBlank()) {
                            session.sessionName.uppercase()
                        } else {
                            "NO ACTIVE LIVE SESSION"
                        },
                        color = F1TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (isDisplayActive && session.sessionType.isNotBlank()) {
                            Text(
                                text = session.sessionType.uppercase(),
                                color = F1Red,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        val statusString = when {
                            session.isLive -> session.status
                            isDisplayActive -> if (session.status.isNotBlank()) session.status else "LATEST ARCHIVE"
                            else -> "NO ACTIVE SESSION"
                        }
                        FlagIndicator(statusText = statusString)
                    }
                }

                if (isDisplayActive) {
                    WeatherChip(
                        weather = weather,
                        onClick = onOpenWeather
                    )
                }
            }

        }
    }
}

@Composable
private fun TableHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "POS",
            color = F1TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(34.dp)
        )
        Text(
            text = "DRIVER",
            color = F1TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1.1f)
        )
        Text(
            text = "TYRE",
            color = F1TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "BEST LAP",
            color = F1TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1.35f)
        )
        Text(
            text = "LAST LAP",
            color = F1TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1.35f)
        )
        Text(
            text = "GAP",
            color = F1TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f)
        )
    }
}

@Composable
private fun DriverTimingRow(
    driver: ParsedDriverTiming,
    isFastestLap: Boolean,
    onClick: () -> Unit
) {
    val teamColor = runCatching {
        Color(android.graphics.Color.parseColor("#${driver.teamColorHex}"))
    }.getOrDefault(F1Red)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(F1CardSurface, RoundedCornerShape(6.dp))
            .border(1.dp, F1CardBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .testTag("driver_row_${driver.racingNumber}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Position
        Text(
            text = driver.position,
            color = F1TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(26.dp)
        )

        // Team Color Bar
        Box(
            modifier = Modifier
                .size(3.dp, 22.dp)
                .background(teamColor, RoundedCornerShape(1.dp))
        )
        Spacer(modifier = Modifier.width(5.dp))

        // Driver Code & Status (No stops count in main view)
        Column(modifier = Modifier.weight(1.1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = driver.driverCode,
                    color = F1TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "#${driver.racingNumber}",
                    color = F1TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (driver.inPit) {
                Text(
                    text = "IN PIT",
                    color = Color(0xFFF1C40F),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (driver.isRetired) {
                Text(
                    text = "RETIRED",
                    color = Color(0xFFE74C3C),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (driver.isStopped) {
                Text(
                    text = "STOPPED",
                    color = Color(0xFFE67E22),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Tyre Stint Badge (Only compound badge in main view; stint details & tyre age in details popup)
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            TyreBadge(
                compound = driver.currentCompound
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Best Lap Time (Highlighted in purple for overall fastest lap of the race, white otherwise)
        val bestLapColor = when {
            isFastestLap -> Color(0xFFB24BF3) // F1 Fastest Lap Purple
            driver.bestLapTime != "--" && driver.bestLapTime.isNotBlank() -> Color.White
            else -> F1TextMuted
        }
        Text(
            text = driver.bestLapTime,
            color = bestLapColor,
            fontSize = 11.sp,
            fontWeight = if (isFastestLap) FontWeight.ExtraBold else FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.weight(1.35f)
        )

        // Last Lap Time with visual indicator for gap to car ahead (Catching: Green = gap decreasing, Red = gap increasing)
        val lastLapColor = when {
            driver.lastLapTime == "--" || driver.lastLapTime.isBlank() -> F1TextMuted
            driver.isCatchingAhead == true -> Color(0xFF2ECC71) // Green: Gap decreasing (catching car ahead)
            driver.isCatchingAhead == false -> Color(0xFFE74C3C) // Red: Gap increasing (losing to car ahead)
            else -> F1TextPrimary
        }

        Row(
            modifier = Modifier.weight(1.35f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = driver.lastLapTime,
                color = lastLapColor,
                fontSize = 11.sp,
                fontWeight = if (driver.isCatchingAhead != null) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            if (driver.isCatchingAhead != null && driver.lastLapTime != "--" && driver.lastLapTime.isNotBlank()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (driver.isCatchingAhead == true) "▼" else "▲",
                    color = lastLapColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Gap to Leader
        Text(
            text = driver.gapToLeader,
            color = if (driver.gapToLeader == "LEADER" || driver.position == "1") Color(0xFFFFD700) else F1TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.weight(1.1f)
        )
    }
}

@Composable
private fun NoActiveSessionCard(
    session: ParsedSession,
    backendUrl: String,
    error: String?,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToStandings: () -> Unit,
    onNavigateToCalendar: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = F1PitWallSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, F1CardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color(0xFFE67E22).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsMotorsports,
                        contentDescription = null,
                        tint = Color(0xFFE67E22),
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "NO LIVE SESSION IN PROGRESS",
                    color = F1TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = "There is currently no Formula 1 session active on track. Live timings, intervals, and tyre stint histories will appear here in real time as soon as the cars hit the track.",
                    color = F1TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )

                // Quick Navigation Shortcuts to Standings and Calendar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToStandings,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD700)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Standings", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onNavigateToCalendar,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = F1Red),
                        border = androidx.compose.foundation.BorderStroke(1.dp, F1Red.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Calendar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE74C3C).copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = error,
                            color = Color(0xFFFF6B6B),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Text(
                    text = "Backend Host: $backendUrl",
                    color = F1TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = F1CardSurface),
                        modifier = Modifier.border(1.dp, F1CardBorder, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = F1Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Server Settings", color = F1TextPrimary, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(containerColor = F1Red)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refresh", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
