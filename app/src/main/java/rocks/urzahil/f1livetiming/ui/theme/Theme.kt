package rocks.urzahil.f1livetiming.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val F1DarkColorScheme = darkColorScheme(
    primary = F1Red,
    onPrimary = Color.White,
    primaryContainer = F1DarkRed,
    onPrimaryContainer = Color.White,
    secondary = F1BrightRed,
    onSecondary = Color.White,
    background = F1CarbonBackground,
    onBackground = F1TextPrimary,
    surface = F1PitWallSurface,
    onSurface = F1TextPrimary,
    surfaceVariant = F1CardSurface,
    onSurfaceVariant = F1TextSecondary,
    outline = F1CardBorder
)

@Composable
fun F1LiveTimingTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = F1DarkColorScheme,
        typography = Typography,
        content = content
    )
}
