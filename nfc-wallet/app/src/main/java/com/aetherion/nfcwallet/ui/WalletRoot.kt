package com.aetherion.nfcwallet.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun WalletRoot(vm: WalletViewModel, onOpenNfcSettings: () -> Unit) {
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val broken by vm.vaultBroken.collectAsStateWithLifecycle()
    val needsScreenLock by vm.needsScreenLock.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.consumeMessage()
        }
    }

    if (broken) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Vault keys are gone") },
            text = {
                Text(
                    "Android removed the encryption keys (this happens if the screen lock is removed or reset). " +
                        "The saved cards can no longer be decrypted. Reset the vault to start fresh.",
                )
            },
            confirmButton = { TextButton(onClick = vm::resetVault) { Text("Reset vault") } },
        )
    }

    if (!unlocked) {
        LockScreen(needsScreenLock = needsScreenLock, onUnlock = vm::unlock)
    } else {
        MainNav(vm, onOpenNfcSettings)
    }
}

@Composable
private fun LockScreen(needsScreenLock: Boolean, onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Text("NFC Wallet is locked", style = MaterialTheme.typography.titleLarge)
            if (needsScreenLock) {
                Text(
                    "Set up a screen lock or fingerprint in Android settings first. " +
                        "The wallet's encryption keys are tied to it.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onUnlock) { Text("Unlock") }
        }
    }
}

@Composable
private fun MainNav(vm: WalletViewModel, onOpenNfcSettings: () -> Unit) {
    val nav = rememberNavController()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val nfcStatus by vm.nfcStatus.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val write by vm.write.collectAsStateWithLifecycle()
    val magic by vm.magic.collectAsStateWithLifecycle()
    val revealed by vm.revealed.collectAsStateWithLifecycle()

    NavHost(navController = nav, startDestination = "wallet") {
        composable("wallet") {
            WalletScreen(
                cards = cards,
                nfcStatus = nfcStatus,
                onScan = vm::startScan,
                onOpenCard = { id -> nav.navigate("card/$id") },
                onSettings = { nav.navigate("settings") },
                onOpenNfcSettings = onOpenNfcSettings,
            )
        }
        composable("card/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DetailScreen(
                card = cards.firstOrNull { it.id == id },
                revealed = revealed?.takeIf { it.id == id }?.dump,
                onBack = {
                    vm.hideReveal()
                    nav.popBackStack()
                },
                onReveal = { vm.reveal(id) },
                onHide = vm::hideReveal,
                onEdit = { n, c, i, note -> vm.updateCard(id, n, c, i, note) },
                onDelete = { vm.deleteCard(id) },
                onCopyToTag = { vm.startWrite(id) },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() }, onResetVault = vm::resetVault)
        }
    }

    if (scan != ScanState.Idle) {
        ScanSheet(
            state = scan,
            nfcStatus = nfcStatus,
            onSave = vm::saveScanned,
            onRetry = vm::startScan,
            onDismiss = vm::cancelScan,
            onOpenNfcSettings = onOpenNfcSettings,
        )
    }

    if (write != WriteState.Idle) {
        WriteSheet(
            state = write,
            magic = magic,
            onMagicChange = vm::setMagic,
            onRetry = vm::retryWrite,
            onDismiss = vm::cancelWrite,
        )
    }
}
