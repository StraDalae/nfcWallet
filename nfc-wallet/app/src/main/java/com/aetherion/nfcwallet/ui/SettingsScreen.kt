package com.aetherion.nfcwallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onResetVault: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(
                "How your cards are protected",
                "• Everything is encrypted with AES-256-GCM. The keys live in the Android Keystore " +
                    "(hardware-backed where available) and the OS only lets them be used right after you unlock " +
                    "with your fingerprint, face, or screen lock.\n" +
                    "• Names, colours and notes use one key; raw tag data (UID, memory, sectors) uses a second key " +
                    "with a much shorter unlock window, so it stays sealed unless you reveal or copy a card.\n" +
                    "• The database stores only random IDs and ciphertext. Nothing is sent off the device, " +
                    "and backups are disabled.\n" +
                    "• The app locks as soon as you leave it, and screenshots are blocked.",
            )
            HorizontalDivider()
            Section(
                "Use responsibly",
                "Only read, save, and copy tags and cards that you own or are authorized to copy. " +
                    "Copying access credentials without permission may violate laws or the terms of the system that issued them. " +
                    "Payment cards, passports, and most transit and DESFire cards are protected by design and cannot be cloned.",
            )
            HorizontalDivider()
            Section(
                "Reset vault",
                "Deletes every saved card and destroys the encryption keys. This can't be undone.",
            )
            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete all cards and keys") }
            Spacer(Modifier.height(16.dp))
            Text("NFC Wallet 0.1.0", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Delete everything?") },
            text = { Text("All saved cards and the encryption keys will be permanently erased.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onResetVault() }) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
