package com.aetherion.nfcwallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteSheet(
    state: WriteState,
    magic: Boolean,
    onMagicChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                WriteState.Idle -> Unit
                is WriteState.Waiting -> {
                    NfcPulse()
                    Spacer(Modifier.height(12.dp))
                    Text("Hold a blank tag to your phone", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Warning, null, tint = Color(0xFFFFB74D), modifier = Modifier.size(20.dp))
                        Text(
                            "Writing overwrites whatever is on the tag. Only copy cards you own or are authorized to copy.",
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.isClassic) {
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Target is a \"magic\" card", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Also writes block 0 so the UID matches. Leave off for ordinary blank cards.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = magic, onCheckedChange = onMagicChange)
                        }
                    }
                }
                WriteState.Writing -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Writing… don't move the tag", style = MaterialTheme.typography.titleMedium)
                }
                is WriteState.Done -> {
                    Icon(
                        Icons.Filled.CheckCircle, null,
                        Modifier.size(56.dp),
                        tint = if (state.verified) Color(0xFF66BB6A) else Color(0xFFFFB74D),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (state.verified) "Copied and verified" else "Written, but not fully verified",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(state.detail, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
                is WriteState.Failed -> {
                    Icon(Icons.Filled.Error, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(state.message, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Close") }
                        Button(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("Try again") }
                    }
                }
            }
        }
    }
}
