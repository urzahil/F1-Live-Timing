package rocks.urzahil.f1livetiming.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.urzahil.f1livetiming.data.repository.ParsedDriverTiming
import rocks.urzahil.f1livetiming.ui.theme.F1CardBorder
import rocks.urzahil.f1livetiming.ui.theme.F1CardSurface
import rocks.urzahil.f1livetiming.ui.theme.F1PitWallSurface
import rocks.urzahil.f1livetiming.ui.theme.F1Red
import rocks.urzahil.f1livetiming.ui.theme.F1TextMuted
import rocks.urzahil.f1livetiming.ui.theme.F1TextPrimary
import rocks.urzahil.f1livetiming.ui.theme.F1TextSecondary

@Composable
fun DriverTyreStintDialog(
    driver: ParsedDriverTiming,
    onDismiss: () -> Unit
) {
    val teamColor = runCatching {
        Color(android.graphics.Color.parseColor("#${driver.teamColorHex}"))
    }.getOrDefault(F1Red)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = F1PitWallSurface,
        titleContentColor = F1TextPrimary,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Team color accent pill
                Box(
                    modifier = Modifier
                        .size(4.dp, 28.dp)
                        .background(teamColor, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#${driver.racingNumber}",
                            color = F1TextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = driver.fullName,
                            color = F1TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${driver.driverCode})",
                            color = F1TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = driver.teamName.ifBlank { "Formula 1 Team" },
                        color = F1TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Telemetry quick summary row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(F1CardSurface, RoundedCornerShape(8.dp))
                        .border(1.dp, F1CardBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    TelemetryMiniCell(label = "POS", value = "P${driver.position}")
                    TelemetryMiniCell(label = "GAP", value = driver.gapToLeader)
                    TelemetryMiniCell(label = "INTERVAL", value = driver.intervalToAhead)
                    TelemetryMiniCell(label = "STOPS", value = "${driver.pitStops}")
                    TelemetryMiniCell(label = "STINT LAPS", value = "${driver.currentCompoundAge}")
                }

                // Best lap & last lap breakdown
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(F1CardSurface, RoundedCornerShape(8.dp))
                        .border(1.dp, F1CardBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "BEST LAP", color = F1TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = driver.bestLapTime,
                            color = if (driver.isOverallBestLap) Color(0xFFB24BF3) else if (driver.bestLapTime != "--") Color.White else F1TextMuted,
                            fontSize = 13.sp,
                            fontWeight = if (driver.isOverallBestLap) FontWeight.ExtraBold else FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "LAST LAP", color = F1TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        val lastLapColor = when {
                            driver.lastLapTime == "--" || driver.lastLapTime.isBlank() -> F1TextMuted
                            driver.isCatchingAhead == true -> Color(0xFF2ECC71)
                            driver.isCatchingAhead == false -> Color(0xFFE74C3C)
                            else -> F1TextPrimary
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = driver.lastLapTime,
                                color = lastLapColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            if (driver.isCatchingAhead != null && driver.lastLapTime != "--" && driver.lastLapTime.isNotBlank()) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (driver.isCatchingAhead == true) "▼" else "▲",
                                    color = lastLapColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = F1CardBorder)

                // Tyres Chronological History Section
                Text(
                    text = "TYRE STINT CHRONOLOGY",
                    color = F1TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                if (driver.stintsHistory.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(driver.stintsHistory) { stint ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(F1CardSurface, RoundedCornerShape(6.dp))
                                    .border(1.dp, F1CardBorder, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "STINT ${stint.stintNumber}",
                                        color = F1TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    TyreBadge(
                                        compound = stint.compound,
                                        isNew = stint.isNew
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stint.compound,
                                        color = F1TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = lapsLabel(stint.totalLaps),
                                    color = Color(0xFFC0CAD6),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                } else {
                    // Fallback to current fitted compound
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(F1CardSurface, RoundedCornerShape(6.dp))
                            .border(1.dp, F1CardBorder, RoundedCornerShape(6.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "CURRENT SET", color = F1TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            TyreBadge(
                                compound = driver.currentCompound,
                                lapsAge = driver.currentCompoundAge,
                                isNew = driver.isCurrentCompoundNew
                            )
                        }
                        Text(
                            text = lapsLabel(driver.currentCompoundAge),
                            color = F1TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = F1Red)
            }
        }
    )
}

@Composable
private fun TelemetryMiniCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = F1TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            color = F1TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SectorPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = F1TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            color = F1TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun lapsLabel(laps: Int): String = if (laps == 1) "1 LAP" else "$laps LAPS"
