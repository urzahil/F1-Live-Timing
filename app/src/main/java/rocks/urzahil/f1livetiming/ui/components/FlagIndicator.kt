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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.urzahil.f1livetiming.ui.theme.FlagGreen
import rocks.urzahil.f1livetiming.ui.theme.FlagRed
import rocks.urzahil.f1livetiming.ui.theme.FlagSafetyCar
import rocks.urzahil.f1livetiming.ui.theme.F1TextMuted

@Composable
fun FlagIndicator(
    statusText: String,
    modifier: Modifier = Modifier
) {
    // Orange is reserved for safety car alerts; a session that is not running is neutral grey.
    val upper = statusText.uppercase()
    val (flagColor, label) = when {
        upper.contains("RED") -> Pair(FlagRed, "RED FLAG")
        upper.contains("VIRTUAL") || upper.contains("VSC") -> Pair(FlagSafetyCar, "VIRTUAL SAFETY CAR")
        upper == "SC" || upper.contains("SAFETY") -> Pair(FlagSafetyCar, "SAFETY CAR")
        upper.contains("STARTED") || upper.contains("LIVE") -> Pair(FlagGreen, "GREEN FLAG")
        upper.contains("FINISHED") || upper.contains("CHEQUERED") -> Pair(Color(0xFF8E9BAE), "SESSION ENDED")
        upper.contains("NO ACTIVE") || upper.contains("INACTIVE") || upper.contains("DISCONNECTED") -> Pair(F1TextMuted, "SESSION STARTED")
        else -> Pair(F1TextMuted, upper.ifBlank { "SESSION STARTED" })
    }

    // Only red flag and safety car states pulse.
    val isAlert = flagColor == FlagRed || flagColor == FlagSafetyCar
    val infiniteTransition = rememberInfiniteTransition(label = "flag_pulse")
    val alphaAnim by if (isAlert) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
    } else {
        rememberInfiniteTransition(label = "steady").animateFloat(
            initialValue = 1.0f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(1000)),
            label = "steady"
        )
    }

    Row(
        modifier = modifier
            .alpha(alphaAnim)
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
