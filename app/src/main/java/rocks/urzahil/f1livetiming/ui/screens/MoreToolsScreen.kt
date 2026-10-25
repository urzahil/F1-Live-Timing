package rocks.urzahil.f1livetiming.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.urzahil.f1livetiming.ui.theme.F1CarbonBackground
import rocks.urzahil.f1livetiming.ui.theme.F1CardBorder
import rocks.urzahil.f1livetiming.ui.theme.F1CardSurface
import rocks.urzahil.f1livetiming.ui.theme.F1PitWallSurface
import rocks.urzahil.f1livetiming.ui.theme.F1Red
import rocks.urzahil.f1livetiming.ui.theme.F1TextMuted
import rocks.urzahil.f1livetiming.ui.theme.F1TextPrimary
import rocks.urzahil.f1livetiming.ui.theme.F1TextSecondary
import rocks.urzahil.f1livetiming.ui.viewmodel.MoreTool

private data class MoreToolInfo(val title: String, val description: String, val icon: ImageVector)

private fun MoreTool.info(): MoreToolInfo = when (this) {
    MoreTool.CHAMPIONSHIP_PREDICTOR -> MoreToolInfo(
        title = "Championship Predictor",
        description = "Who can still mathematically win the drivers' and constructors' titles",
        icon = Icons.Default.EmojiEvents
    )
    MoreTool.FIA_DOCUMENTS -> MoreToolInfo(
        title = "FIA Documents",
        description = "Decisions, classifications and notices the FIA publishes for each race of the season",
        icon = Icons.Default.Description
    )
}

internal val MoreTool.pageTitle: String get() = info().title

/** Hub listing every extra tool; each entry opens its own full-screen page. */
@Composable
fun MoreToolsScreen(
    onOpenTool: (MoreTool) -> Unit,
    onBack: () -> Unit
) {
    ToolPageScaffold(title = "More tools", onBack = onBack) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(MoreTool.entries) { tool ->
                MoreToolCard(tool = tool, onClick = { onOpenTool(tool) })
            }
        }
    }
}

@Composable
private fun MoreToolCard(tool: MoreTool, onClick: () -> Unit) {
    val info = tool.info()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(F1CardSurface, RoundedCornerShape(8.dp))
            .border(1.dp, F1CardBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("more_tool_${tool.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(F1Red, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = info.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.title,
                color = F1TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = info.description,
                color = F1TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = F1TextMuted
        )
    }
}

/** Full-screen page with a back arrow and title, shared by the hub and every tool. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(F1CarbonBackground),
        containerColor = F1CarbonBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        color = F1TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tool_page_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = F1TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = F1PitWallSurface,
                    titleContentColor = F1TextPrimary
                )
            )
        },
        content = content
    )
}
