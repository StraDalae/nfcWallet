package com.aetherion.nfcwallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aetherion.nfcwallet.data.Capability
import com.aetherion.nfcwallet.data.CardDump
import com.aetherion.nfcwallet.data.WalletCard
import com.aetherion.nfcwallet.data.colonSeparated
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    card: WalletCard?,
    revealed: CardDump?,
    onBack: () -> Unit,
    onReveal: () -> Unit,
    onHide: () -> Unit,
    onEdit: (name: String, colorIndex: Int, iconKey: String, note: String) -> Unit,
    onDelete: () -> Unit,
    onCopyToTag: () -> Unit,
) {
    // The card vanishes from the list after deletion; leave the screen when that happens.
    LaunchedEffect(card == null) { if (card == null) onBack() }
    if (card == null) return

    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card.meta.name, maxLines = 1) },
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardTile(card)

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                InfoRow("Type", card.meta.summary)
                InfoRow("What you can do", card.meta.capability.description)
                InfoRow("Saved", dateFormat.format(Date(card.meta.createdAt)))
                card.meta.lastUsedAt?.let { InfoRow("Last copied to a tag", dateFormat.format(Date(it))) }
                if (card.meta.note.isNotBlank()) InfoRow("Note", card.meta.note)
            }

            if (revealed == null) {
                OutlinedButton(onClick = onReveal, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Visibility, null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Show UID and tag data")
                }
            } else {
                RevealedData(revealed)
                OutlinedButton(onClick = onHide, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.VisibilityOff, null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Hide")
                }
            }

            Button(
                onClick = onCopyToTag,
                enabled = card.meta.capability != Capability.VIEW_ONLY,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.ContentCopy, null)
                Spacer(Modifier.padding(4.dp))
                Text("Copy to a blank tag")
            }
            if (card.meta.capability == Capability.VIEW_ONLY) {
                Text(
                    "This card type is protected by design, so it can be saved for reference but not copied.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(onClick = { showEdit = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Edit, null)
                Spacer(Modifier.padding(4.dp))
                Text("Rename / restyle")
            }
            TextButton(
                onClick = { showDelete = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Filled.Delete, null)
                Spacer(Modifier.padding(4.dp))
                Text("Delete from wallet")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showEdit) EditDialog(card, onDismiss = { showEdit = false }, onConfirm = { n, c, i, note ->
        onEdit(n, c, i, note)
        showEdit = false
    })

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete \"${card.meta.name}\"?") },
            text = { Text("This permanently removes the saved card and its data from this phone.") },
            confirmButton = {
                TextButton(onClick = { showDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RevealedData(dump: CardDump) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        InfoRow("UID", dump.uidHex.colonSeparated())
        InfoRow("Technologies", dump.techList.joinToString(", "))
        if (dump.atqaHex != null || dump.sak != null) {
            InfoRow("ATQA / SAK", "${dump.atqaHex ?: "-"} / ${dump.sak?.let { "0x%02X".format(it) } ?: "-"}")
        }
        dump.pages?.let { InfoRow("Memory", "${it.chip}, ${(it.pageCount - 4) * 4} bytes of user data") }
        dump.classic?.let { c ->
            InfoRow("Sectors", "${c.sectors.count { s -> s.readable }} of ${c.sectors.size} read")
        }
        dump.isoDep?.historicalBytesHex?.let { InfoRow("Historical bytes", it) }
        dump.ndef?.let { n ->
            InfoRow("NDEF", "${n.type}, ${n.records.size} record(s), ${if (n.isWritable) "writable" else "read-only"}")
            n.records.forEachIndexed { i, r -> InfoRow("Record ${i + 1} (${r.kind})", r.preview.ifBlank { "(empty)" }) }
        }
    }
}

@Composable
private fun EditDialog(
    card: WalletCard,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(card.meta.name) }
    var note by remember { mutableStateOf(card.meta.note) }
    var color by remember { mutableIntStateOf(card.meta.colorIndex) }
    var icon by remember { mutableStateOf(card.meta.iconKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true)
                ColorPicker(color) { color = it }
                IconPicker(icon) { icon = it }
                OutlinedTextField(note, { note = it.take(200) }, label = { Text("Note") })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, color, icon, note) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
