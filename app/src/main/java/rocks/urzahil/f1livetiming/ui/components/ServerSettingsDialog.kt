package rocks.urzahil.f1livetiming.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import rocks.urzahil.f1livetiming.data.api.PingResult
import rocks.urzahil.f1livetiming.ui.theme.F1CardBorder
import rocks.urzahil.f1livetiming.ui.theme.F1CardSurface
import rocks.urzahil.f1livetiming.ui.theme.F1PitWallSurface
import rocks.urzahil.f1livetiming.ui.theme.F1Red
import rocks.urzahil.f1livetiming.ui.theme.F1TextPrimary
import rocks.urzahil.f1livetiming.ui.theme.F1TextSecondary

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
                    Switch(
                        checked = showLatestSession,
                        onCheckedChange = onToggleShowLatestSession,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = F1Red,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = F1PitWallSurface
                        )
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
