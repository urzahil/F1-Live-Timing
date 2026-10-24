package rocks.urzahil.f1livetiming.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import rocks.urzahil.f1livetiming.data.model.PracticeTimingItem
import rocks.urzahil.f1livetiming.data.model.SeasonRaceItem
import rocks.urzahil.f1livetiming.data.model.WeekendSessionType
import rocks.urzahil.f1livetiming.ui.theme.F1CardBorder
import rocks.urzahil.f1livetiming.ui.theme.F1CardSurface
import rocks.urzahil.f1livetiming.ui.theme.F1PitWallSurface
import rocks.urzahil.f1livetiming.ui.theme.F1TextMuted
import rocks.urzahil.f1livetiming.ui.theme.F1TextPrimary
import rocks.urzahil.f1livetiming.ui.theme.F1TextSecondary
import rocks.urzahil.f1livetiming.ui.theme.TeamColors

/**
 * Free Practice Timings View
 * Displays individual lap times, gaps to leader, sector splits, and laps completed for FP1, FP2, and FP3.
 */
@Composable
internal fun PracticeTimingsView(
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
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pos
                Text(
                    text = pos,
                    color = if (isLeader) ColorxFFFFD700 else F1TextPrimary,
                    fontSize = 12.sp,
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
                            fontSize = 13.sp,
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



