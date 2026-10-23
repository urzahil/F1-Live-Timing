package com.example.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt

/**
 * Shrinks a Text's layout box to its cap height (digits / capitals), so the visible glyphs
 * are centered by surrounding padding instead of the font's ascent/descent line box.
 */
fun Modifier.trimToCapHeight(fontSize: TextUnit, capHeightRatio: Float = 0.72f): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val baseline = placeable[FirstBaseline]
        val capHeight = (fontSize.toPx() * capHeightRatio).roundToInt()
        layout(placeable.width, capHeight) { placeable.place(0, capHeight - baseline) }
    }
