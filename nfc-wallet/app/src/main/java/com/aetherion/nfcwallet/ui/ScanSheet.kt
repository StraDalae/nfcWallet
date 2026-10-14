package com.aetherion.nfcwallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aetherion.nfcwallet.data.colonSeparated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheet(
    state: ScanState,
    nfcStatus: NfcStatus,
    onSave: (name: String, colorIndex: Int, iconKey: String, note: String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenNfcSettings: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                ScanState.Idle -> Unit
                ScanState.Waiting -> {
                    if (nfcStatus == NfcStatus.OK) {
                        NfcPulse()
                        Spacer(Modifier.height(16.dp))
                        Text("Ready to scan", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Hold a card or tag to the back of your phone.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(
                            if (nfcStatus == NfcStatus.UNSUPPORTED) "This device has no NFC reader." else "NFC is turned off.",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (nfcStatus == NfcStatus.DISABLED) {
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onOpenNfcSettings) { Text("Open NFC settings") }
                        }
                    }
                }
                ScanState.Reading -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Reading… keep the card in place", style = MaterialTheme.typography.titleMedium)
                }
                is ScanState.Error -> {
                    Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(state.message, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text("Try again") }
                }
                is ScanState.Result -> SaveForm(state, onSave)
            }
        }
    }
}

@Composable
private fun SaveForm(
    state: ScanState.Result,
    onSave: (String, Int, String, String) -> Unit,
) {
    val result = state.result
    var name by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableIntStateOf(0) }
    var icon by rememberSaveable { mutableStateOf("key") }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Save this card", style = MaterialTheme.typography.titleLarge)

        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            InfoRow("Type", result.summary)
            InfoRow("UID", result.dump.uidHex.colonSeparated())
            CapabilityChip(result.capability)
            Spacer(Modifier.height(6.dp))
            Text(
                result.capability.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.notice?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("Name") },
            placeholder = { Text("e.g. Office door, Gym fob") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Colour", style = MaterialTheme.typography.labelLarge)
        ColorPicker(color) { color = it }
        Text("Icon", style = MaterialTheme.typography.labelLarge)
        IconPicker(icon) { icon = it }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(200) },
            label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSave(name, color, icon, note) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save to wallet") }
    }
}
