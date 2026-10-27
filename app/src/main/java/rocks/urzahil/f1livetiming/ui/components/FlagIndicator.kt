package rocks.urzahil.f1livetiming.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.urzahil.f1livetiming.ui.theme.FlagGreen
import rocks.urzahil.f1livetiming.ui.theme.FlagRed
import rocks.urzahil.f1livetiming.ui.theme.FlagSafetyCar

@Composable
fun FlagIndicator(
    statusText: String,
    modifier: Modifier = Modifier
) {
    // Orange is reserved for safety car alerts; a session that has not started or has ended is light grey.
    val upper = statusText.uppercase()
    val inactiveGrey = Color(0xFF8E9BAE)
    val (flagColor, label) = when {
        upper.contains("RED") -> Pair(FlagRed, "RED FLAG")
        upper.contains("VIRTUAL") || upper.contains("VSC") -> Pair(FlagSafetyCar, "VIRTUAL SAFETY CAR")
        upper == "SC" || upper.contains("SAFETY") -> Pair(FlagSafetyCar, "SAFETY CAR")
        upper.contains("STARTED") || upper.contains("LIVE") -> Pair(FlagGreen, "GREEN FLAG")
        upper.contains("FINISHED") || upper.contains("CHEQUERED") -> Pair(inactiveGrey, "SESSION ENDED")
        upper.contains("NO ACTIVE") || upper.contains("INACTIVE") || upper.contains("DISCONNECTED") -> Pair(inactiveGrey, "SESSION STARTED")
        else -> Pair(inactiveGrey, upper.ifBlank { "SESSION STARTED" })
    }

    // Only red flag and safety car states pulse. Other states run no animation at all, so the pill does
    // not keep requesting frames, and the pulse is read in the layer so it redraws without recomposing.
    val isAlert = flagColor == FlagRed || flagColor == FlagSafetyCar
    val pulse = if (isAlert) {
        rememberInfiniteTransition(label = "flag_pulse").animateFloat(
            initialValue = 1.0f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
    } else {
        null
    }

    Row(
        modifier = modifier
            .graphicsLayer { alpha = pulse?.value ?: 1f }
            .background(flagColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(flagColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = flagColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
