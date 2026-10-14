package com.aetherion.nfcwallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aetherion.nfcwallet.data.WalletCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    cards: List<WalletCard>,
    nfcStatus: NfcStatus,
    onScan: () -> Unit,
    onOpenCard: (String) -> Unit,
    onSettings: () -> Unit,
    onOpenNfcSettings: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(cards, query) {
        if (query.isBlank()) cards else cards.filter { it.meta.name.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallet") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Filled.Nfc, null) },
                text = { Text("Scan") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (nfcStatus != NfcStatus.OK) NfcBanner(nfcStatus, onOpenNfcSettings)

            if (cards.isEmpty()) {
                EmptyWallet()
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Search cards") },
                    singleLine = true,
                )
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.id }) { card ->
                        CardTile(card, onClick = { onOpenCard(card.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyWallet() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Filled.Nfc, null,
                Modifier.padding(bottom = 4.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Your wallet is empty", style = MaterialTheme.typography.titleLarge)
            Text(
                "Tap Scan, then hold a card or tag to the back of your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NfcBanner(status: NfcStatus, onOpenNfcSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
        Text(
            if (status == NfcStatus.UNSUPPORTED) "This device has no NFC reader." else "NFC is turned off.",
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (status == NfcStatus.DISABLED) TextButton(onClick = onOpenNfcSettings) { Text("Turn on") }
    }
}
