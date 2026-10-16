package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Dynamic Team Colors Registry.
 * Extracts and stores team and constructor colors at runtime from APIs (OpenF1, SignalR live feed).
 * Inferred from drivers when constructor API lacks color information.
 * Contains no hardcoded team colors.
 */
object TeamColors {

    private val runtimeColorMap = ConcurrentHashMap<String, Color>()

    fun registerColor(identifier: String?, color: Color?) {
        if (identifier.isNullOrBlank() || color == null) return
        val raw = identifier.trim().lowercase()
        runtimeColorMap[raw] = color

        val normalized = normalizeKey(identifier)
        if (normalized.isNotBlank()) {
            runtimeColorMap[normalized] = color
        }
    }

    fun registerHexColor(identifier: String?, hex: String?) {
        val parsed = parseHex(hex) ?: return
        registerColor(identifier, parsed)
    }

    fun getTeamColor(identifier: String?): Color {
        if (identifier.isNullOrBlank()) return F1Red

        val raw = identifier.trim().lowercase()
        runtimeColorMap[raw]?.let { return it }

        val normalized = normalizeKey(identifier)
        if (normalized.isNotBlank()) {
            runtimeColorMap[normalized]?.let { return it }
        }

        // Avoid substring matching: short driver identifiers can otherwise inherit another
        // team's color (for example, overlapping names or acronyms).

        // Derive dynamic distinct color from hash code at runtime (zero hardcoding)
        return deriveDynamicColor(identifier)
    }

    fun parseHex(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        val clean = hex.trim().removePrefix("#").removePrefix("0x")
        return try {
            val value = clean.toLong(16)
            if (clean.length <= 6) {
                Color(0xFF000000 or value)
            } else {
                Color(value)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeKey(key: String): String {
        return key.lowercase()
            .replace("_", " ")
            .replace("-", " ")
            .replace("f1", "")
            .replace("team", "")
            .replace("racing", "")
            .replace("scuderia", "")
            .trim()
    }

    private fun deriveDynamicColor(name: String): Color {
        val clean = name.trim().lowercase()
        if (clean.isBlank()) return F1Red
        val hash = abs(clean.hashCode())
        val hue = (hash % 360).toFloat()
        val hsv = floatArrayOf(hue, 0.85f, 0.90f)
        val argb = android.graphics.Color.HSVToColor(hsv)
        return Color(argb)
    }
}
