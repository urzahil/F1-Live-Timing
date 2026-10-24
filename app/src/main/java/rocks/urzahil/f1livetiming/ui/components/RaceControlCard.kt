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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.urzahil.f1livetiming.data.repository.ParsedRaceControlMessage
import rocks.urzahil.f1livetiming.ui.theme.F1CardBorder
import rocks.urzahil.f1livetiming.ui.theme.F1CardSurface
import rocks.urzahil.f1livetiming.ui.theme.F1TextMuted
import rocks.urzahil.f1livetiming.ui.theme.F1TextPrimary
import rocks.urzahil.f1livetiming.ui.theme.FlagGreen
import rocks.urzahil.f1livetiming.ui.theme.FlagRed
import rocks.urzahil.f1livetiming.ui.theme.FlagSafetyCar
import rocks.urzahil.f1livetiming.ui.theme.FlagYellow

/** Latest race control messages (newest first), shown below the classification. */
@Composable
fun RaceControlCard(
    messages: List<ParsedRaceControlMessage>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(F1CardSurface, RoundedCornerShape(6.dp))
            .border(1.dp, FlagYellow, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("race_control_card"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = null,
                tint = FlagYellow,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "RACE CONTROL",
                color = FlagYellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        messages.forEachIndexed { index, message ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(F1CardBorder)
                )
            }
            RaceControlMessageRow(message)
        }
    }
}

@Composable
private fun RaceControlMessageRow(message: ParsedRaceControlMessage) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = message.time.ifBlank { "--:--:--" },
            color = F1TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            // Sized by its content so larger system font scales never wrap the time; every time
            // has the same monospace length, so message text still lines up across rows.
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 1.dp, end = 8.dp)
        )
        // Fixed-width slot keeps message text aligned whether or not a flag dot is shown.
        Box(
            modifier = Modifier
                .width(14.dp)
                .padding(top = 4.dp)
        ) {
            raceControlColor(message)?.let { color ->
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(color, CircleShape)
                )
            }
        }
        Text(
            text = message.message,
            color = F1TextPrimary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun raceControlColor(message: ParsedRaceControlMessage): Color? {
    val flag = message.flag
    val text = message.message.uppercase()
    return when {
        flag == "RED" -> FlagRed
        flag.contains("YELLOW") -> FlagYellow
        flag == "GREEN" || flag == "CLEAR" -> FlagGreen
        flag == "BLUE" -> Color(0xFF3399FF)
        flag == "CHEQUERED" || flag.contains("BLACK AND WHITE") -> F1TextPrimary
        message.category.equals("SafetyCar", ignoreCase = true) ||
            text.contains("SAFETY CAR") -> FlagSafetyCar
        else -> null
    }
}
