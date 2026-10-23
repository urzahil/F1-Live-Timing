package com.example.ui.screens

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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.trimToCapHeight
import com.example.data.model.ConstructorStandingItem
import com.example.data.model.DriverStandingItem
import com.example.ui.theme.F1CardBorder
import com.example.ui.theme.F1CardSurface
import com.example.ui.theme.F1PitWallSurface
import com.example.ui.theme.F1Red
import com.example.ui.theme.F1TextMuted
import com.example.ui.theme.F1TextPrimary
import com.example.ui.theme.F1TextSecondary
import com.example.ui.theme.TeamColors
import com.example.ui.viewmodel.StandingsCategory

@Composable
fun StandingsScreen(
    category: StandingsCategory,
    season: String,
    drivers: List<DriverStandingItem>,
    constructors: List<ConstructorStandingItem>,
    isLoading: Boolean,
    error: String?,
    onSelectCategory: (StandingsCategory) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val leaderDriverPoints = drivers.firstOrNull()?.points?.toFloatOrNull() ?: 1f
    val leaderConstructorPoints = constructors.firstOrNull()?.points?.toFloatOrNull() ?: 1f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(com.example.ui.theme.F1CarbonBackground)
    ) {
        // Season Banner & Category Toggle Tabs
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(F1PitWallSurface)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "WORLD CHAMPIONSHIP",
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

            Spacer(modifier = Modifier.height(10.dp))

            TabRow(
                selectedTabIndex = category.ordinal,
                containerColor = F1CardSurface,
                contentColor = F1Red,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[category.ordinal]),
                        color = F1Red
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, F1CardBorder, RoundedCornerShape(8.dp))
            ) {
                Tab(
                    selected = category == StandingsCategory.DRIVERS,
                    onClick = { onSelectCategory(StandingsCategory.DRIVERS) },
                    text = {
                        Text(
                            text = "DRIVERS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (category == StandingsCategory.DRIVERS) F1Red else F1TextSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_drivers_standings")
                )
                Tab(
                    selected = category == StandingsCategory.CONSTRUCTORS,
                    onClick = { onSelectCategory(StandingsCategory.CONSTRUCTORS) },
                    text = {
                        Text(
                            text = "CONSTRUCTORS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (category == StandingsCategory.CONSTRUCTORS) F1Red else F1TextSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_constructors_standings")
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = F1Red)
            }
        } else if (error != null && drivers.isEmpty() && constructors.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Unable to load standings", color = F1TextPrimary, fontWeight = FontWeight.Bold)
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (category == StandingsCategory.DRIVERS) {
                    items(drivers, key = { it.driver?.driverId ?: it.position.orEmpty() }) { item ->
                        DriverStandingRow(
                            item = item,
                            leaderPoints = leaderDriverPoints
                        )
                    }
                } else {
                    items(constructors, key = { it.constructor?.constructorId ?: it.position.orEmpty() }) { item ->
                        ConstructorStandingRow(
                            item = item,
                            leaderPoints = leaderConstructorPoints
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

@Composable
private fun DriverStandingRow(
    item: DriverStandingItem,
    leaderPoints: Float
) {
    val pos = item.position.orEmpty()
    val badgeColor = when (pos) {
        "1" -> Color(0xFFFFD700)
        "2" -> Color(0xFFC0C0C0)
        "3" -> Color(0xFFCD7F32)
        else -> F1TextMuted
    }

    val currentPoints = item.points?.toFloatOrNull() ?: 0f
    val diffToLeader = (leaderPoints - currentPoints).toInt().coerceAtLeast(0)
    val progress = if (leaderPoints > 0f) (currentPoints / leaderPoints).coerceIn(0f, 1f) else 0f

    val constructorId = item.constructors?.firstOrNull()?.constructorId
    val constructorName = item.constructors?.firstOrNull()?.name
    val teamColor = TeamColors.getTeamColor(constructorId ?: constructorName)

    val headshotUrl = item.driver?.headshotUrl ?: item.headshotUrl

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(F1CardSurface, RoundedCornerShape(8.dp))
            .border(1.dp, F1CardBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position
            Text(
                text = pos,
                color = badgeColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(26.dp)
            )

            // Driver Photo / Headshot
            Box(
                modifier = Modifier
                    .size(42.dp)
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
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Driver Name & Country Code Badge
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.driver?.fullName.orEmpty(),
                    color = F1TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val countryCode = com.example.ui.theme.CountryCodeHelper.getCountryCode(item.driver?.nationality)
                    if (!countryCode.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .background(F1PitWallSurface, RoundedCornerShape(3.dp))
                                .border(1.dp, F1CardBorder, RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = countryCode,
                                color = F1TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = constructorName ?: "Formula 1",
                        color = F1TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Points without "PTS" and (difference to leader) in smaller font
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = item.points.orEmpty(),
                        color = F1TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (pos != "1" && diffToLeader > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "(-$diffToLeader)",
                            color = F1TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                val winsCount = item.wins?.toIntOrNull() ?: 0
                if (winsCount > 0) {
                    Text(
                        text = if (winsCount == 1) "1 win" else "$winsCount wins",
                        color = F1TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Proportional Team Colour Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(F1CardBorder, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress.coerceAtLeast(0.01f))
                    .height(3.dp)
                    .background(teamColor, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun ConstructorStandingRow(
    item: ConstructorStandingItem,
    leaderPoints: Float
) {
    val pos = item.position.orEmpty()
    val badgeColor = when (pos) {
        "1" -> Color(0xFFFFD700)
        "2" -> Color(0xFFC0C0C0)
        "3" -> Color(0xFFCD7F32)
        else -> F1TextMuted
    }

    val currentPoints = item.points?.toFloatOrNull() ?: 0f
    val diffToLeader = (leaderPoints - currentPoints).toInt().coerceAtLeast(0)
    val progress = if (leaderPoints > 0f) (currentPoints / leaderPoints).coerceIn(0f, 1f) else 0f

    val constructorId = item.constructor?.constructorId
    val constructorName = item.constructor?.name
    val teamColor = TeamColors.getTeamColor(constructorId ?: constructorName)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(F1CardSurface, RoundedCornerShape(8.dp))
            .border(1.dp, F1CardBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pos,
                color = badgeColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(26.dp)
            )

            // Team color accent pill
            Box(
                modifier = Modifier
                    .size(4.dp, 24.dp)
                    .background(teamColor, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))

            // Constructor Name (Nationality removed per request)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = constructorName.orEmpty(),
                    color = F1TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Points without "PTS" and (difference to leader) in smaller font
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = item.points.orEmpty(),
                        color = F1TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (pos != "1" && diffToLeader > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "(-$diffToLeader)",
                            color = F1TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                val winsCount = item.wins?.toIntOrNull() ?: 0
                if (winsCount > 0) {
                    Text(
                        text = if (winsCount == 1) "1 win" else "$winsCount wins",
                        color = F1TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Proportional Team Colour Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(F1CardBorder, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress.coerceAtLeast(0.01f))
                    .height(3.dp)
                    .background(teamColor, RoundedCornerShape(2.dp))
            )
        }
    }
}
