package rocks.urzahil.f1livetiming.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.urzahil.f1livetiming.data.repository.ParsedWeather
import rocks.urzahil.f1livetiming.ui.theme.F1CardBorder
import rocks.urzahil.f1livetiming.ui.theme.F1CardSurface
import rocks.urzahil.f1livetiming.ui.theme.F1PitWallSurface
import rocks.urzahil.f1livetiming.ui.theme.F1Red
import rocks.urzahil.f1livetiming.ui.theme.F1TextMuted
import rocks.urzahil.f1livetiming.ui.theme.F1TextPrimary
import rocks.urzahil.f1livetiming.ui.theme.F1TextSecondary

@Composable
fun WeatherChip(
    weather: ParsedWeather,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(F1CardSurface, RoundedCornerShape(8.dp))
            .border(1.dp, F1CardBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (weather.isRaining) Icons.Default.WaterDrop else Icons.Default.Thermostat,
            contentDescription = "Weather",
            tint = if (weather.isRaining) Color(0xFF0070FF) else F1Red,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Track ${weather.trackTemp}",
            color = F1TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (weather.isRaining) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "RAIN",
                color = Color(0xFF0070FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun WeatherDialog(
    weather: ParsedWeather,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = F1PitWallSurface,
        titleContentColor = F1TextPrimary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = F1Red,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Meteo",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Rain status banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (weather.isRaining) Color(0xFF0070FF).copy(alpha = 0.2f)
                            else Color(0xFF2ECC71).copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (weather.isRaining) "⚠ RAIN DETECTED ON TRACK" else "✓ DRY TRACK CONDITIONS",
                        color = if (weather.isRaining) Color(0xFF3399FF) else Color(0xFF2ECC71),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                WeatherMetricRow(
                    icon = Icons.Default.Thermostat,
                    label = "Track Temperature",
                    value = weather.trackTemp
                )
                WeatherMetricRow(
                    icon = Icons.Default.Thermostat,
                    label = "Air Temperature",
                    value = weather.airTemp
                )
                WeatherMetricRow(
                    icon = Icons.Default.WaterDrop,
                    label = "Humidity",
                    value = weather.humidity
                )
                WeatherMetricRow(
                    icon = Icons.Default.Air,
                    label = "Pressure",
                    value = weather.pressure
                )
                WeatherMetricRow(
                    icon = Icons.Default.Air,
                    label = "Wind",
                    value = "${weather.windSpeed} (${weather.windDirection})"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = F1Red)
            }
        }
    )
}

@Composable
private fun WeatherMetricRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(F1CardSurface, RoundedCornerShape(6.dp))
            .border(1.dp, F1CardBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = F1TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = F1TextSecondary,
                fontSize = 12.sp
            )
        }
        Text(
            text = value,
            color = F1TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
