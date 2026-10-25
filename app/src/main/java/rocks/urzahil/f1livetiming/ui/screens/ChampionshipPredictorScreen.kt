package rocks.urzahil.f1livetiming.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.urzahil.f1livetiming.data.model.ConstructorStandingItem
import rocks.urzahil.f1livetiming.data.model.DriverStandingItem
import rocks.urzahil.f1livetiming.data.model.SeasonRaceItem
import rocks.urzahil.f1livetiming.ui.theme.F1CardBorder
import rocks.urzahil.f1livetiming.ui.theme.F1CardSurface
import rocks.urzahil.f1livetiming.ui.theme.F1Red
import rocks.urzahil.f1livetiming.ui.theme.F1TextMuted
import rocks.urzahil.f1livetiming.ui.theme.F1TextPrimary
import rocks.urzahil.f1livetiming.ui.theme.F1TextSecondary
import rocks.urzahil.f1livetiming.ui.theme.TeamColors
import rocks.urzahil.f1livetiming.ui.viewmodel.ChampionshipPredictor
import rocks.urzahil.f1livetiming.ui.viewmodel.StandingEntry
import rocks.urzahil.f1livetiming.ui.viewmodel.StandingsCategory
import rocks.urzahil.f1livetiming.ui.viewmodel.TitleContender

private val LeaderLineGold = Color(0xFFFFD700)
private val BarRowHeight = 32.dp

/** Shows who can still win each title, as current points plus the maximum still available. */
@Composable
fun ChampionshipPredictorScreen(
    drivers: List<DriverStandingItem>,
    constructors: List<ConstructorStandingItem>,
    calendar: List<SeasonRaceItem>,
    standingsRound: Int?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    var category by rememberSaveable { mutableStateOf(StandingsCategory.DRIVERS) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        if (drivers.isEmpty() || calendar.isEmpty()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = F1Red)
                }
            } else {
                PredictorError(message = error ?: "Standings or calendar not available yet.", onRetry = onRetry)
            }
            return@Column
        }

        val remaining = remember(calendar, standingsRound) {
            ChampionshipPredictor.remainingSessions(calendar, standingsRound, System.currentTimeMillis())
        }
        val isDrivers = category == StandingsCategory.DRIVERS
        val pointsAvailable = if (isDrivers) remaining.driverPoints else remaining.constructorPoints
        val contenders = remember(drivers, constructors, isDrivers, pointsAvailable) {
            val entries = if (isDrivers) drivers.map { it.toEntry() } else constructors.map { it.toEntry() }
            ChampionshipPredictor.contenders(entries, pointsAvailable)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                CategoryTabs(category = category, onSelect = { category = it })
            }
            item {
                SummaryCard(
                    standingsRound = standingsRound,
                    pointsAvailable = pointsAvailable,
                    races = remaining.races,
                    sprints = remaining.sprints,
                    contenders = contenders,
                    isDrivers = isDrivers
                )
            }
            item {
                ContenderChart(contenders = contenders, labelWidth = if (isDrivers) 44.dp else 96.dp)
            }
            item {
                ExplanationCard()
            }
        }
    }
}

private fun DriverStandingItem.toEntry(): StandingEntry {
    val constructor = constructors?.firstOrNull()
    return StandingEntry(
        id = driver?.driverId ?: position.orEmpty(),
        label = driver?.code ?: driver?.familyName?.take(3)?.uppercase().orEmpty(),
        colorKey = constructor?.constructorId ?: constructor?.name,
        points = points?.toFloatOrNull() ?: 0f
    )
}

private fun ConstructorStandingItem.toEntry() = StandingEntry(
    id = constructor?.constructorId ?: position.orEmpty(),
    label = constructor?.name.orEmpty(),
    colorKey = constructor?.constructorId ?: constructor?.name,
    points = points?.toFloatOrNull() ?: 0f
)

