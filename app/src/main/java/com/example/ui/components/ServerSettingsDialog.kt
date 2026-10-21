package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.PingResult
import com.example.ui.theme.F1CardBorder
import com.example.ui.theme.F1CardSurface
import com.example.ui.theme.F1PitWallSurface
import com.example.ui.theme.F1Red
import com.example.ui.theme.F1TextPrimary
import com.example.ui.theme.F1TextSecondary

@Composable
fun ServerSettingsDialog(
    currentUrl: String,
    isTesting: Boolean,
    pingResult: PingResult?,
    showLatestSession: Boolean,
    onToggleShowLatestSession: (Boolean) -> Unit,
    onTestConnection: (String) -> Unit,
    onSaveUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputUrl by remember(currentUrl) { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = F1PitWallSurface,
        titleContentColor = F1TextPrimary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = F1Red,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Backend Server Connection",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configure the Formula 1 backend live timing and telemetry API server endpoint:",
                    color = F1TextSecondary,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = { Text("Server Host URL", color = F1TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = F1Red,
                        unfocusedBorderColor = F1CardBorder,
                        focusedTextColor = F1TextPrimary,
                        unfocusedTextColor = F1TextPrimary,
                        focusedContainerColor = F1CardSurface,
                        unfocusedContainerColor = F1CardSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Toggle: Show Latest Available Session Data
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(F1CardSurface, RoundedCornerShape(8.dp))
                        .border(1.dp, F1CardBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Show Latest Session Data",
                            color = F1TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Display latest session & telemetry from Live API instead of 'No live session in progress'",
                            color = F1TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                    CenteredSwitch(
                        checked = showLatestSession,
                        onCheckedChange = onToggleShowLatestSession
                    )
                }

                // Test Connection Button
                Button(
                    onClick = { onTestConnection(inputUrl) },
                    enabled = !isTesting && inputUrl.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = F1CardSurface,
                        contentColor = F1TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, F1CardBorder, RoundedCornerShape(8.dp))
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = F1Red,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing Connection...", fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = F1Red
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ping Server Connection", fontSize = 12.sp)
                    }
                }

                // Ping Result Feedback Card
                if (pingResult != null) {
                    val isOk = pingResult.isSuccess
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isOk) Color(0xFF2ECC71).copy(alpha = 0.15f)
                                else Color(0xFFE74C3C).copy(alpha = 0.15f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (isOk) Color(0xFF2ECC71).copy(alpha = 0.4f)
                                else Color(0xFFE74C3C).copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isOk) Color(0xFF2ECC71) else Color(0xFFE74C3C),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isOk) "Online (${pingResult.latencyMs} ms)" else "Connection Failed",
                                color = if (isOk) Color(0xFF2ECC71) else Color(0xFFE74C3C),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = pingResult.message,
                                color = F1TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveUrl(inputUrl)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = F1Red)
            ) {
                Text("Save & Connect", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = F1TextSecondary)
            }
        }
    )
}

/**
 * Fixed-geometry replacement for Material's Switch: the thumb is always vertically centered in
 * the track, so it can't drift against the border at some display scales.
 */
@Composable
private fun CenteredSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val trackWidth = 52.dp
    val trackHeight = 32.dp
    val thumbSize = 24.dp
    val inset = (trackHeight - thumbSize) / 2
    val thumbX by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - inset else inset,
        label = "switchThumb"
    )
    Box(
        modifier = Modifier
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(if (checked) F1Red else F1PitWallSurface)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbX)
                .size(thumbSize)
                .clip(CircleShape)
                .background(if (checked) Color.White else Color.Gray)
        )
    }
}
