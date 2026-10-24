package rocks.urzahil.f1livetiming.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// Center glyphs inside their line box and trim the extra leading/font padding, so text in
// buttons, pills and rows sits optically centered instead of riding high.
private val CenteredLineHeight = LineHeightStyle(
  alignment = LineHeightStyle.Alignment.Center,
  trim = LineHeightStyle.Trim.Both,
)

private fun TextStyle.centered(): TextStyle =
  copy(
    lineHeightStyle = CenteredLineHeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
  )

private val Base = Typography()

// Set of Material typography styles to start with
val Typography =
  Typography(
    displayLarge = Base.displayLarge.centered(),
    displayMedium = Base.displayMedium.centered(),
    displaySmall = Base.displaySmall.centered(),
    headlineLarge = Base.headlineLarge.centered(),
    headlineMedium = Base.headlineMedium.centered(),
    headlineSmall = Base.headlineSmall.centered(),
    titleLarge = Base.titleLarge.centered(),
    titleMedium = Base.titleMedium.centered(),
    titleSmall = Base.titleSmall.centered(),
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
      ).centered(),
    bodyMedium = Base.bodyMedium.centered(),
    bodySmall = Base.bodySmall.centered(),
    labelLarge = Base.labelLarge.centered(),
    labelMedium = Base.labelMedium.centered(),
    labelSmall = Base.labelSmall.centered(),
  )
