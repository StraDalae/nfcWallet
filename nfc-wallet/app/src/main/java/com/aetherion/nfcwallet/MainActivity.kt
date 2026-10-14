package com.aetherion.nfcwallet

import android.content.Intent
import android.graphics.Color
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aetherion.nfcwallet.security.AuthGate
import com.aetherion.nfcwallet.ui.NfcMode
import com.aetherion.nfcwallet.ui.NfcStatus
import com.aetherion.nfcwallet.ui.NfcWalletTheme
import com.aetherion.nfcwallet.ui.WalletRoot
import com.aetherion.nfcwallet.ui.WalletViewModel
import kotlinx.coroutines.launch

/** FragmentActivity (not just ComponentActivity) because BiometricPrompt needs a FragmentManager. */
class MainActivity : FragmentActivity() {

    private val vm: WalletViewModel by viewModels()
    private lateinit var gate: AuthGate
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Keeps UIDs and card names out of screenshots and the recents thumbnail.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        gate = AuthGate(this)
        vm.authenticator = gate
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            NfcWalletTheme {
                WalletRoot(
                    vm = vm,
                    onOpenNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    vm.nfcMode.collect { applyReaderMode(it) }
                } finally {
                    disableReaderMode()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNfcStatus()
        applyReaderMode(vm.nfcMode.value) // pick up NFC being switched on while we were away
        if (!vm.unlocked.value) vm.autoUnlock()
    }

    override fun onStop() {
        super.onStop()
        // The system credential screen can stop us while a prompt is showing; don't lock in that case.
        if (!gate.promptInFlight && !isChangingConfigurations) vm.lock()
    }

    override fun onDestroy() {
        if (vm.authenticator === gate) vm.authenticator = null
        super.onDestroy()
    }

    private fun refreshNfcStatus() {
        val adapter = nfcAdapter
        vm.setNfcStatus(
            when {
                adapter == null -> NfcStatus.UNSUPPORTED
                !adapter.isEnabled -> NfcStatus.DISABLED
                else -> NfcStatus.OK
            },
        )
    }

    private fun applyReaderMode(mode: NfcMode) {
        val adapter = nfcAdapter ?: return
        if (mode == NfcMode.Idle || !adapter.isEnabled) {
            disableReaderMode()
            return
        }
        adapter.enableReaderMode(
            this,
            NfcAdapter.ReaderCallback { tag -> vm.onTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V,
            null,
        )
    }

    private fun disableReaderMode() {
        runCatching { nfcAdapter?.disableReaderMode(this) }
    }
}
