package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TyreHard
import com.example.ui.theme.TyreIntermediate
import com.example.ui.theme.TyreMedium
import com.example.ui.theme.TyreSoft
import com.example.ui.theme.TyreUnknown
import com.example.ui.theme.TyreWet

@Composable
fun TyreBadge(
    compound: String,
    modifier: Modifier = Modifier,
    lapsAge: Int? = null,
    isNew: Boolean? = null
) {
    val upper = compound.uppercase()
    val (compoundColor, initial, textColor) = when {
        upper.contains("SOFT") || upper == "S" -> Triple(TyreSoft, "S", Color.White)
        upper.contains("MEDIUM") || upper == "M" -> Triple(TyreMedium, "M", Color.Black)
        upper.contains("HARD") || upper == "H" -> Triple(TyreHard, "H", Color.Black)
        upper.contains("INTER") || upper == "I" -> Triple(TyreIntermediate, "I", Color.White)
        upper.contains("WET") || upper == "W" -> Triple(TyreWet, "W", Color.White)
        else -> Triple(TyreUnknown, "?", Color.White)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pirelli Compound Circular Badge
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color(0xFF141820), CircleShape)
                .border(2.dp, compoundColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = compoundColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        if (lapsAge != null && lapsAge > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${lapsAge}L",
                color = Color(0xFFC0CAD6),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }

        if (isNew == true) {
            Spacer(modifier = Modifier.width(3.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFF27AE60).copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 3.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "N",
                    modifier = Modifier.trimToInkHeight("N", 9.sp, FontFamily.Default),
                    color = Color(0xFF2ECC71),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
