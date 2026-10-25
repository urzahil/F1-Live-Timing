package rocks.urzahil.f1livetiming.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
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
import rocks.urzahil.f1livetiming.data.repository.ParsedDriverTiming
import rocks.urzahil.f1livetiming.data.repository.ParsedRaceControlMessage
import rocks.urzahil.f1livetiming.data.repository.ParsedSession
import rocks.urzahil.f1livetiming.data.repository.ParsedSessionClock
import rocks.urzahil.f1livetiming.data.repository.ParsedWeather
import rocks.urzahil.f1livetiming.ui.components.FlagIndicator
import rocks.urzahil.f1livetiming.ui.components.RaceControlCard
import rocks.urzahil.f1livetiming.ui.components.TyreBadge
import rocks.urzahil.f1livetiming.ui.components.hasTyreData
import rocks.urzahil.f1livetiming.ui.components.WeatherChip
import rocks.urzahil.f1livetiming.ui.theme.F1CardBorder
import rocks.urzahil.f1livetiming.ui.theme.F1CardSurface
import rocks.urzahil.f1livetiming.ui.theme.F1PitWallSurface
import rocks.urzahil.f1livetiming.ui.theme.F1Red
import rocks.urzahil.f1livetiming.ui.theme.F1TextMuted
import rocks.urzahil.f1livetiming.ui.theme.F1TextPrimary
import rocks.urzahil.f1livetiming.ui.theme.F1TextSecondary
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun LiveTimingScreen(
    session: ParsedSession,
    weather: ParsedWeather,
    liveTimings: List<ParsedDriverTiming>,
    raceControl: List<ParsedRaceControlMessage> = emptyList(),
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
    // Eliminated qualifying drivers are hidden; how many there are tells which part is running.
    val visibleTimings = liveTimings.filterNot { it.isKnockedOut }
    val qualifyingPart = qualifyingPartLabel(session, liveTimings.count { it.isKnockedOut })
    val hasSessionData = liveTimings.isNotEmpty() || session.sessionName.isNotBlank() || session.meetingName.isNotBlank()
    val isLiveSessionActive = (session.isLive || (showLatestSession && hasSessionData)) && liveTimings.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(rocks.urzahil.f1livetiming.ui.theme.F1CarbonBackground)
    ) {
        // --- Pit Wall Header Bar ---
        SessionHeaderCard(
            session = session,
            weather = weather,
            showLatestSession = showLatestSession,
            qualifyingPart = qualifyingPart,
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
            // Above the standings: practice and (sprint) qualifying are timed sessions, so they show
            // the session clock; races show the current lap / total laps like "LAP 17 / 44".
            if (isTimedSession(session)) {
                session.clock?.let { SessionClockRow(clock = it) }
            } else {
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
                    SessionProgressRow(
                        text = lapText,
                        dotColor = if (session.isLive) Color(0xFF2ECC71) else Color(0xFFFFD700)
                    )
                }
            }

            // Table Header Bar (POS, DRIVER, TYRE, BEST LAP, LAST LAP, GAP)
            TableHeaderRow()

            // Live Telemetry Driver Leaderboard
            val listState = rememberLazyListState()
            // In timed sessions a new P1 is the news, so bring the list back to the top when it changes hands.
            val leaderId = visibleTimings.firstOrNull { it.position == "1" }
                ?.let { it.racingNumber.ifBlank { it.driverCode } }
            // NOTE: to also scroll to the top in races and sprints, replace the line below with: CM
            // if (true) {
            if (isTimedSession(session)) {
                LaunchedEffect(leaderId) {
                    if (leaderId != null) listState.animateScrollToItem(0)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Key on identity only: a key that includes the position collides (car 1 in P11 vs
                // car 11 in P1) and crashes the list, and it recreates rows on every overtake.
                items(visibleTimings, key = { it.racingNumber.ifBlank { it.driverCode } }) { driver ->
                    DriverTimingRow(
                        driver = driver,
                        isFastestLap = driver.isOverallBestLap,
                        flashLastLap = isTimedSession(session),
                        onClick = { onSelectDriver(driver) }
                    )
                }
                if (raceControl.isNotEmpty()) {
                    item(key = "race_control") {
                        RaceControlCard(
                            messages = raceControl,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/** Centered status line above the table: a coloured dot followed by the lap count or session clock. */
@Composable
private fun SessionProgressRow(text: String, dotColor: Color) {
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
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = F1TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp,
            maxLines = 1
        )
    }
}

/**
 * Remaining session time. Counts down locally every second while the feed says the clock is
 * running, and holds (yellow dot) when it is stopped, e.g. under a red flag or between Q parts.
 */
@Composable
private fun SessionClockRow(clock: ParsedSessionClock) {
    val now by produceState(initialValue = System.currentTimeMillis(), clock) {
        while (clock.extrapolating) {
            value = System.currentTimeMillis()
            delay(1000L - value % 1000L)
        }
        value = System.currentTimeMillis()
    }
    SessionProgressRow(
        text = "REMAINING ${formatSessionClock(clock.remainingAt(now))}",
        dotColor = if (clock.extrapolating) Color(0xFF2ECC71) else Color(0xFFFFD700)
    )
}

/** "1:00:00" for an hour or more, otherwise "59:59". */
private fun formatSessionClock(remainingMillis: Long): String {
    // Round up so the display reaches 00:00 exactly when the clock runs out.
    val totalSeconds = (remainingMillis + 999L) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/** Practice, Qualifying and Sprint Qualifying/Shootout run on time rather than a lap count. */
private fun isTimedSession(session: ParsedSession): Boolean {
    val type = session.sessionType.trim()
    if (type.equals("Practice", ignoreCase = true) || type.equals("Qualifying", ignoreCase = true)) return true
    if (type.isNotEmpty()) return false
    val name = session.sessionName.lowercase()
    return "practice" in name || "qualifying" in name || "shootout" in name
}

/**
 * "Q1"/"Q2"/"Q3" (or "SQ1"… for sprint qualifying), or null outside qualifying. The feed does not name the
 * part, so it is inferred from the eliminations: 6 drivers are out after Q1 and 12 after Q2.
 */
private fun qualifyingPartLabel(session: ParsedSession, knockedOut: Int): String? {
    val type = session.sessionType.trim()
    val name = session.sessionName.lowercase()
    val isQualifying = type.equals("Qualifying", ignoreCase = true) ||
        (type.isEmpty() && ("qualifying" in name || "shootout" in name))
    if (!isQualifying) return null
    val prefix = if ("sprint" in name || "shootout" in name) "SQ" else "Q"
    val part = when {
        // This consider 6 drivers knocked out per session, change accordingly if rules change CM
        knockedOut >= 12 -> 3
        knockedOut >= 6 -> 2
        else -> 1
    }
    return "$prefix$part"
}

@Composable
private fun SessionHeaderCard(
    session: ParsedSession,
    weather: ParsedWeather,
    showLatestSession: Boolean = false,
    qualifyingPart: String? = null,
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
                                text = session.sessionType.uppercase() + (qualifyingPart?.let { " · $it" } ?: ""),
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
        // Spans the rows' 32dp tyre slot plus their 10dp spacer, so the label has room at large
        // font scales while the columns below stay aligned.
        Text(
            text = "TYRE",
            color = F1TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(42.dp)
        )
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

private val LastLapFlashColor = Color(0xFFFFD700)

@Composable
private fun DriverTimingRow(
    driver: ParsedDriverTiming,
    isFastestLap: Boolean,
    flashLastLap: Boolean,
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

        // Tyre Stint Badge (Only compound badge in main view; stint details & tyre age in details popup).
        // The slot keeps its width when there is no tyre data so the columns stay aligned.
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (hasTyreData(driver.currentCompound)) {
                TyreBadge(
                    compound = driver.currentCompound
                )
            }
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

        // Flash when a new lap time arrives. The first value a row sees (initial load, scrolling the row
        // back into view) is not a change, so it does not flash.
        val flash = remember { Animatable(0f) }
        if (flashLastLap) {
            val seen = remember { booleanArrayOf(false) }
            LaunchedEffect(driver.lastLapTime) {
                val isTime = driver.lastLapTime != "--" && driver.lastLapTime.isNotBlank()
                if (seen[0] && isTime) {
                    flash.snapTo(1f)
                    flash.animateTo(0f, tween(durationMillis = 1500))
                }
                seen[0] = true
            }
        }

        Box(modifier = Modifier.weight(1.35f)) {
        // Only as wide as its content, so the highlight does not spill over the empty space in the column.
        Row(
            modifier = Modifier
                .background(LastLapFlashColor.copy(alpha = 0.45f * flash.value), RoundedCornerShape(3.dp)),
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
