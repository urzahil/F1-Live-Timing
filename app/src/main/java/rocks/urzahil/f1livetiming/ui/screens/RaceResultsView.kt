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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import rocks.urzahil.f1livetiming.data.model.RaceResultItem
import rocks.urzahil.f1livetiming.ui.theme.F1CardBorder
import rocks.urzahil.f1livetiming.ui.theme.F1CardSurface
import rocks.urzahil.f1livetiming.ui.theme.F1PitWallSurface
import rocks.urzahil.f1livetiming.ui.theme.F1TextMuted
import rocks.urzahil.f1livetiming.ui.theme.F1TextPrimary
import rocks.urzahil.f1livetiming.ui.theme.TeamColors

@Composable
internal fun RaceResultsView(
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
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pos
                Text(
                    text = pos,
                    color = badgeColor,
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.driver?.familyName.orEmpty().ifBlank { item.driver?.fullName.orEmpty() },
                                color = F1TextPrimary,
                                fontSize = 13.sp,
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