@Composable
private fun CategoryTabs(category: StandingsCategory, onSelect: (StandingsCategory) -> Unit) {
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
        StandingsCategory.entries.forEach { option ->
            Tab(
                selected = category == option,
                onClick = { onSelect(option) },
                text = {
                    Text(
                        text = option.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (category == option) F1Red else F1TextSecondary
                    )
                },
                modifier = Modifier.testTag("predictor_tab_${option.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun SummaryCard(
    standingsRound: Int?,
    pointsAvailable: Int,
    races: Int,
    sprints: Int,
    contenders: List<TitleContender>,
    isDrivers: Boolean
) {
    val champion = ChampionshipPredictor.clinchedChampion(contenders)
    val inFight = contenders.count { it.inContention }
    val noun = if (isDrivers) "drivers" else "teams"

    val status = when {
        champion != null && races == 0 && sprints == 0 -> "${champion.label} won the title"
        champion != null -> "${champion.label} has clinched the title"
        else -> "$inFight $noun still in the title fight"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(F1CardSurface, RoundedCornerShape(8.dp))
            .border(1.dp, F1CardBorder, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (standingsRound != null) "AFTER ROUND $standingsRound" else "SEASON START",
                color = LeaderLineGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "$pointsAvailable pts available",
                color = F1TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = remainingLabel(races, sprints),
            color = F1TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = LeaderLineGold,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = status, color = F1TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun remainingLabel(races: Int, sprints: Int): String {
    if (races == 0 && sprints == 0) return "Season complete"
    val raceText = if (races == 1) "1 race" else "$races races"
    val sprintText = if (sprints == 1) "1 sprint" else "$sprints sprints"
    return if (sprints > 0) "$raceText · $sprintText left" else "$raceText left"
}

@Composable
private fun ContenderChart(contenders: List<TitleContender>, labelWidth: Dp) {
    val scale = contenders.maxOfOrNull { it.maxPoints }?.takeIf { it > 0f } ?: 1f
    val leaderFraction = (contenders.firstOrNull()?.points ?: 0f) / scale

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(F1CardSurface, RoundedCornerShape(8.dp))
            .border(1.dp, F1CardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Rows have no spacing between them so the leader's dashed line reads as one continuous line.
        contenders.forEach { contender ->
            ContenderBarRow(
                contender = contender,
                scale = scale,
                leaderFraction = leaderFraction,
                labelWidth = labelWidth
            )
        }
    }
}

@Composable
private fun ContenderBarRow(
    contender: TitleContender,
    scale: Float,
    leaderFraction: Float,
    labelWidth: Dp
) {
    val teamColor = TeamColors.getTeamColor(contender.colorKey)
    val pointsFraction = (contender.points / scale).coerceIn(0f, 1f)
    val maxFraction = (contender.maxPoints / scale).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BarRowHeight)
            .alpha(if (contender.inContention) 1f else 0.45f)
            .testTag("predictor_row_${contender.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = contender.label,
            color = teamColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(labelWidth)
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .drawWithContent {
                    drawContent()
                    val x = size.width * leaderFraction
                    val dash = 4.dp.toPx()
                    drawLine(
                        color = LeaderLineGold,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .width(maxWidth * maxFraction.coerceAtLeast(0.01f))
                    .height(20.dp)
                    .background(teamColor.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .width(maxWidth * pointsFraction.coerceAtLeast(0.01f))
                    .height(20.dp)
                    .background(teamColor, RoundedCornerShape(4.dp))
            )
        }

        Column(
            modifier = Modifier.width(52.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = formatPoints(contender.points),
                color = F1TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = formatPoints(contender.maxPoints),
                color = F1TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun formatPoints(points: Float): String =
    if (points % 1f == 0f) points.toInt().toString() else points.toString()

@Composable
private fun ExplanationCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(F1CardSurface, RoundedCornerShape(8.dp))
            .border(1.dp, F1CardBorder, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "The solid part of each bar is the points scored so far. The faded part is the most " +
                "they can still finish on, winning every remaining Grand Prix (25 pts) and sprint (8 pts); " +
                "for teams a one-two (43 and 15 pts). The small grey number is that maximum.",
            color = F1TextSecondary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The gold dashed line is the leader's points. A bar that still reaches the line is in the " +
                "title fight; one that falls short is mathematically out and shown faded.",
            color = F1TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun PredictorError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Unable to run the predictor", color = F1TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = message, color = F1TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = F1Red)) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry")
            }
        }
    }
}
