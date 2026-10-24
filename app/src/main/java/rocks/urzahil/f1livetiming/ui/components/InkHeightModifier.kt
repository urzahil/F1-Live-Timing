package rocks.urzahil.f1livetiming.ui.components

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import kotlin.math.ceil

/**
 * Shrinks a Text's layout box to the real ink bounds of [text] as measured on
 * this device's font, so surrounding padding centers the visible glyphs regardless of the
 * vendor font's ascent/descent metrics.
 */
fun Modifier.trimToInkHeight(
    text: String,
    fontSize: TextUnit,
    fontFamily: FontFamily = FontFamily.Monospace,
    bold: Boolean = true
): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val baseline = placeable[FirstBaseline]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(
                if (fontFamily == FontFamily.Monospace) Typeface.MONOSPACE else Typeface.DEFAULT,
                if (bold) Typeface.BOLD else Typeface.NORMAL
            )
            textSize = fontSize.toPx()
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        // bounds.top is negative (above baseline), bounds.bottom is relative to baseline.
        val inkHeight = ceil(bounds.height().toFloat()).toInt().coerceAtLeast(1)
        layout(placeable.width, inkHeight) { placeable.place(0, -(baseline + bounds.top)) }
    }
