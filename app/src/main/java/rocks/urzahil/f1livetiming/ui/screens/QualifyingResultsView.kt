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
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material3.HorizontalDivider
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
import coil3.compose.AsyncImage
import rocks.urzahil.f1livetiming.data.model.QualifyingResultItem
import rocks.urzahil.f1livetiming.ui.theme.F1CardBorder
import rocks.urzahil.f1livetiming.ui.theme.F1CardSurface
import rocks.urzahil.f1livetiming.ui.theme.F1Gold
import rocks.urzahil.f1livetiming.ui.theme.F1PitWallSurface
import rocks.urzahil.f1livetiming.ui.theme.F1Red
import rocks.urzahil.f1livetiming.ui.theme.F1TextMuted
import rocks.urzahil.f1livetiming.ui.theme.F1TextPrimary
import rocks.urzahil.f1livetiming.ui.theme.F1TextSecondary
import rocks.urzahil.f1livetiming.ui.theme.TeamColors

@Composable
internal fun QualifyingResultsView(
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
                    color = F1Gold,
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
                    .border(1.dp, if (isPole) F1Gold.copy(alpha = 0.5f) else F1CardBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pos
                Text(
                    text = pos,
                    color = if (isPole) F1Gold else F1TextPrimary,
                    fontSize = 12.sp,
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
                    color = if (!item.q3.isNullOrBlank()) F1Gold else F1TextMuted,
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
internal fun QualifyingCutoffDivider(label: String) {
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

